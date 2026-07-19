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
import com.dgtdi.mcdlssg.common.upscale.AlgorithmManager;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.graphics.impl.CopyOperation;
import com.dgtdi.mcdlssg.core.graphics.impl.buffer.BufferDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.buffer.BufferUsages;
import com.dgtdi.mcdlssg.core.graphics.impl.buffer.IBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.ComputePipeline;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.IShaderProgram;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.ShaderDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.ShaderSource;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.ShaderType;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureFormat;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureType;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureUsages;
import com.dgtdi.mcdlssg.core.graphics.opengl.utils.GlTextureCopier;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

public final class DLSSGEffects implements AutoCloseable {
    private static final int REPROJECT_UBO_SIZE = 16 * 4 * 2 + 16;

    private ITexture hudlessTexture;
    private ITexture uiTexture;
    private ITexture fallbackMotionVectors;
    private ITexture rawMotionVectors;
    private ITexture previousDepth;
    private ITexture upscaledDepth;
    private IBuffer reprojectUbo;
    private ComputePipeline extractUiPipeline;
    private IShaderProgram extractUiShader;
    private ComputePipeline reprojectPipeline;
    private IShaderProgram reprojectShader;
    private ComputePipeline dilatePipeline;
    private IShaderProgram dilateShader;
    private ComputePipeline upscalePipeline;
    private IShaderProgram upscaleShader;
    private IBuffer upscaleUbo;
    private int colorWidth;
    private int colorHeight;
    private int renderWidth;
    private int renderHeight;
    private boolean reprojectReset = true;

    public void ensureTextures(int colorWidth, int colorHeight, int renderWidth, int renderHeight) {
        if (hudlessTexture != null
                && this.colorWidth == colorWidth && this.colorHeight == colorHeight
                && this.renderWidth == renderWidth && this.renderHeight == renderHeight) {
            return;
        }
        destroyTextures();
        this.colorWidth = Math.max(1, colorWidth);
        this.colorHeight = Math.max(1, colorHeight);
        this.renderWidth = Math.max(1, renderWidth);
        this.renderHeight = Math.max(1, renderHeight);
        hudlessTexture = createTexture(TextureFormat.RGBA8, this.colorWidth, this.colorHeight, "DLSSG HUD-less Capture");
        uiTexture = createTexture(TextureFormat.RGBA8, this.colorWidth, this.colorHeight, "DLSSG UI Capture");
        fallbackMotionVectors = createTexture(TextureFormat.RG16F, this.colorWidth, this.colorHeight, "DLSSG Fallback Motion Vectors");
        rawMotionVectors = createTexture(TextureFormat.RG16F, this.colorWidth, this.colorHeight, "DLSSG Raw Motion Vectors");
        previousDepth = createTexture(TextureFormat.R32F, this.colorWidth, this.colorHeight, "DLSSG Previous Depth");
        upscaledDepth = createTexture(TextureFormat.R32F, this.colorWidth, this.colorHeight, "DLSSG Upscaled Depth");
        reprojectReset = true;
    }

    private static ITexture createTexture(TextureFormat format, int width, int height, String label) {
        return RenderSystems.opengl().device().createTexture(
                TextureDescription.create()
                        .type(TextureType.Texture2D)
                        .format(format)
                        .size(width, height)
                        .usages(TextureUsages.create().sampler().storage().transferSource().transferDestination())
                        .label(label)
                        .build()
        );
    }

    public void captureHudless(ITexture finalColor) {
        if (hudlessTexture == null || finalColor == null) {
            return;
        }
        GlTextureCopier.copy(
                CopyOperation.create()
                        .src(finalColor)
                        .dst(hudlessTexture)
                        .fromTo(CopyOperation.TextureChannel.R, CopyOperation.TextureChannel.R)
                        .fromTo(CopyOperation.TextureChannel.G, CopyOperation.TextureChannel.G)
                        .fromTo(CopyOperation.TextureChannel.B, CopyOperation.TextureChannel.B)
                        .fromTo(CopyOperation.TextureChannel.A, CopyOperation.TextureChannel.A)
        );
    }

    public ITexture hudlessTexture() {
        return hudlessTexture;
    }

    public ITexture extractUI(ITexture finalColor) {
        if (uiTexture == null || hudlessTexture == null || finalColor == null) {
            return null;
        }
        ensureExtractUiPipeline();
        extractUiPipeline.descriptorSet().samplerTexture("finalColor", finalColor);
        extractUiPipeline.descriptorSet().samplerTexture("hudlessColor", hudlessTexture);
        extractUiPipeline.descriptorSet().storageImage("outputUI", uiTexture);
        extractUiPipeline.descriptorSet().update();
        dispatch(extractUiPipeline, colorWidth, colorHeight);
        return uiTexture;
    }

    public ITexture generateFallbackMotionVectors(ITexture depth) {
        if (fallbackMotionVectors == null || depth == null) {
            return null;
        }
        ensureReprojectPipeline();
        ensureDilatePipeline();
        fillReprojectUbo();
        reprojectPipeline.descriptorSet().samplerTexture("inputDepth", depth);
        reprojectPipeline.descriptorSet().samplerTexture("previousDepth", previousDepth);
        reprojectPipeline.descriptorSet().storageImage("outputMotionVectors", rawMotionVectors);
        reprojectPipeline.descriptorSet().storageImage("outputDepth", upscaledDepth);
        reprojectPipeline.descriptorSet().uniformBuffer("ReprojectData", reprojectUbo);
        reprojectPipeline.descriptorSet().update();
        dispatch(reprojectPipeline, colorWidth, colorHeight);
        if (MCDLSSGConfig.isDLSSFrameGenerationMotionVectorDilationEnabled()) {
            dilatePipeline.descriptorSet().samplerTexture("inputMotionVectors", rawMotionVectors);
            dilatePipeline.descriptorSet().samplerTexture("inputDepth", upscaledDepth);
            dilatePipeline.descriptorSet().storageImage("outputMotionVectors", fallbackMotionVectors);
            dilatePipeline.descriptorSet().update();
            dispatch(dilatePipeline, colorWidth, colorHeight);
        } else {
            GlTextureCopier.copy(
                    CopyOperation.create()
                            .src(rawMotionVectors)
                            .dst(fallbackMotionVectors)
                            .fromTo(CopyOperation.TextureChannel.R, CopyOperation.TextureChannel.R)
                            .fromTo(CopyOperation.TextureChannel.G, CopyOperation.TextureChannel.G)
            );
        }
        reprojectReset = false;
        GlTextureCopier.copy(
                CopyOperation.create()
                        .src(upscaledDepth)
                        .dst(previousDepth)
                        .fromTo(CopyOperation.TextureChannel.R, CopyOperation.TextureChannel.R)
        );
        return fallbackMotionVectors;
    }

    public ITexture upscaledDepth() {
        return upscaledDepth;
    }

    public ITexture fallbackMotionVectors() {
        return fallbackMotionVectors;
    }

    public ITexture invalidMotionVectors(ITexture depth) {
        if (fallbackMotionVectors == null || depth == null) {
            return null;
        }
        ensureReprojectPipeline();
        fillReprojectUbo(true);
        reprojectPipeline.descriptorSet().samplerTexture("inputDepth", depth);
        reprojectPipeline.descriptorSet().samplerTexture("previousDepth", previousDepth);
        reprojectPipeline.descriptorSet().storageImage("outputMotionVectors", rawMotionVectors);
        reprojectPipeline.descriptorSet().storageImage("outputDepth", upscaledDepth);
        reprojectPipeline.descriptorSet().uniformBuffer("ReprojectData", reprojectUbo);
        reprojectPipeline.descriptorSet().update();
        dispatch(reprojectPipeline, colorWidth, colorHeight);
        GlTextureCopier.copy(
                CopyOperation.create()
                        .src(rawMotionVectors)
                        .dst(fallbackMotionVectors)
                        .fromTo(CopyOperation.TextureChannel.R, CopyOperation.TextureChannel.R)
                        .fromTo(CopyOperation.TextureChannel.G, CopyOperation.TextureChannel.G)
        );
        return fallbackMotionVectors;
    }

    public ITexture[] upscaleExternal(ITexture depth, ITexture motionVectors) {
        if (depth == null || motionVectors == null || upscaledDepth == null) {
            return null;
        }
        ensureUpscalePipeline();
        if (upscaleUbo == null) {
            upscaleUbo = RenderSystems.opengl().device().createBuffer(
                    BufferDescription.create()
                            .size(16)
                            .usages(BufferUsages.create().ubo().transferDst())
                            .build()
            );
        }
        ByteBuffer buffer = upscaleUbo.map(true);
        buffer.putFloat((float) depth.getWidth());
        buffer.putFloat((float) depth.getHeight());
        buffer.putFloat((float) colorWidth);
        buffer.putFloat((float) colorHeight);
        buffer.rewind();
        upscaleUbo.unmap();
        upscalePipeline.descriptorSet().samplerTexture("inputDepth", depth);
        upscalePipeline.descriptorSet().samplerTexture("inputMotionVectors", motionVectors);
        upscalePipeline.descriptorSet().storageImage("outputDepth", upscaledDepth);
        upscalePipeline.descriptorSet().storageImage("outputMotionVectors", fallbackMotionVectors);
        upscalePipeline.descriptorSet().uniformBuffer("UpscaleData", upscaleUbo);
        upscalePipeline.descriptorSet().update();
        dispatch(upscalePipeline, colorWidth, colorHeight);
        return new ITexture[]{upscaledDepth, fallbackMotionVectors};
    }

    private void ensureUpscalePipeline() {
        if (upscalePipeline != null) {
            return;
        }
        upscaleShader = RenderSystems.opengl().device().createShaderProgram(
                ShaderDescription.create()
                        .compute(new ShaderSource(ShaderType.Compute, "/shader/dlssg/mv_depth_upscale.comp.glsl", true))
                        .name("dlssg_mv_depth_upscale")
                        .uniformSamplerTexture("inputDepth", 0)
                        .uniformSamplerTexture("inputMotionVectors", 1)
                        .uniformStorageTexture("outputDepth", 2)
                        .uniformStorageTexture("outputMotionVectors", 3)
                        .uniformBuffer("UpscaleData", 4, 16)
                        .build()
        );
        upscaleShader.compile();
        upscalePipeline = ComputePipeline.builder()
                .shader(upscaleShader)
                .build(RenderSystems.opengl().device());
    }

    public void requestReprojectReset() {
        reprojectReset = true;
    }

    private void fillReprojectUbo() {
        fillReprojectUbo(false);
    }

    private void fillReprojectUbo(boolean forceReset) {
        if (reprojectUbo == null) {
            reprojectUbo = RenderSystems.opengl().device().createBuffer(
                    BufferDescription.create()
                            .size(REPROJECT_UBO_SIZE)
                            .usages(BufferUsages.create().ubo().transferDst())
                            .build()
            );
        }
        Matrix4f current = new Matrix4f(AlgorithmManager.param.currentModelViewProjectionMatrix);
        Matrix4f previous = new Matrix4f(AlgorithmManager.param.lastModelViewProjectionMatrix);
        Matrix4f inverse = new Matrix4f(current).invert();
        ByteBuffer buffer = reprojectUbo.map(true);
        inverse.get(buffer);
        buffer.position(64);
        previous.get(buffer);
        buffer.position(128);
        buffer.putFloat(colorWidth);
        buffer.putFloat(colorHeight);
        buffer.putFloat((reprojectReset || forceReset) ? 1.0f : 0.0f);
        buffer.putFloat(MCDLSSGConfig.isDLSSFrameGenerationMotionVectorDeadzoneEnabled() ? 1.0f : 0.0f);
        buffer.rewind();
        reprojectUbo.unmap();
    }

    private void ensureExtractUiPipeline() {
        if (extractUiPipeline != null) {
            return;
        }
        extractUiShader = RenderSystems.opengl().device().createShaderProgram(
                ShaderDescription.create()
                        .compute(new ShaderSource(ShaderType.Compute, "/shader/dlssg/extract_ui.comp.glsl", true))
                        .name("dlssg_extract_ui")
                        .uniformSamplerTexture("finalColor", 0)
                        .uniformSamplerTexture("hudlessColor", 1)
                        .uniformStorageTexture("outputUI", 2)
                        .build()
        );
        extractUiShader.compile();
        extractUiPipeline = ComputePipeline.builder()
                .shader(extractUiShader)
                .build(RenderSystems.opengl().device());
    }

    private void ensureReprojectPipeline() {
        if (reprojectPipeline != null) {
            return;
        }
        reprojectShader = RenderSystems.opengl().device().createShaderProgram(
                ShaderDescription.create()
                        .compute(new ShaderSource(ShaderType.Compute, "/shader/dlssg/depth_reproject_mv.comp.glsl", true))
                        .name("dlssg_depth_reproject_mv")
                        .uniformSamplerTexture("inputDepth", 0)
                        .uniformSamplerTexture("previousDepth", 1)
                        .uniformStorageTexture("outputMotionVectors", 2)
                        .uniformStorageTexture("outputDepth", 3)
                        .uniformBuffer("ReprojectData", 4, REPROJECT_UBO_SIZE)
                        .build()
        );
        reprojectShader.compile();
        reprojectPipeline = ComputePipeline.builder()
                .shader(reprojectShader)
                .build(RenderSystems.opengl().device());
    }

    private void ensureDilatePipeline() {
        if (dilatePipeline != null) {
            return;
        }
        dilateShader = RenderSystems.opengl().device().createShaderProgram(
                ShaderDescription.create()
                        .compute(new ShaderSource(ShaderType.Compute, "/shader/dlssg/mv_dilate.comp.glsl", true))
                        .name("dlssg_mv_dilate")
                        .uniformSamplerTexture("inputMotionVectors", 0)
                        .uniformSamplerTexture("inputDepth", 1)
                        .uniformStorageTexture("outputMotionVectors", 2)
                        .build()
        );
        dilateShader.compile();
        dilatePipeline = ComputePipeline.builder()
                .shader(dilateShader)
                .build(RenderSystems.opengl().device());
    }

    private static void dispatch(ComputePipeline pipeline, int width, int height) {
        ICommandBuffer commandBuffer = RenderSystems.opengl().device().defaultCommandPool().createCommandBuffer();
        try {
            commandBuffer.begin();
            commandBuffer.bindPipeline(pipeline);
            commandBuffer.dispatch((width + 15) / 16, (height + 15) / 16, 1);
            commandBuffer.end();
            RenderSystems.opengl().device().submitCommandBuffer(commandBuffer);
            commandBuffer.waitForFence();
        } finally {
            commandBuffer.destroy();
        }
    }

    private void destroyTextures() {
        if (hudlessTexture != null) {
            hudlessTexture.destroy();
            hudlessTexture = null;
        }
        if (uiTexture != null) {
            uiTexture.destroy();
            uiTexture = null;
        }
        if (fallbackMotionVectors != null) {
            fallbackMotionVectors.destroy();
            fallbackMotionVectors = null;
        }
        if (previousDepth != null) {
            previousDepth.destroy();
            previousDepth = null;
        }
        if (upscaledDepth != null) {
            upscaledDepth.destroy();
            upscaledDepth = null;
        }
        if (rawMotionVectors != null) {
            rawMotionVectors.destroy();
            rawMotionVectors = null;
        }
    }

    @Override
    public void close() {
        destroyTextures();
        if (reprojectUbo != null) {
            reprojectUbo.destroy();
            reprojectUbo = null;
        }
        if (extractUiPipeline != null) {
            extractUiPipeline.destroy();
            extractUiPipeline = null;
        }
        if (extractUiShader != null) {
            extractUiShader.destroy();
            extractUiShader = null;
        }
        if (reprojectPipeline != null) {
            reprojectPipeline.destroy();
            reprojectPipeline = null;
        }
        if (reprojectShader != null) {
            reprojectShader.destroy();
            reprojectShader = null;
        }
        if (dilatePipeline != null) {
            dilatePipeline.destroy();
            dilatePipeline = null;
        }
        if (dilateShader != null) {
            dilateShader.destroy();
            dilateShader = null;
        }
        if (upscalePipeline != null) {
            upscalePipeline.destroy();
            upscalePipeline = null;
        }
        if (upscaleShader != null) {
            upscaleShader.destroy();
            upscaleShader = null;
        }
        if (upscaleUbo != null) {
            upscaleUbo.destroy();
            upscaleUbo = null;
        }
        colorWidth = colorHeight = renderWidth = renderHeight = 0;
        reprojectReset = true;
    }
}
