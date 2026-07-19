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

package com.dgtdi.mcdlssg.common.minecraft.handler;

import com.dgtdi.mcdlssg.common.debug.imgui.ImGuiDebugContext;
import com.dgtdi.mcdlssg.common.minecraft.CallType;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.FrameBufferAttachmentType;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.IBindableFrameBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.impl.Destroyable;
import net.minecraft.client.renderer.PostChain;

public interface IMinecraftRenderHandler extends Destroyable {
    void onRenderWorldBegin(CallType type);

    void onRenderWorldEnd(CallType type);

    void onRenderHandBegin();

    void onRenderHandEnd();

    void onProcessPostChain(PostChain postChain);

    IBindableFrameBuffer getFullSizeRenderTarget();

    IBindableFrameBuffer getScaledRenderTarget();

    void initialize();

    void resize();

    default ITexture getColorTexture() {
        return getScaledRenderTarget().getTexture(FrameBufferAttachmentType.Color);
    }

    default ITexture getDepthTexture() {
        return getScaledRenderTarget().getTexture(FrameBufferAttachmentType.AnyDepth);
    }

    default ITexture getMotionVectorsTexture() {
        return null;
    }

    default void renderImGuiDebug(ImGuiDebugContext ctx) {
    }

    default void collectDebugTextures(ImGuiDebugContext ctx) {
    }
}
