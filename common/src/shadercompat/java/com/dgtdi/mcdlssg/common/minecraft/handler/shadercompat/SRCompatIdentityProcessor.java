package com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat;

import com.dgtdi.mcdlssg.api.AbstractAlgorithm;
import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import org.joml.Vector2f;

public class SRCompatIdentityProcessor implements SRCompatProcessor {
    @Override
    public int version() {
        return -1;
    }

    @Override
    public boolean needsPreProcessColor(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public boolean needsPreProcessDepth(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public boolean needsPreProcessMotionVectors(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public boolean needsPreProcessExposure(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public boolean needsAdaptJitter(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public boolean needsAdaptPreExposure(SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
        return false;
    }

    @Override
    public Vector2f adaptJitterForAlgorithm(Vector2f rawJitter, AbstractAlgorithm algorithm, SRShaderCompatData config, AlgorithmDescription<?> description) {
        return rawJitter;
    }

    @Override
    public Vector2f adaptJitterForShaderpack(Vector2f rawJitter, AbstractAlgorithm algorithm, SRShaderCompatData config, AlgorithmDescription<?> description) {
        return rawJitter;
    }

    @Override
    public void preProcessColor(ITexture input, ITexture output, ICommandBuffer commandBuffer,
                                SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }

    @Override
    public void preProcessDepth(ITexture input, ITexture output, ICommandBuffer commandBuffer,
                                SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }

    @Override
    public void preProcessMotionVectors(ITexture input, ITexture output, ICommandBuffer commandBuffer,
                                        SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }

    @Override
    public void preProcessExposure(ITexture input, ITexture output, ICommandBuffer commandBuffer,
                                    SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }

    @Override
    public float adaptPreExposureForAlgorithm(float rawExposure, AbstractAlgorithm algorithm, SRShaderCompatData config, AlgorithmDescription<?> description) {
        return rawExposure;
    }

    @Override
    public void registerMacros(MacroRegistrar registrar, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }

    @Override
    public void registerUniforms(UniformRegistrar registrar, SRShaderCompatData config, AbstractAlgorithm algorithm, AlgorithmDescription<?> description) {
    }
}
