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

package com.dgtdi.mcdlssg.core.graphics.opengl;

import com.dgtdi.mcdlssg.core.graphics.impl.buffer.BufferDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.command.CommandPoolFlags;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandDecoder;
import com.dgtdi.mcdlssg.core.graphics.impl.device.IDevice;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.FramebufferDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.ComputePipeline;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.GraphicsPipeline;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.RenderPass;
import com.dgtdi.mcdlssg.core.graphics.impl.sampler.SamplerDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.IShaderProgram;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.ShaderDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.*;
import com.dgtdi.mcdlssg.core.graphics.impl.validation.ValidatedCommandDecoder;
import com.dgtdi.mcdlssg.core.graphics.impl.vertex.VertexBufferDescription;
import com.dgtdi.mcdlssg.core.graphics.opengl.buffer.GlBuffer;
import com.dgtdi.mcdlssg.core.graphics.opengl.command.GlCommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.opengl.command.GlCommandDecoder;
import com.dgtdi.mcdlssg.core.graphics.opengl.command.GlCommandPool;
import com.dgtdi.mcdlssg.core.graphics.opengl.framebuffer.GlFrameBuffer;
import com.dgtdi.mcdlssg.core.graphics.opengl.pipeline.GlComputePipeline;
import com.dgtdi.mcdlssg.core.graphics.opengl.pipeline.GlGraphicsPipeline;
import com.dgtdi.mcdlssg.core.graphics.opengl.pipeline.GlPipelineDescriptorSet;
import com.dgtdi.mcdlssg.core.graphics.opengl.pipeline.GlRenderPass;
import com.dgtdi.mcdlssg.core.graphics.opengl.shader.GlShaderProgram;
import com.dgtdi.mcdlssg.core.graphics.opengl.texture.*;
import com.dgtdi.mcdlssg.core.graphics.opengl.vertex.GlVertexBuffer;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanTexture;

import java.util.Collections;
import java.util.EnumSet;

public class GlDevice implements IDevice {
    private final GlCommandDecoder commandDecoder;
    private final GlCommandPool defaultCommandPool;
    private final ValidatedCommandDecoder validatedCommandDecoder;

    public GlDevice() {
        this.commandDecoder = new GlCommandDecoder(this);
        this.defaultCommandPool = new GlCommandPool(this, java.util.EnumSet.of(CommandPoolFlags.Reset));
        this.validatedCommandDecoder = new ValidatedCommandDecoder(this.commandDecoder);
    }

    @Override
    public ITexture createTexture(TextureDescription description) {
        if (description.getType() == TextureType.Texture2D) {
            return GlTexture2D.create(description);
        }
        if (description.getType() == TextureType.Texture1D) {
            return GlTexture1D.create(description);
        }
        return null;
    }

    @Override
    public GlSampler createSampler(SamplerDescription description) {
        return new GlSampler(description);
    }

    @Override
    public GlTextureView createTextureView(TextureViewDescription description) {
        return GlTextureView.create(description);
    }

    @Override
    public GlFrameBuffer createFramebuffer(FramebufferDescription description) {
        ITexture colorTex = description.getColorAttachment();
        ITexture depthTex = description.getDepthAttachment();

        if (colorTex == null && description.getColorFormat() != null) {
            colorTex = createTexture(TextureDescription.create()
                    .type(TextureType.Texture2D)
                    .format(description.getColorFormat())
                    .size(description.getWidth(), description.getHeight())
                    .usages(TextureUsages.create().storage().sampler().attachmentColor())
                    .build());
        }
        if (depthTex == null && description.getDepthFormat() != null) {
            depthTex = createTexture(TextureDescription.create()
                    .type(TextureType.Texture2D)
                    .format(description.getDepthFormat())
                    .size(description.getWidth(), description.getHeight())
                    .usages(TextureUsages.create().storage().sampler().attachmentDepth())
                    .build());
        }

        GlFrameBuffer fbo = GlFrameBuffer.create(
                colorTex,
                depthTex,
                description.getWidth(),
                description.getHeight()
        );
        if (description.getLabel() != null) {
            fbo.label(description.getLabel());
        }
        return fbo;
    }

    @Override
    public GlShaderProgram createShaderProgram(ShaderDescription description) {
        return new GlShaderProgram(description);
    }

    @Override
    public GlVertexBuffer createVertexBuffer(VertexBufferDescription description) {
        return GlVertexBuffer.create(description);
    }

    @Override
    public GlBuffer createBuffer(BufferDescription description) {
        return new GlBuffer(description);
    }

    @Override
    public GlRenderPass createRenderPass(RenderPass.Builder builder) {
        return new GlRenderPass(
                builder.getFrameBuffer(),
                builder.getClearState()
        );
    }

    @Override
    public GlPipelineDescriptorSet createDescriptorSet(IShaderProgram shader) {
        return new GlPipelineDescriptorSet(shader);
    }

    @Override
    public GlComputePipeline createComputePipeline(ComputePipeline.Builder builder) {
        return new GlComputePipeline(
                builder.shader(),
                createDescriptorSet(builder.shader())
        );
    }

    @Override
    public GlGraphicsPipeline createGraphicsPipeline(GraphicsPipeline.Builder builder) {
        return new GlGraphicsPipeline(
                builder.shader(),
                builder.renderPass(),
                builder.rasterization(),
                builder.depthStencil(),
                builder.colorBlend(),
                builder.dynamicStates(),
                builder.primitiveType(),
                builder.vertexFormat(),
                createDescriptorSet(builder.shader())
        );
    }

    @Override
    public GlCommandBuffer createCommandBuffer() {
        return defaultCommandPool.createCommandBuffer();
    }

    @Override
    public GlCommandPool createCommandPool(CommandPoolFlags... flags) {
        EnumSet<CommandPoolFlags> poolFlags = EnumSet.noneOf(CommandPoolFlags.class);
        if (flags != null) {
            Collections.addAll(poolFlags, flags);
        }
        return new GlCommandPool(this, poolFlags);
    }

    @Override
    public GlCommandPool defaultCommandPool() {
        return defaultCommandPool;
    }

    @Override
    public ICommandDecoder commandDecoder() {
        return validatedCommandDecoder;
    }

    @Override
    public void submitCommandBuffer(ICommandBuffer commandBuffer) {
        commandBuffer.submit(this);
    }

    public GlImportableTexture2D createTextureImportable(ITexture exportedTexture) {
        if (exportedTexture instanceof VulkanTexture) {
            return new GlImportableTexture2D((VulkanTexture) exportedTexture);
        }
        throw new IllegalArgumentException();
    }
}
