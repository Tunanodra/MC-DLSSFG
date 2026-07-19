/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.dgtdi.mcdlssg.core.streamline;

import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.minecraft.MinecraftWindow;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanDevice;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanUtils.VK_CHECK;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRWin32Surface.*;
import static org.lwjgl.vulkan.VK10.*;

public final class DLSSGPresentBackend implements AutoCloseable {
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private long surface;
    private long swapchain;
    private int swapchainFormat;
    private VkExtent2D swapchainExtent;
    private long[] swapchainImages = new long[0];
    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private final long[] imageAvailable = new long[MAX_FRAMES_IN_FLIGHT];
    private final long[] renderFinished = new long[MAX_FRAMES_IN_FLIGHT];
    private final long[] inFlight = new long[MAX_FRAMES_IN_FLIGHT];
    private int currentFrame;
    private boolean initialized;
    private boolean failed;

    public boolean initialize() {
        if (initialized) {
            return true;
        }
        if (failed || !MCDLSSGConfig.isEnableDLSSFrameGeneration()
                || RenderSystems.vulkan() == null || !Streamline.isInitialized()) {
            return false;
        }
        try {
            createSurface();
            createSwapchain();
            createCommandResources();
            createSyncObjects();
            initialized = true;
            return true;
        } catch (Throwable throwable) {
            failed = true;
            MCDLSSG.LOGGER.error("Failed to initialize DLSS G Vulkan presentation backend", throwable);
            close();
            return false;
        }
    }

    public boolean present() {
        if (failed || !initialize()) {
            return false;
        }
        if (isFramebufferSizeChanged()) {
            DLSSGRuntime.resize();
            recreateSwapchain();
        }
        VulkanDevice device = RenderSystems.vulkan().device();
        boolean prepared = false;
        try {
            VK_CHECK(vkWaitForFences(device.getVkDevice(), inFlight[currentFrame], true, 1_000_000_000L));
            VK_CHECK(vkResetFences(device.getVkDevice(), inFlight[currentFrame]));
            IntBuffer imageIndex;
            try (MemoryStack stack = stackPush()) {
                imageIndex = stack.mallocInt(1);
                int acquire = vkAcquireNextImageKHR(
                        device.getVkDevice(), swapchain, Long.MAX_VALUE,
                        imageAvailable[currentFrame], VK_NULL_HANDLE, imageIndex
                );
                if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
                    recreateSwapchain();
                    acquire = vkAcquireNextImageKHR(
                            device.getVkDevice(), swapchain, Long.MAX_VALUE,
                            imageAvailable[currentFrame], VK_NULL_HANDLE, imageIndex
                    );
                }
                if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) {
                    VK_CHECK(acquire);
                }
                int index = imageIndex.get(0);
                boolean contentReady = DLSSGRuntime.preparePresent();
                prepared = contentReady;
                if (contentReady && !"1".equals(System.getenv("SR_DLSSG_TEST_BLANK"))) {
                    recordCommandBuffer(commandBuffers[currentFrame], swapchainImages[index]);
                } else {
                    DLSSGRuntime.presentEnd();
                    recordBlankCommandBuffer(commandBuffers[currentFrame], swapchainImages[index]);
                }
                prepared = true;

                long ready = DLSSGRuntime.readySemaphore();
                LongBuffer waits = ready == 0
                        ? stack.longs(imageAvailable[currentFrame])
                        : stack.longs(imageAvailable[currentFrame], ready);
                IntBuffer stages = ready == 0
                        ? stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT)
                        : stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pWaitSemaphores(waits)
                        .pWaitDstStageMask(stages)
                        .pCommandBuffers(stack.pointers(commandBuffers[currentFrame].address()))
                        .pSignalSemaphores(stack.longs(renderFinished[currentFrame]));
                DLSSGRuntime.renderSubmitStart();
                VK_CHECK(vkQueueSubmit(device.getMainQueue().getQueue(), submit, inFlight[currentFrame]));
                DLSSGRuntime.renderSubmitEnd();

                VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                        .pWaitSemaphores(stack.longs(renderFinished[currentFrame]))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(swapchain))
                        .pImageIndices(stack.ints(index));
                int result = vkQueuePresentKHR(device.getMainQueue().getQueue(), present);
                DLSSGRuntime.presentEnd();
                if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                    recreateSwapchain();
                } else {
                    VK_CHECK(result);
                }
            }
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            return true;
        } catch (Throwable throwable) {
            MCDLSSG.LOGGER.error("DLSS G Vulkan present failed; tearing down Vulkan presentation", throwable);
            if (prepared) {
                consumeReadySemaphore(device);
            }
            failed = true;
            DLSSGRuntime.presentEnd();
            try {
                close();
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private void recordBlankCommandBuffer(VkCommandBuffer commandBuffer, long swapchainImage) {
        try (MemoryStack stack = stackPush()) {
            VK_CHECK(vkResetCommandBuffer(commandBuffer, 0));
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VK_CHECK(vkBeginCommandBuffer(commandBuffer, begin));
            transition(stack, commandBuffer, swapchainImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            VkClearColorValue clearColor = VkClearColorValue.calloc(stack);
            if ("1".equals(System.getenv("SR_DLSSG_TEST_BLANK"))) {
                clearColor.float32(0, 1).float32(1, 0).float32(2, 1).float32(3, 1);
            } else {
                clearColor.float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 1);
            }
            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .levelCount(1)
                    .layerCount(1);
            vkCmdClearColorImage(commandBuffer, swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor, range);
            transition(stack, commandBuffer, swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_ACCESS_TRANSFER_WRITE_BIT, 0, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
            VK_CHECK(vkEndCommandBuffer(commandBuffer));
        }
    }

    private void consumeReadySemaphore(VulkanDevice device) {
        long ready = DLSSGRuntime.readySemaphore();
        if (ready == 0) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pWaitSemaphores(stack.longs(ready))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            vkQueueSubmit(device.getMainQueue().getQueue(), submit, VK_NULL_HANDLE);
        } catch (Throwable ignored) {
        }
    }

    private void createSurface() {
        VulkanDevice device = RenderSystems.vulkan().device();
        try (MemoryStack stack = stackPush()) {
            VkWin32SurfaceCreateInfoKHR info = VkWin32SurfaceCreateInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_WIN32_SURFACE_CREATE_INFO_KHR)
                    .hinstance(org.lwjgl.system.windows.WindowsLibrary.HINSTANCE)
                    .hwnd(glfwGetWin32Window(MinecraftWindow.getWindowHandle()));
            LongBuffer out = stack.mallocLong(1);
            VK_CHECK(vkCreateWin32SurfaceKHR(device.getVkInstance(), info, null, out));
            surface = out.get(0);
        }
    }

    private void createSwapchain() {
        VulkanDevice device = RenderSystems.vulkan().device();
        try (MemoryStack stack = stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.getPhysicalDevice(), surface, capabilities));
            IntBuffer formatCount = stack.ints(0);
            VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(device.getPhysicalDevice(), surface, formatCount, null));
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
            VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(device.getPhysicalDevice(), surface, formatCount, formats));
            VkSurfaceFormatKHR selected = formats.get(0);
            for (VkSurfaceFormatKHR format : formats) {
                if (format.format() == VK_FORMAT_R8G8B8A8_UNORM || format.format() == VK_FORMAT_B8G8R8A8_UNORM) {
                    selected = format;
                    break;
                }
            }
            swapchainFormat = selected.format();
            swapchainExtent = VkExtent2D.calloc();
            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                swapchainExtent.set(capabilities.currentExtent());
            } else {
                int[] width = new int[1];
                int[] height = new int[1];
                glfwGetFramebufferSize(MinecraftWindow.getWindowHandle(), width, height);
                swapchainExtent.width(Math.max(capabilities.minImageExtent().width(), Math.min(width[0], capabilities.maxImageExtent().width())));
                swapchainExtent.height(Math.max(capabilities.minImageExtent().height(), Math.min(height[0], capabilities.maxImageExtent().height())));
            }
            int imageCount = Math.max(capabilities.minImageCount() + 1, 3);
            if (capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
            }
            VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(swapchainFormat)
                    .imageColorSpace(selected.colorSpace())
                    .imageExtent(swapchainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);
            LongBuffer out = stack.mallocLong(1);
            VK_CHECK(vkCreateSwapchainKHR(device.getVkDevice(), info, null, out));
            swapchain = out.get(0);
            IntBuffer count = stack.ints(0);
            VK_CHECK(vkGetSwapchainImagesKHR(device.getVkDevice(), swapchain, count, null));
            LongBuffer images = stack.mallocLong(count.get(0));
            VK_CHECK(vkGetSwapchainImagesKHR(device.getVkDevice(), swapchain, count, images));
            swapchainImages = new long[count.get(0)];
            images.get(swapchainImages);
        }
    }

    private void createCommandResources() {
        VulkanDevice device = RenderSystems.vulkan().device();
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(device.getMainQueue().getQueueFamilyIndex())
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            LongBuffer pool = stack.mallocLong(1);
            VK_CHECK(vkCreateCommandPool(device.getVkDevice(), poolInfo, null, pool));
            commandPool = pool.get(0);
            VkCommandBufferAllocateInfo alloc = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(MAX_FRAMES_IN_FLIGHT);
            PointerBuffer buffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            VK_CHECK(vkAllocateCommandBuffers(device.getVkDevice(), alloc, buffers));
            commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
            for (int index = 0; index < commandBuffers.length; index++) {
                commandBuffers[index] = new VkCommandBuffer(buffers.get(index), device.getVkDevice());
            }
        }
    }

    private void createSyncObjects() {
        VulkanDevice device = RenderSystems.vulkan().device();
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphore = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfo fence = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            for (int index = 0; index < MAX_FRAMES_IN_FLIGHT; index++) {
                LongBuffer out = stack.mallocLong(1);
                VK_CHECK(vkCreateSemaphore(device.getVkDevice(), semaphore, null, out));
                imageAvailable[index] = out.get(0);
                VK_CHECK(vkCreateSemaphore(device.getVkDevice(), semaphore, null, out));
                renderFinished[index] = out.get(0);
                VK_CHECK(vkCreateFence(device.getVkDevice(), fence, null, out));
                inFlight[index] = out.get(0);
            }
        }
    }

    private void recordCommandBuffer(VkCommandBuffer commandBuffer, long swapchainImage) {
        VulkanDevice device = RenderSystems.vulkan().device();
        ITexture sourceTexture = DLSSGRuntime.finalColor();
        if (!(sourceTexture instanceof com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanTexture source)) {
            throw new IllegalStateException("DLSS G final color is not a Vulkan texture");
        }
        try (MemoryStack stack = stackPush()) {
            VK_CHECK(vkResetCommandBuffer(commandBuffer, 0));
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VK_CHECK(vkBeginCommandBuffer(commandBuffer, begin));
            transition(stack, commandBuffer, swapchainImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            transition(stack, commandBuffer, source.handle(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_ACCESS_MEMORY_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.srcOffsets(1).set(source.getWidth(), source.getHeight(), 1);
            region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.dstOffsets(1).set(swapchainExtent.width(), swapchainExtent.height(), 1);
            vkCmdBlitImage(commandBuffer, source.handle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_LINEAR);
            transition(stack, commandBuffer, source.handle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,
                    VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_MEMORY_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            transition(stack, commandBuffer, swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_ACCESS_TRANSFER_WRITE_BIT, 0, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
            VK_CHECK(vkEndCommandBuffer(commandBuffer));
        }
    }

    private static void transition(MemoryStack stack, VkCommandBuffer commandBuffer, long image,
                                   int oldLayout, int newLayout, int srcAccess, int dstAccess,
                                   int srcStage, int dstStage) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess);
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, null, null, barrier);
    }

    private boolean isFramebufferSizeChanged() {
        if (swapchain == VK_NULL_HANDLE || swapchainExtent == null) {
            return false;
        }
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(MinecraftWindow.getWindowHandle(), width, height);
        return width[0] > 0 && height[0] > 0
                && (width[0] != swapchainExtent.width() || height[0] != swapchainExtent.height());
    }

    private void recreateSwapchain() {
        VulkanDevice device = RenderSystems.vulkan().device();
        vkDeviceWaitIdle(device.getVkDevice());
        DLSSGRuntime.requestReset();
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device.getVkDevice(), swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
        createSwapchain();
        DLSSGRuntime.resize();
    }

    @Override
    public void close() {
        if (RenderSystems.vulkan() == null) {
            return;
        }
        VulkanDevice device = RenderSystems.vulkan().device();
        vkDeviceWaitIdle(device.getVkDevice());
        for (int index = 0; index < MAX_FRAMES_IN_FLIGHT; index++) {
            if (imageAvailable[index] != VK_NULL_HANDLE) vkDestroySemaphore(device.getVkDevice(), imageAvailable[index], null);
            if (renderFinished[index] != VK_NULL_HANDLE) vkDestroySemaphore(device.getVkDevice(), renderFinished[index], null);
            if (inFlight[index] != VK_NULL_HANDLE) vkDestroyFence(device.getVkDevice(), inFlight[index], null);
        }
        if (commandPool != VK_NULL_HANDLE) vkDestroyCommandPool(device.getVkDevice(), commandPool, null);
        if (swapchain != VK_NULL_HANDLE) vkDestroySwapchainKHR(device.getVkDevice(), swapchain, null);
        if (surface != VK_NULL_HANDLE) vkDestroySurfaceKHR(device.getVkInstance(), surface, null);
        if (swapchainExtent != null) swapchainExtent.free();
        initialized = false;
        commandPool = VK_NULL_HANDLE;
        swapchain = VK_NULL_HANDLE;
        surface = VK_NULL_HANDLE;
    }
}
