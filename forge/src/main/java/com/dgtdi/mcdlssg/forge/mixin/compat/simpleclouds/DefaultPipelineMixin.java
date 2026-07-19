/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dgtdi.mcdlssg.forge.mixin.compat.simpleclouds;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.DefaultPipeline;
import com.dgtdi.mcdlssg.api.MCDLSSGAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DefaultPipeline.class, remap = false)
public class DefaultPipelineMixin {
    @Redirect(method = "afterSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V", ordinal = 0))
    public void blitToScreenA(RenderTarget instance, int width, int height, boolean disableBlend) {
    }

    @Redirect(method = "afterSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V", ordinal = 1))
    public void blitToScreenB(RenderTarget instance, int width, int height, boolean disableBlend) {
        instance.blitToScreen(
                Math.max(MCDLSSGAPI.getRenderWidth(), MCDLSSGAPI.getScreenWidth()),
                Math.max(MCDLSSGAPI.getRenderHeight(), MCDLSSGAPI.getScreenHeight()),
                disableBlend
        );
        GlStateManager._viewport(
                0,
                0,
                MCDLSSGAPI.getRenderWidth(),
                MCDLSSGAPI.getRenderHeight()
        );
    }

    @Redirect(method = "afterSky", at = @At(value = "INVOKE", target = "Ldev/nonamecrackers2/simpleclouds/client/framebuffer/FrameBufferUtils;blitTargetPreservingAlpha(Lcom/mojang/blaze3d/pipeline/RenderTarget;II)V"))
    public void blitToScreenBC(RenderTarget target, int width, int height) {
    }
}
