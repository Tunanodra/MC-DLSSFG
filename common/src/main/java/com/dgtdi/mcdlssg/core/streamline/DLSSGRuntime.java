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
import com.dgtdi.mcdlssg.common.config.enums.DLSSGMotionVectorMode;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.FrameBufferAttachmentType;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class DLSSGRuntime {
    private static final DLSSGFrameController CONTROLLER = new DLSSGFrameController();
    private static final DLSSGPresentBackend PRESENT_BACKEND = new DLSSGPresentBackend();
    private static final DLSSGEffects EFFECTS = new DLSSGEffects();
    private static ITexture motionVectorsOverride;
    private static Vec3 lastCameraPos;
    private static long realFrameCounter;
    private static long lastSampleTimeMs;
    private static long lastRealFrameSample;
    private static long lastPresentedFrameSample = -1;
    private static double realFps;
    private static double displayedFps;
    private static boolean presentingActive;

    private DLSSGRuntime() {
    }

    public static boolean initialize() {
        if (!MCDLSSGConfig.isEnableDLSSFrameGeneration()
                || !Streamline.isInitialized()
                || !Streamline.isDLSSGAvailable()
                || RenderSystems.vulkan() == null
                || RenderSystems.opengl() == null) {
            return false;
        }
        EFFECTS.ensureTextures(
                RenderHandlerManager.getScreenWidth(),
                RenderHandlerManager.getScreenHeight(),
                RenderHandlerManager.getRenderWidth(),
                RenderHandlerManager.getRenderHeight()
        );
        return CONTROLLER.initialize(
                RenderHandlerManager.getScreenWidth(),
                RenderHandlerManager.getScreenHeight(),
                RenderHandlerManager.getRenderWidth(),
                RenderHandlerManager.getRenderHeight()
        );
    }

    public static void beginFrame() {
        if (!CONTROLLER.isInitialized()) {
            initialize();
        }
        CONTROLLER.beginFrame();
    }

    public static void simulationEnd() {
        CONTROLLER.simulationEnd();
    }

    public static void renderSubmitStart() {
        CONTROLLER.renderSubmitStart();
    }

    public static void renderSubmitEnd() {
        CONTROLLER.renderSubmitEnd();
    }

    public static void captureHudless() {
        if (!CONTROLLER.isInitialized()) {
            return;
        }
        ITexture finalColor = originColor();
        if (finalColor != null) {
            EFFECTS.captureHudless(finalColor);
        }
    }

    public static void setMotionVectors(ITexture value) {
        motionVectorsOverride = value;
    }

    public static boolean preparePresent() {
        if (!CONTROLLER.isInitialized()) {
            return false;
        }
        detectCameraTeleport();
        ITexture finalColor = originColor();
        ITexture sourceDepth = RenderHandlerManager.getDepthTexture();
        DLSSGMotionVectorMode mvMode = MCDLSSGConfig.getDLSSFrameGenerationMotionVectorMode();
        ITexture depth;
        ITexture motionVectors;
        if (mvMode == DLSSGMotionVectorMode.DISABLED) {
            motionVectors = EFFECTS.invalidMotionVectors(sourceDepth);
            depth = EFFECTS.upscaledDepth() != null ? EFFECTS.upscaledDepth() : sourceDepth;
        } else {
            ITexture provided = motionVectorsOverride != null
                    ? motionVectorsOverride
                    : (mvMode == DLSSGMotionVectorMode.AUTO
                    && SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)
                    ? RenderHandlerManager.getMotionVectorsTexture()
                    : null);
            if (provided != null) {
                ITexture[] data = EFFECTS.upscaleExternal(sourceDepth, provided);
                if (data != null) {
                    depth = data[0];
                    motionVectors = data[1];
                } else {
                    depth = sourceDepth;
                    motionVectors = provided;
                }
            } else {
                motionVectors = EFFECTS.generateFallbackMotionVectors(sourceDepth);
                depth = EFFECTS.upscaledDepth() != null ? EFFECTS.upscaledDepth() : sourceDepth;
            }
        }
        ITexture hudless = EFFECTS.hudlessTexture() != null ? EFFECTS.hudlessTexture() : finalColor;
        ITexture ui = MCDLSSGConfig.isDLSSFrameGenerationUIRecompositionEnabled()
                ? EFFECTS.extractUI(finalColor)
                : null;
        return CONTROLLER.preparePresent(finalColor, hudless, ui, depth, motionVectors);
    }

    private static void detectCameraTeleport() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            lastCameraPos = null;
            return;
        }
        Vec3 pos = minecraft.gameRenderer.getMainCamera().getPosition();
        if (lastCameraPos != null && pos.distanceToSqr(lastCameraPos) > 100.0) {
            requestReset();
        }
        lastCameraPos = pos;
    }

    private static ITexture originColor() {
        return RenderHandlerManager.getOriginRenderTarget() == null
                ? null
                : RenderHandlerManager.getOriginRenderTarget().getTexture(FrameBufferAttachmentType.Color);
    }

    public static void presentEnd() {
        CONTROLLER.presentEnd();
        motionVectorsOverride = null;
    }

    public static void resize() {
        if (CONTROLLER.isInitialized()) {
            EFFECTS.ensureTextures(
                    RenderHandlerManager.getScreenWidth(),
                    RenderHandlerManager.getScreenHeight(),
                    RenderHandlerManager.getRenderWidth(),
                    RenderHandlerManager.getRenderHeight()
            );
            EFFECTS.requestReprojectReset();
            CONTROLLER.resize(
                    RenderHandlerManager.getScreenWidth(),
                    RenderHandlerManager.getScreenHeight(),
                    RenderHandlerManager.getRenderWidth(),
                    RenderHandlerManager.getRenderHeight()
            );
        }
    }

    public static void requestReset() {
        EFFECTS.requestReprojectReset();
        CONTROLLER.requestReset();
    }

    public static long readySemaphore() {
        return CONTROLLER.readySemaphore();
    }

    public static ITexture finalColor() {
        return CONTROLLER.finalColor();
    }

    public static boolean present() {
        boolean presented = PRESENT_BACKEND.present();
        noteFramePresented(presented);
        return presented;
    }

    private static void noteFramePresented(boolean presented) {
        realFrameCounter++;
        presentingActive = presented && CONTROLLER.isFrameGenerationEnabled();
        long now = System.currentTimeMillis();
        if (lastSampleTimeMs == 0) {
            lastSampleTimeMs = now;
            lastRealFrameSample = realFrameCounter;
            StreamlineDLSSGState state = Streamline.getDLSSGState();
            lastPresentedFrameSample = state == null ? -1 : state.numFramesActuallyPresented;
            return;
        }
        long elapsed = now - lastSampleTimeMs;
        if (elapsed >= 500) {
            realFps = (realFrameCounter - lastRealFrameSample) * 1000.0 / elapsed;
            StreamlineDLSSGState state = Streamline.getDLSSGState();
            long presentedDelta = state != null && lastPresentedFrameSample >= 0
                    ? state.numFramesActuallyPresented - lastPresentedFrameSample
                    : 0;
            if (presentedDelta > 0) {
                displayedFps = presentedDelta * 1000.0 / elapsed;
            } else if (presentingActive) {
                displayedFps = realFps * (getGeneratedFrameCount() + 1);
            } else {
                displayedFps = 0;
            }
            lastPresentedFrameSample = state == null ? -1 : state.numFramesActuallyPresented;
            lastRealFrameSample = realFrameCounter;
            lastSampleTimeMs = now;
        }
    }

    public static boolean isIndicatorActive() {
        return MCDLSSGConfig.isEnableDLSSFrameGeneration() && Streamline.isInitialized();
    }

    public static boolean isPresentingActive() {
        return presentingActive;
    }

    public static double getRealFps() {
        return realFps;
    }

    public static double getDisplayedFps() {
        return displayedFps;
    }

    public static int getGeneratedFrameCount() {
        return MCDLSSGConfig.getDLSSFrameGenerationFramesToGenerate();
    }

    public static boolean isReady() {
        return CONTROLLER.isInitialized();
    }

    public static void prepareShutdown() {
        try {
            CONTROLLER.disable();
        } catch (Throwable throwable) {
            MCDLSSG.LOGGER.warn("Failed to disable DLSS G before shutdown", throwable);
        }
    }

    public static void shutdown() {
        try {
            PRESENT_BACKEND.close();
            CONTROLLER.close();
            EFFECTS.close();
        } catch (Throwable throwable) {
            MCDLSSG.LOGGER.warn("Failed to shut down DLSS G runtime", throwable);
        }
        motionVectorsOverride = null;
    }
}
