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
import com.dgtdi.mcdlssg.common.upscale.InteropResourcesConverter;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureFormat;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureType;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureUsages;
import com.dgtdi.mcdlssg.core.graphics.opengl.texture.GlImportableTexture2D;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VkGlInteropSemaphore;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanDevice;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanTexture;

import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;

public final class DLSSGFrameResources implements AutoCloseable {
    public static final int FRAME_COUNT = 3;

    private final Slot[] slots = new Slot[FRAME_COUNT];
    private int colorWidth;
    private int colorHeight;
    private int dataWidth;
    private int dataHeight;
    private int nextSlot;

    public void initialize(int colorWidth, int colorHeight, int dataWidth, int dataHeight) {
        close();
        this.colorWidth = Math.max(1, colorWidth);
        this.colorHeight = Math.max(1, colorHeight);
        this.dataWidth = Math.max(1, dataWidth);
        this.dataHeight = Math.max(1, dataHeight);
        for (int index = 0; index < FRAME_COUNT; index++) {
            slots[index] = new Slot(index, this.colorWidth, this.colorHeight, this.dataWidth, this.dataHeight);
        }
    }

    public boolean isInitialized() {
        return slots[0] != null;
    }

    public boolean matches(int colorWidth, int colorHeight, int dataWidth, int dataHeight) {
        return isInitialized()
                && this.colorWidth == colorWidth && this.colorHeight == colorHeight
                && this.dataWidth == dataWidth && this.dataHeight == dataHeight;
    }

    public Slot acquire() {
        if (!isInitialized()) {
            throw new IllegalStateException("DLSS G frame resources are not initialized");
        }
        Slot slot = slots[nextSlot];
        nextSlot = (nextSlot + 1) % FRAME_COUNT;
        return slot;
    }

    @Override
    public void close() {
        if (RenderSystems.vulkan() != null) {
            RenderSystems.vulkan().device().getMainQueue().waitIdle();
        }
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] != null) {
                slots[index].close();
                slots[index] = null;
            }
        }
        nextSlot = 0;
        colorWidth = colorHeight = dataWidth = dataHeight = 0;
    }

    public static final class Slot implements AutoCloseable {
        private final int index;
        private final int colorWidth;
        private final int colorHeight;
        private final int dataWidth;
        private final int dataHeight;
        private VulkanTexture finalColorVk;
        private GlImportableTexture2D finalColorGl;
        private VulkanTexture hudLessColorVk;
        private GlImportableTexture2D hudLessColorGl;
        private VulkanTexture uiColorAlphaVk;
        private GlImportableTexture2D uiColorAlphaGl;
        private VulkanTexture depthVk;
        private GlImportableTexture2D depthGl;
        private VulkanTexture motionVectorsVk;
        private GlImportableTexture2D motionVectorsGl;
        private VkGlInteropSemaphore glReady;
        private boolean populated;

        private Slot(int index, int colorWidth, int colorHeight, int dataWidth, int dataHeight) {
            this.index = index;
            this.colorWidth = colorWidth;
            this.colorHeight = colorHeight;
            this.dataWidth = dataWidth;
            this.dataHeight = dataHeight;
            create();
        }

        private void create() {
            VulkanDevice vkDevice = RenderSystems.vulkan().device();
            finalColorVk = createExportable(vkDevice, TextureFormat.RGBA8, colorWidth, colorHeight, "DLSSG Final Color");
            finalColorGl = RenderSystems.opengl().device().createTextureImportable(finalColorVk);
            hudLessColorVk = createExportable(vkDevice, TextureFormat.RGBA8, colorWidth, colorHeight, "DLSSG HUD-less Color");
            hudLessColorGl = RenderSystems.opengl().device().createTextureImportable(hudLessColorVk);
            uiColorAlphaVk = createExportable(vkDevice, TextureFormat.RGBA8, colorWidth, colorHeight, "DLSSG UI Color Alpha");
            uiColorAlphaGl = RenderSystems.opengl().device().createTextureImportable(uiColorAlphaVk);
            depthVk = createExportable(vkDevice, TextureFormat.R32F, colorWidth, colorHeight, "DLSSG Depth");
            depthGl = RenderSystems.opengl().device().createTextureImportable(depthVk);
            motionVectorsVk = createExportable(vkDevice, TextureFormat.RG16F, colorWidth, colorHeight, "DLSSG Motion Vectors");
            motionVectorsGl = RenderSystems.opengl().device().createTextureImportable(motionVectorsVk);
            glReady = VkGlInteropSemaphore.create(vkDevice);
        }

        private VulkanTexture createExportable(VulkanDevice device, TextureFormat format, int width, int height, String label) {
            return device.createTextureExportable(
                    TextureDescription.create()
                            .type(TextureType.Texture2D)
                            .format(format)
                            .size(width, height)
                            .usages(TextureUsages.create()
                                    .sampler()
                                    .storage()
                                    .transferSource()
                                    .transferDestination())
                            .label(label + "-" + index)
                            .build()
            );
        }

        public boolean populate(
                ITexture finalColor,
                ITexture hudLessColor,
                ITexture uiColorAlpha,
                ITexture depth,
                ITexture motionVectors
        ) {
            if (finalColor == null) {
                populated = false;
                return false;
            }
            boolean gameFrame = depth != null && motionVectors != null;
            String motionVectorPreprocessingFunction =
                    SRWorkModeManager.getCurrentState().motionVectorPreprocessingFunction();

            InteropResourcesConverter.processInputTextures(
                    finalColor, finalColorGl,
                    depth, gameFrame ? depthGl : null,
                    motionVectors, gameFrame ? motionVectorsGl : null,
                    null, null,
                    gameFrame ? motionVectorPreprocessingFunction : null
            );
            InteropResourcesConverter.processInputTextures(
                    hudLessColor != null ? hudLessColor : finalColor, hudLessColorGl,
                    null, null,
                    null, null,
                    null, null
            );
            if (gameFrame && MCDLSSGConfig.isDLSSFrameGenerationUIRecompositionEnabled() && uiColorAlpha != null) {
                InteropResourcesConverter.processInputTextures(
                        uiColorAlpha, uiColorAlphaGl,
                        null, null,
                        null, null,
                        null, null
                );
            } else {
                clear(uiColorAlphaGl);
            }
            glReady.signalVulkan(
                    gameFrame
                            ? new int[]{
                            Math.toIntExact(finalColorGl.handle()),
                            Math.toIntExact(hudLessColorGl.handle()),
                            Math.toIntExact(uiColorAlphaGl.handle()),
                            Math.toIntExact(depthGl.handle()),
                            Math.toIntExact(motionVectorsGl.handle())
                    }
                            : new int[]{
                            Math.toIntExact(finalColorGl.handle()),
                            Math.toIntExact(hudLessColorGl.handle())
                    },
                    new int[0],
                    gameFrame
                            ? new int[]{
                            GL_LAYOUT_GENERAL_EXT,
                            GL_LAYOUT_GENERAL_EXT,
                            GL_LAYOUT_GENERAL_EXT,
                            GL_LAYOUT_GENERAL_EXT,
                            GL_LAYOUT_GENERAL_EXT
                    }
                            : new int[]{
                            GL_LAYOUT_GENERAL_EXT,
                            GL_LAYOUT_GENERAL_EXT
                    }
            );
            finalColorVk.setCurrentLayout(VK_IMAGE_LAYOUT_GENERAL);
            hudLessColorVk.setCurrentLayout(VK_IMAGE_LAYOUT_GENERAL);
            uiColorAlphaVk.setCurrentLayout(VK_IMAGE_LAYOUT_GENERAL);
            depthVk.setCurrentLayout(VK_IMAGE_LAYOUT_GENERAL);
            motionVectorsVk.setCurrentLayout(VK_IMAGE_LAYOUT_GENERAL);
            populated = true;
            return true;
        }

        private static void clear(ITexture texture) {
            ICommandBuffer commandBuffer = RenderSystems.opengl().device().defaultCommandPool().createCommandBuffer();
            try {
                commandBuffer.begin();
                commandBuffer.clearTextureRGBA(texture, new float[]{0, 0, 0, 0});
                commandBuffer.end();
                RenderSystems.opengl().device().submitCommandBuffer(commandBuffer);
                commandBuffer.waitForFence();
            } catch (Throwable throwable) {
                MCDLSSG.LOGGER.debug("Failed to clear DLSS G UI texture", throwable);
            } finally {
                commandBuffer.destroy();
            }
        }

        public boolean isPopulated() {
            return populated;
        }

        public long readySemaphore() {
            return glReady.getVkSemaphoreHandle();
        }

        public VulkanTexture finalColor() {
            return finalColorVk;
        }

        public VulkanTexture hudLessColor() {
            return hudLessColorVk;
        }

        public VulkanTexture uiColorAlpha() {
            return uiColorAlphaVk;
        }

        public VulkanTexture depth() {
            return depthVk;
        }

        public VulkanTexture motionVectors() {
            return motionVectorsVk;
        }

        @Override
        public void close() {
            populated = false;
            if (glReady != null) glReady.destroy();
            if (motionVectorsGl != null) motionVectorsGl.destroy();
            if (motionVectorsVk != null) motionVectorsVk.destroy();
            if (depthGl != null) depthGl.destroy();
            if (depthVk != null) depthVk.destroy();
            if (uiColorAlphaGl != null) uiColorAlphaGl.destroy();
            if (uiColorAlphaVk != null) uiColorAlphaVk.destroy();
            if (hudLessColorGl != null) hudLessColorGl.destroy();
            if (hudLessColorVk != null) hudLessColorVk.destroy();
            if (finalColorGl != null) finalColorGl.destroy();
            if (finalColorVk != null) finalColorVk.destroy();
        }
    }
}
