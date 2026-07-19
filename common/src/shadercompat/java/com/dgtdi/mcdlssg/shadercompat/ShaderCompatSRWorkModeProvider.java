package com.dgtdi.mcdlssg.shadercompat;

import com.dgtdi.mcdlssg.api.InitializationDescription;
import com.dgtdi.mcdlssg.api.platform.Platform;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.debug.imgui.ImGuiDebugContext;
import com.dgtdi.mcdlssg.common.minecraft.handler.IMinecraftRenderHandler;
import com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat.SRShaderCompatData;
import com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat.ShaderCompatHandler;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeProvider;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeState;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureFormat;

import java.util.Optional;

public class ShaderCompatSRWorkModeProvider implements SRWorkModeProvider {
    private boolean listenersRegistered;

    @Override
    public String id() {
        return SRWorkModeManager.SHADER_COMPAT;
    }

    @Override
    public boolean isActive() {
        try {
            return IrisShaderCompatUtils.shouldApplyMCDLSSGChanges();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public IMinecraftRenderHandler createRenderHandler() {
        return new ShaderCompatHandler();
    }

    @Override
    public SRWorkModeState getState() {
        Optional<SRShaderCompatData.WorldProfile> profile;
        try {
            profile = IrisShaderCompatUtils.getCurrentConfig();
        } catch (Throwable ignored) {
            profile = Optional.empty();
        }
        InitializationDescription desc = InitializationDescription.defaults();
        TextureFormat internalFormat = TextureFormat.RGBA16F;
        String motionVectorPreprocessingFunction = null;

        if (profile.isPresent() && profile.get().enabled) {
            SRShaderCompatData.UpscaleConfig upscale = profile.get().upscale;
            desc.setHdrInput(upscale.isHdrInput)
                    .setAutoExposure(upscale.isAutoExposure)
                    .setMotionJittered(upscale.isMotionJittered);
            internalFormat = upscale.internalFormat;
            if (upscale.customs != null) {
                motionVectorPreprocessingFunction = upscale.customs.motionVectorPreprocessingFunction;
            }
        }

        return new SRWorkModeState(
                desc,
                internalFormat,
                motionVectorPreprocessingFunction,
                ShaderCompatHandler.irisApiIsShaderPackInUse() || ShaderCompatHandler.irisHasShaderPack(),
                ShaderCompatHandler.isLoadingShader()
        );
    }

    @Override
    public void onClientSetup() {
        if (listenersRegistered || !Platform.currentPlatform.isInstallIris()) {
            return;
        }
        IrisShaderCompatEventHandler.registerEventListeners();
        listenersRegistered = true;
    }

    @Override
    public void reloadShaderPack() {
        ShaderCompatHandler.irisApiReloadShader();
    }

    @Override
    public void renderImGuiDebug(ImGuiDebugContext ctx) {
        SRWorkModeState state = getState();
        ctx.property("Shader Pack In Use", state.shaderPackInUse());
        ctx.property("Shader Pack Loading", state.shaderPackLoading());
        ctx.property("Force Disable Shader Compat", MCDLSSGConfig.isForceDisableShaderCompat());
        ctx.property("Internal Format", state.internalTextureFormat());
        ctx.property("Motion Vector Preprocess", state.motionVectorPreprocessingFunction());

        Optional<SRShaderCompatData.WorldProfile> profile = IrisShaderCompatUtils.getCurrentConfig();
        ctx.property("World Profile Loaded", profile.isPresent());
        if (profile.isPresent()) {
            SRShaderCompatData.UpscaleConfig upscale = profile.get().upscale;
            ctx.property("Profile Enabled", profile.get().enabled);
            ctx.property("Upscale Enabled", upscale.enabled);
            ctx.property("Trigger Pass", upscale.trigger.passName);
            ctx.property("Trigger Order", upscale.trigger.order);
            ctx.property("HDR Input", upscale.isHdrInput);
            ctx.property("Auto Exposure", upscale.isAutoExposure);
            ctx.property("Motion Jittered", upscale.isMotionJittered);
        }
    }
}
