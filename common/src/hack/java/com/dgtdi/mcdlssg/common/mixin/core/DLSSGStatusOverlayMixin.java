/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.dgtdi.mcdlssg.common.mixin.core;

import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.core.streamline.DLSSGRuntime;
import com.dgtdi.mcdlssg.core.streamline.Streamline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class DLSSGStatusOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void mcdlssg$renderDlssGIndicator(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (!MCDLSSGConfig.isEnableDLSSFrameGeneration()
                || !MCDLSSGConfig.isDLSSFrameGenerationIndicatorVisible()
                || !DLSSGRuntime.isIndicatorActive()
                || Minecraft.getInstance().options.hideGui) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();

        boolean active = DLSSGRuntime.isPresentingActive();
        boolean available = Streamline.isDLSSGAvailable();
        int multiplier = DLSSGRuntime.getGeneratedFrameCount() + 1;
        String modeText;
        int modeColor;
        if (!available) {
            modeText = "DLSS G: UNAVAILABLE";
            modeColor = 0xFFFF5555;
        } else if (active) {
            modeText = "DLSS G: ON x" + multiplier;
            modeColor = 0xFF55FF55;
        } else {
            modeText = "DLSS G: STANDBY";
            modeColor = 0xFFFFFF55;
        }

        String fpsText;
        if (active && DLSSGRuntime.getDisplayedFps() > 0.5) {
            fpsText = String.format("FPS: %.0f -> %.0f", DLSSGRuntime.getRealFps(), DLSSGRuntime.getDisplayedFps());
        } else {
            fpsText = String.format("FPS: %.0f", DLSSGRuntime.getRealFps());
        }

        int y = 6;
        guiGraphics.drawString(font, modeText, screenWidth - font.width(modeText) - 8, y, modeColor, true);
        y += font.lineHeight + 2;
        guiGraphics.drawString(font, fpsText, screenWidth - font.width(fpsText) - 8, y, 0xFFFFFFFF, true);
    }
}
