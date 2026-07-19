package com.dgtdi.mcdlssg.common.workmode;

import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.debug.imgui.ImGuiDebugContext;
import com.dgtdi.mcdlssg.common.minecraft.handler.IMinecraftRenderHandler;
import com.dgtdi.mcdlssg.common.minecraft.handler.MinecraftRenderHandler;

public class HackSRWorkModeProvider implements SRWorkModeProvider {
    @Override
    public String id() {
        return SRWorkModeManager.HACK;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public IMinecraftRenderHandler createRenderHandler() {
        return new MinecraftRenderHandler();
    }

    @Override
    public SRWorkModeState getState() {
        return SRWorkModeState.defaults();
    }

    @Override
    public void renderImGuiDebug(ImGuiDebugContext ctx) {
        ctx.property("Upscale Enabled", MCDLSSGConfig.isEnableUpscale());
        ctx.property("Upscale Enabled (Original)", MCDLSSGConfig.isEnableUpscaleOriginal());
        ctx.property("Capture Mode", MCDLSSGConfig.getCaptureMode());
        ctx.property("Scale Factor", MCDLSSGConfig.getRenderScaleFactor());
    }
}
