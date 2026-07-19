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

import com.mojang.blaze3d.platform.Window;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.debug.PerformanceInfo;
import com.dgtdi.mcdlssg.common.minecraft.B3DVulkanBridge;
import com.dgtdi.mcdlssg.common.minecraft.MinecraftUtils;
import com.dgtdi.mcdlssg.common.minecraft.MinecraftWindow;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.core.streamline.DLSSGRuntime;
import com.dgtdi.mcdlssg.common.perf.PerformanceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public abstract Window getWindow();

    @Shadow
    @Nullable
    public ClientLevel level;

    @Unique
    private int mcdlssg$cacheWidth = 0;
    @Unique
    private int mcdlssg$cacheHeight = 0;
    @Unique
    private boolean mcdlssg$b3dVulkanFrame = false;

    #if MC_VER <= MC_1_20_1
    @Inject(at = @At(value = "TAIL"), method = "<init>")
    #elif MC_VER > MC_26_1_2
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", unsafe = true), method = "<init>")
    #else
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), method = "<init>")
    #endif
    private void onClientStarted(net.minecraft.client.main.GameConfig data, CallbackInfo ci) {
        MCDLSSG.onClientStarted();
    }

    /*
    @Inject(at = @At(value = "TAIL"), method = "doWorldLoad")
    private void onJoinLevel(CallbackInfo ci) {
        MCDLSSG.onJoinLevel();
    }

    @Inject(at = @At(value = "TAIL"), method = "setLevel")
    private void onLevelChanged(CallbackInfo ci) {
        MCDLSSG.onLevelChanged();
    }
    */
    @Inject(at = @At(value = "RETURN"), method = "onGameLoadFinished")
    private void onLoadDone(CallbackInfo ci) {
        MCDLSSG.gameIsLoaded = true;
        MCDLSSG.onGameLoadFinished();
    }

    @Inject(at = @At(value = "HEAD"), method = "runTick")
    private void onRenderBegin(CallbackInfo ci) {
        DLSSGRuntime.beginFrame();
        if (B3DVulkanBridge.isB3DVulkanBackend()) {
            mcdlssg$b3dVulkanFrame = true;
            PerformanceTracker.push("Frame");
            RenderHandlerManager.onFrameBegin();
            return;
        }
        if (mcdlssg$cacheWidth != RenderHandlerManager.getScreenWidth() || mcdlssg$cacheHeight != RenderHandlerManager.getScreenHeight()) {
            mcdlssg$cacheWidth = RenderHandlerManager.getScreenWidth();
            mcdlssg$cacheHeight = RenderHandlerManager.getScreenHeight();
            MinecraftUtils.resize();
        }
        // 窗口 resize 去抖——尺寸稳定后触发一次算法重建。
        MCDLSSG.tickResize();
        GL11.glViewport(0, 0, RenderHandlerManager.getScreenWidth(), RenderHandlerManager.getScreenHeight());
        PerformanceTracker.push("Frame");
        RenderHandlerManager.onFrameBegin();
    }

    @Inject(at = @At(value = "RETURN"), method = "runTick")
    private void onRenderEnd(CallbackInfo ci) {
        DLSSGRuntime.simulationEnd();
        mcdlssg$hookFpsString();
        if (mcdlssg$b3dVulkanFrame) {
            mcdlssg$b3dVulkanFrame = false;
            RenderHandlerManager.onFrameEnd();
            PerformanceTracker.pop("Frame");
            MCDLSSG.onClientTickEnd();
            return;
        }
        RenderHandlerManager.onFrameEnd();
        PerformanceTracker.pop("Frame");
        MCDLSSG.onClientTickEnd();
    }

    @Unique
    private void mcdlssg$hookFpsString() {
        if (!DLSSGRuntime.isPresentingActive()) {
            return;
        }
        double realFps = DLSSGRuntime.getRealFps();
        int multiplier = DLSSGRuntime.getGeneratedFrameCount() + 1;
        double displayedFps = DLSSGRuntime.getDisplayedFps();
        double latencyMs = realFps > 1.0 ? 1000.0 / realFps : 0.0;
        ((Minecraft) (Object) this).fpsString = String.format(
                java.util.Locale.ROOT,
                "%.0f(%.0f x%d)+%.1fms",
                displayedFps,
                realFps,
                multiplier,
                latencyMs
        );
    }

    #if MC_VER <= MC_1_21_11
    @Inject(method = "resizeDisplay", at = @At(value = "HEAD"), cancellable = true)
    private void onResize(CallbackInfo ci) {
        if (
                MinecraftWindow.getWindowSourceWidth() <= 1 ||
                        MinecraftWindow.getWindowSourceHeight() <= 1
        ) {
            ci.cancel();
        }
    }
    #else
    @Inject(method = "resizeGui", at = @At(value = "HEAD"), cancellable = true)
    private void onResize(CallbackInfo ci) {
        if (
                MinecraftWindow.getWindowSourceWidth() <= 1 ||
                        MinecraftWindow.getWindowSourceHeight() <= 1
        ) {
            ci.cancel();
        }
    }
    #endif
    #if MC_VER > MC_26_1_2
    //just like fabric`s invoke point
    @Inject(method = "stop",at = @At(value = "TAIL"))
    public void onDestroy(CallbackInfo ci) {
        MCDLSSG.onClientStopping();
    }
    #else
    //just like fabric`s invoke point
    @Inject(method = "destroy",at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V"))
    public void onDestroy(CallbackInfo ci) {
        MCDLSSG.onClientStopping();
    }
    #endif

}
