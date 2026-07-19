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

package com.dgtdi.mcdlssg.api;

import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.IFrameBuffer;
import com.dgtdi.mcdlssg.core.graphics.renderdoc.RenderDoc;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;

public class MCDLSSGAPI {
    public static final IEventBus EVENT_BUS = BusBuilder.builder().build();

    public static IFrameBuffer getOriginMinecraftFrameBuffer() {
        return RenderHandlerManager.getOriginRenderTarget();
    }

    public static IFrameBuffer getMinecraftFrameBuffer() {
        return RenderHandlerManager.getRenderTarget();
    }

    public static int getScreenWidth() {
        return RenderHandlerManager.getScreenWidth();
    }

    public static int getScreenHeight() {
        return RenderHandlerManager.getScreenHeight();
    }

    public static int getRenderWidth() {
        return RenderHandlerManager.getRenderWidth();
    }

    public static int getRenderHeight() {
        return RenderHandlerManager.getRenderHeight();
    }

    public static AlgorithmDescription<?> getCurrentAlgorithmDescription() {
        return MCDLSSG.algorithmDescription;
    }

    public static AbstractAlgorithm getCurrentAlgorithm() {
        return MCDLSSG.currentAlgorithm;
    }

    public static void debugRenderdocCapture() {
        RenderHandlerManager.needCapture();
    }

    public static void debugRenderdocCaptureUpscale() {
        RenderHandlerManager.needCaptureUpscale();
    }

    public static void debugRenderdocCaptureVulkan() {
        RenderHandlerManager.needCaptureVulkan();
    }

    public static void debugRenderdocTriggerCapture() {
        if (RenderDoc.renderdoc != null) {
            RenderDoc.renderdoc.TriggerCapture.call();
        }
    }
}
