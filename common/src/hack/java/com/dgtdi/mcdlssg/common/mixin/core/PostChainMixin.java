/*
 * Super Resolution
 * Copyright (c) 2025-2026. 187J3X1-114514
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

package com.dgtdi.mcdlssg.common.mixin.core;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.common.mixin.core.accessor.PostChainAccessor;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
#if MC_VER > MC_1_21_10
import net.minecraft.resources.Identifier;
#else
import net.minecraft.resources.ResourceLocation;
#endif
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(PostChain.class)
public abstract class PostChainMixin {
    #if MC_VER < MC_1_21_4
    @Unique
    private List<String> mcdlssg$blackList = null;
    @Shadow
    @Final
    private List<PostPass> passes;
    @Shadow
    @Final
    private Map<String, RenderTarget> customRenderTargets;
    @Shadow
    @Final
    private RenderTarget screenTarget;
    @Shadow
    @Final
    private String name;

    #if MC_VER >= MC_1_20_6
    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void onInitPostChain(
            TextureManager textureManager,
            net.minecraft.server.packs.resources.ResourceProvider resourceProvider,
            RenderTarget screenTarget,
            #if MC_VER > MC_1_21_10
            Identifier resourceLocation,
            #else
            ResourceLocation resourceLocation,
            #endif
            CallbackInfo ci
    ) throws IOException, JsonSyntaxException {
        if (mcdlssg$onBlackList()) {
            return;
        }
        if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
            return;
        }

        if (!screenTarget.equals(RenderHandlerManager.getOriginRenderTarget().asMcRenderTarget())) {
            return;
        }
        this.passes.forEach(PostPass::close);
        this.passes.clear();
        this.customRenderTargets.values().forEach(RenderTarget::destroyBuffers);
        this.customRenderTargets.clear();
        ((PostChainAccessor) this).setScreenTarget(RenderHandlerManager.getRenderTarget().asMcRenderTarget());
        this.updateOrthoMatrix();
        this.load(textureManager, resourceLocation);
        MCDLSSG.LOGGER.info("已注入PostChain {}", this.name);
    }

    #else
    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void onInitPostChain(
            TextureManager textureManager,
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            RenderTarget screenTarget,
            ResourceLocation name,
            CallbackInfo ci
    ) throws IOException, JsonSyntaxException {
        if (mcdlssg$onBlackList()) {
            return;
        }
        if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
            return;
        }

        if (!screenTarget.equals(RenderHandlerManager.getOriginRenderTarget().asMcRenderTarget())) {
            return;
        }
        this.passes.forEach(PostPass::close);
        this.passes.clear();
        this.customRenderTargets.values().forEach(RenderTarget::destroyBuffers);
        this.customRenderTargets.clear();
        ((PostChainAccessor) this).setScreenTarget(RenderHandlerManager.getRenderTarget().asMcRenderTarget());
        this.updateOrthoMatrix();
        this.load(textureManager, name);
        MCDLSSG.LOGGER.info("已注入PostChain {}", this.name);
    }
    #endif

    @Shadow
    public abstract void resize(int width, int height);

    @Shadow
    protected abstract void updateOrthoMatrix();

    @Shadow
    protected abstract void load(TextureManager textureManager, ResourceLocation resourceLocation) throws IOException, JsonSyntaxException;

    @Inject(method = "resize", at = @At("HEAD"), cancellable = true)
    public void onResize(int width, int height, CallbackInfo ci) {
        if (mcdlssg$onBlackList()) {
            return;
        }
        if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
            return;
        }
        if (
                width != RenderHandlerManager.getRenderWidth() ||
                        height != RenderHandlerManager.getRenderHeight()
        ) {
            this.resize(RenderHandlerManager.getRenderWidth(), RenderHandlerManager.getRenderHeight());
            ci.cancel();
        }
    }

    @Inject(method = "process", at = @At("HEAD"))
    public void onProcess(float partialTicks, CallbackInfo ci) {
        if (mcdlssg$onBlackList()) {
            return;
        }
        if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
            ((PostChainAccessor) this).setScreenTarget(RenderHandlerManager.getOriginRenderTarget().asMcRenderTarget());
            return;
        }
        RenderHandlerManager.onProcessPostChain((PostChain) (Object) this);
    }

    @Unique
    private boolean mcdlssg$onBlackList() {
        if (mcdlssg$blackList == null) {
            mcdlssg$blackList = new ArrayList<>();
            mcdlssg$blackList.add("minecraft:shaders/post/fancymenu_gui_blur.json");
            mcdlssg$blackList.add("minecraft:shaders/post/fancymenu_gui_smooth_circle.json");
            mcdlssg$blackList.add("minecraft:shaders/post/fancymenu_gui_smooth_image_circle.json");
            mcdlssg$blackList.add("minecraft:shaders/post/fancymenu_gui_smooth_image_rect.json");
            mcdlssg$blackList.add("minecraft:shaders/post/fancymenu_gui_smooth_rect.json");
            mcdlssg$blackList.add("minecraft:shaders/post/modern_gaussian_blur.json");
            mcdlssg$blackList.add("minecraft:shaders/post/blur.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/achromatomaly.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/achromatopsia.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/deuteranomaly.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/deuteranopia.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/protanomaly.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/protanopia.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/tritanomaly.json");
            mcdlssg$blackList.add("colorblindness:shaders/post/tritanopia.json");

            mcdlssg$blackList.addAll(MCDLSSGConfig.getInjectPostChainBlackList());
        }

        return mcdlssg$blackList.contains(name);
    }
    #endif
}
