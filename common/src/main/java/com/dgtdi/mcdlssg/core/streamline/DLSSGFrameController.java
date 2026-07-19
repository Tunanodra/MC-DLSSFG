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
import com.dgtdi.mcdlssg.common.upscale.AlgorithmManager;
import com.dgtdi.mcdlssg.common.upscale.VulkanInteropAlgorithm;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;

public final class DLSSGFrameController implements AutoCloseable {
    private static final StreamlineTypes.Viewport VIEWPORT = new StreamlineTypes.Viewport(0);

    private final DLSSGFrameResources resources = new DLSSGFrameResources();
    private StreamlineTypes.FrameToken frameToken;
    private DLSSGFrameResources.Slot currentSlot;
    private int frameIndex;
    private boolean initialized;
    private boolean frameOpen;
    private boolean optionsEnabled;
    private boolean reset = true;
    private long lastPresentTimeMs;
    private int stallCooldown;

    public boolean initialize(int colorWidth, int colorHeight, int dataWidth, int dataHeight) {
        if (!MCDLSSGConfig.isEnableDLSSFrameGeneration() || !Streamline.isInitialized()
                || !Streamline.isDLSSGAvailable()) {
            return false;
        }
        resources.initialize(colorWidth, colorHeight, dataWidth, dataHeight);
        StreamlineTypes.ReflexOptions reflexOptions = new StreamlineTypes.ReflexOptions();
        reflexOptions.mode = StreamlineTypes.ReflexMode.LOW_LATENCY;
        Streamline.session().reflexSetOptions(reflexOptions);
        int offResult = Streamline.session().dlssGSetOptions(VIEWPORT, offOptions());
        if (offResult != 0) {
            MCDLSSG.LOGGER.warn("Initial slDLSSGSetOptions(off) failed. result={}", offResult);
        }
        optionsEnabled = false;
        initialized = true;
        reset = true;
        return true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isFrameGenerationEnabled() {
        return optionsEnabled;
    }

    public void resize(int colorWidth, int colorHeight, int dataWidth, int dataHeight) {
        if (!initialized || resources.matches(colorWidth, colorHeight, dataWidth, dataHeight)) {
            return;
        }
        disable();
        resources.initialize(colorWidth, colorHeight, dataWidth, dataHeight);
        reset = true;
    }

    public void beginFrame() {
        if (!initialized || frameOpen) {
            return;
        }
        frameToken = new StreamlineTypes.FrameToken();
        int result = Streamline.session().getNewFrameToken(frameIndex++, frameToken);
        if (result != 0) {
            fail("slGetNewFrameToken", result);
            return;
        }
        frameOpen = true;
        Streamline.session().pclSetMarker(StreamlineTypes.PclMarker.CONTROLLER_INPUT_SAMPLE, frameToken);
        Streamline.session().reflexSleep(frameToken);
        Streamline.session().pclSetMarker(StreamlineTypes.PclMarker.SIMULATION_START, frameToken);
    }

    public void simulationEnd() {
        marker(StreamlineTypes.PclMarker.SIMULATION_END);
    }

    public void renderSubmitStart() {
        marker(StreamlineTypes.PclMarker.RENDER_SUBMIT_START);
    }

    public void renderSubmitEnd() {
        marker(StreamlineTypes.PclMarker.RENDER_SUBMIT_END);
    }

    public boolean preparePresent(
            ITexture finalColor,
            ITexture hudLessColor,
            ITexture uiColorAlpha,
            ITexture depth,
            ITexture motionVectors
    ) {
        if (!frameOpen) {
            return false;
        }
        boolean gameFrame = !shouldDisableForCurrentScreen() && depth != null && motionVectors != null;
        long now = System.currentTimeMillis();
        if (lastPresentTimeMs != 0 && now - lastPresentTimeMs > 250) {
            stallCooldown = 30;
            reset = true;
            if (optionsEnabled) {
                Streamline.session().dlssGSetOptions(VIEWPORT, offOptions());
                optionsEnabled = false;
            }
        }
        lastPresentTimeMs = now;
        if (stallCooldown > 0) {
            stallCooldown--;
            gameFrame = false;
        }
        currentSlot = resources.acquire();
        if (!currentSlot.populate(finalColor, hudLessColor, uiColorAlpha, depth, motionVectors)) {
            disable();
            return false;
        }
        if (!gameFrame) {
            if (optionsEnabled) {
                int offResult = Streamline.session().dlssGSetOptions(VIEWPORT, offOptions());
                if (offResult != 0) {
                    fail("slDLSSGSetOptions(off)", offResult);
                    return false;
                }
                optionsEnabled = false;
            }
            reset = true;
            marker(StreamlineTypes.PclMarker.PRESENT_START);
            return true;
        }
        int constantsResult = Streamline.session().setConstants(createConstants(), frameToken, VIEWPORT);
        if (constantsResult != 0) {
            fail("slSetConstants", constantsResult);
            return false;
        }
        StreamlineTypes.ResourceTag[] tags = createTags(currentSlot);
        int tagResult = Streamline.session().setTagForFrame(frameToken, VIEWPORT, tags, 0L);
        if (tagResult != 0) {
            fail("slSetTagForFrame", tagResult);
            return false;
        }
        StreamlineTypes.DlssGOptions options = createOnOptions();
        int optionsResult = Streamline.session().dlssGSetOptions(VIEWPORT, options);
        if (optionsResult != 0) {
            fail("slDLSSGSetOptions", optionsResult);
            return false;
        }
        optionsEnabled = true;
        marker(StreamlineTypes.PclMarker.PRESENT_START);
        reset = false;
        return true;
    }

    private StreamlineTypes.DlssGOptions offOptions() {
        StreamlineTypes.DlssGOptions options = new StreamlineTypes.DlssGOptions();
        options.mode = StreamlineTypes.DlssGMode.OFF;
        return options;
    }

    private StreamlineTypes.DlssGOptions createOnOptions() {
        StreamlineTypes.DlssGOptions options = new StreamlineTypes.DlssGOptions();
        options.mode = StreamlineTypes.DlssGMode.ON;
        options.flags = StreamlineTypes.DlssGFlags.ENABLE_FULLSCREEN_MENU_DETECTION
                | StreamlineTypes.DlssGFlags.RETAIN_RESOURCES_WHEN_OFF;
        int requested = MCDLSSGConfig.getDLSSFrameGenerationFramesToGenerate();
        StreamlineDLSSGState state = Streamline.getDLSSGState();
        if (state != null && state.numFramesToGenerateMax > 0) {
            requested = Math.min(requested, state.numFramesToGenerateMax);
        }
        options.numFramesToGenerate = Math.max(1, requested);
        options.numBackBuffers = DLSSGFrameResources.FRAME_COUNT;
        options.motionVectorDepthWidth = currentSlot.depth().getWidth();
        options.motionVectorDepthHeight = currentSlot.depth().getHeight();
        options.colorWidth = currentSlot.finalColor().getWidth();
        options.colorHeight = currentSlot.finalColor().getHeight();
        options.colorBufferFormat = currentSlot.finalColor().getTextureFormat().vk();
        options.motionVectorBufferFormat = currentSlot.motionVectors().getTextureFormat().vk();
        options.depthBufferFormat = currentSlot.depth().getTextureFormat().vk();
        options.hudLessBufferFormat = currentSlot.hudLessColor().getTextureFormat().vk();
        options.uiBufferFormat = currentSlot.uiColorAlpha().getTextureFormat().vk();
        options.enableUserInterfaceRecomposition = (byte) (
                MCDLSSGConfig.isDLSSFrameGenerationUIRecompositionEnabled() ? 1 : 0
        );
        return options;
    }

    public void presentEnd() {
        marker(StreamlineTypes.PclMarker.PRESENT_END);
        frameOpen = false;
        frameToken = null;
        currentSlot = null;
    }

    public long readySemaphore() {
        return currentSlot == null ? 0L : currentSlot.readySemaphore();
    }

    public VulkanTexture finalColor() {
        return currentSlot == null ? null : currentSlot.finalColor();
    }

    public void requestReset() {
        reset = true;
    }

    public void disable() {
        if (optionsEnabled && Streamline.isInitialized()) {
            Streamline.setDLSSGOptions(false, 1);
        }
        optionsEnabled = false;
        frameOpen = false;
        frameToken = null;
        currentSlot = null;
        reset = true;
    }

    private void marker(int marker) {
        if (frameOpen && frameToken != null) {
            Streamline.session().pclSetMarker(marker, frameToken);
        }
    }

    private boolean shouldDisableForCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || minecraft.screen != null;
    }

    private StreamlineTypes.Constants createConstants() {
        VulkanInteropAlgorithm.FrameData frameData = null;
        if (MCDLSSG.currentAlgorithm instanceof VulkanInteropAlgorithm algorithm) {
            frameData = algorithm.currentFrameData();
        }
        StreamlineTypes.Constants constants = new StreamlineTypes.Constants();
        Matrix4f projection = frameData != null
                ? new Matrix4f(frameData.projectionMatrix())
                : new Matrix4f(AlgorithmManager.param.currentProjectionMatrix);
        Matrix4f previousProjection = frameData != null
                ? new Matrix4f(frameData.lastProjectionMatrix())
                : new Matrix4f(AlgorithmManager.param.lastProjectionMatrix);
        Matrix4f viewProjection = frameData != null
                ? new Matrix4f(frameData.modelViewProjectionMatrix())
                : new Matrix4f(AlgorithmManager.param.currentModelViewProjectionMatrix);
        Matrix4f previousViewProjection = frameData != null
                ? new Matrix4f(frameData.lastModelViewProjectionMatrix())
                : new Matrix4f(AlgorithmManager.param.lastModelViewProjectionMatrix);

        constants.cameraViewToClip = matrix(projection);
        constants.clipToCameraView = matrix(new Matrix4f(projection).invert());
        constants.clipToLensClip = matrix(new Matrix4f());
        constants.clipToPrevClip = matrix(new Matrix4f(previousViewProjection).mul(new Matrix4f(viewProjection).invert()));
        constants.prevClipToClip = matrix(new Matrix4f(viewProjection).mul(new Matrix4f(previousViewProjection).invert()));
        if (frameData != null) {
            constants.jitterOffsetX = frameData.jitterOffset().x;
            constants.jitterOffsetY = frameData.jitterOffset().y;
            constants.cameraNear = frameData.cameraNear();
            constants.cameraFar = frameData.cameraFar();
            constants.cameraFov = (float) Math.toRadians(frameData.verticalFov());
            constants.cameraAspectRatio = frameData.screenSize().x / frameData.screenSize().y;
        }
        constants.motionVectorScaleX = 1.0f;
        constants.motionVectorScaleY = 1.0f;
        constants.depthInverted = 0;
        constants.cameraMotionIncluded = 1;
        constants.motionVectors3D = 0;
        constants.motionVectorsInvalidValue = -32768.0f;
        constants.reset = (byte) (reset ? 1 : 0);
        constants.motionVectorsDilated = 1;
        constants.motionVectorsJittered = 0;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f position = new Vector3f(
                (float) camera.getPosition().x,
                (float) camera.getPosition().y,
                (float) camera.getPosition().z
        );
        Vector3f forward = new Vector3f(camera.getLookVector().x, camera.getLookVector().y, camera.getLookVector().z);
        Vector3f up = new Vector3f(camera.getUpVector().x, camera.getUpVector().y, camera.getUpVector().z);
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        constants.cameraPosX = position.x;
        constants.cameraPosY = position.y;
        constants.cameraPosZ = position.z;
        constants.cameraFwdX = forward.x;
        constants.cameraFwdY = forward.y;
        constants.cameraFwdZ = forward.z;
        constants.cameraUpX = up.x;
        constants.cameraUpY = up.y;
        constants.cameraUpZ = up.z;
        constants.cameraRightX = right.x;
        constants.cameraRightY = right.y;
        constants.cameraRightZ = right.z;
        return constants;
    }

    private static float[] matrix(Matrix4f matrix) {
        return matrix.get(new float[16]);
    }

    private static StreamlineTypes.ResourceTag[] createTags(DLSSGFrameResources.Slot slot) {
        return new StreamlineTypes.ResourceTag[]{
                tag(slot.depth(), StreamlineTypes.BufferType.DEPTH),
                tag(slot.motionVectors(), StreamlineTypes.BufferType.MOTION_VECTORS),
                tag(slot.hudLessColor(), StreamlineTypes.BufferType.HUD_LESS_COLOR),
                tag(slot.uiColorAlpha(), StreamlineTypes.BufferType.UI_COLOR_AND_ALPHA)
        };
    }

    private static StreamlineTypes.ResourceTag tag(VulkanTexture texture, int type) {
        StreamlineTypes.Resource resource = new StreamlineTypes.Resource();
        resource.nativeHandle = texture.handle();
        resource.memory = texture.getImageMemory();
        resource.view = texture.getImageView();
        resource.state = VK_IMAGE_LAYOUT_GENERAL;
        resource.width = texture.getWidth();
        resource.height = texture.getHeight();
        resource.nativeFormat = texture.getTextureFormat().vk();
        resource.mipLevels = texture.getMipmapSettings().getLevels();
        resource.arrayLayers = 1;

        StreamlineTypes.Extent extent = new StreamlineTypes.Extent();
        extent.width = texture.getWidth();
        extent.height = texture.getHeight();

        StreamlineTypes.ResourceTag tag = new StreamlineTypes.ResourceTag();
        tag.resource = resource;
        tag.type = type;
        tag.lifecycle = StreamlineTypes.ResourceLifecycle.VALID_UNTIL_PRESENT;
        tag.extent = extent;
        return tag;
    }

    private void fail(String operation, int result) {
        MCDLSSG.LOGGER.error("{} failed. result={} ({})", operation, StreamlineResult.nameOf(result), result);
        disable();
    }

    @Override
    public void close() {
        disable();
        resources.close();
        initialized = false;
    }
}
