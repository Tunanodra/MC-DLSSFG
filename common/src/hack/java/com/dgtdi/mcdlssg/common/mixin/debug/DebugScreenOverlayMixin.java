/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.dgtdi.mcdlssg.common.mixin.debug;

import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.streamline.DLSSGRuntime;
import com.dgtdi.mcdlssg.core.streamline.Streamline;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void mcdlssg$addSrDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = cir.getReturnValue();
        String renderApi = RenderSystems.current() == RenderSystems.vulkan() ? "Vulkan" : "OpenGL";
        if (RenderSystems.vulkan() != null && RenderSystems.current() != RenderSystems.vulkan()) {
            renderApi += " + Vulkan Interop";
        }
        String algo = MCDLSSGConfig.isEnableUpscale()
                ? MCDLSSGConfig.UPSCALE_ALGO.get().toString()
                : "None";
        lines.add(String.format(
                "[SR] API: %s | Upscale: %s | %dx%d -> %dx%d",
                renderApi,
                algo,
                RenderHandlerManager.getRenderWidth(),
                RenderHandlerManager.getRenderHeight(),
                RenderHandlerManager.getScreenWidth(),
                RenderHandlerManager.getScreenHeight()
        ));
        String dlssGStatus;
        if (!MCDLSSGConfig.isEnableDLSSFrameGeneration()) {
            dlssGStatus = "OFF";
        } else if (!Streamline.isDLSSGAvailable()) {
            dlssGStatus = "UNAVAILABLE";
        } else if (DLSSGRuntime.isPresentingActive()) {
            dlssGStatus = String.format(
                    "ON x%d (%.0f -> %.0f FPS)",
                    DLSSGRuntime.getGeneratedFrameCount() + 1,
                    DLSSGRuntime.getRealFps(),
                    DLSSGRuntime.getDisplayedFps()
            );
        } else {
            dlssGStatus = "STANDBY";
        }
        lines.add("[SR] DLSS G: " + dlssGStatus);
        cir.setReturnValue(lines);
    }
}
