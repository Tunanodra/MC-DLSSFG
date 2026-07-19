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

package com.dgtdi.mcdlssg.core.graphics.impl.device;

import com.dgtdi.mcdlssg.core.graphics.impl.buffer.BufferDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.buffer.IBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.command.CommandPoolFlags;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandDecoder;
import com.dgtdi.mcdlssg.core.graphics.impl.command.ICommandPool;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.FramebufferDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.framebuffer.IFrameBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.ComputePipeline;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.GraphicsPipeline;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.PipelineDescriptorSet;
import com.dgtdi.mcdlssg.core.graphics.impl.pipeline.RenderPass;
import com.dgtdi.mcdlssg.core.graphics.impl.sampler.ISampler;
import com.dgtdi.mcdlssg.core.graphics.impl.sampler.SamplerDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.IShaderProgram;
import com.dgtdi.mcdlssg.core.graphics.impl.shader.ShaderDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITexture;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.ITextureView;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureViewDescription;
import com.dgtdi.mcdlssg.core.graphics.impl.vertex.IVertexBuffer;
import com.dgtdi.mcdlssg.core.graphics.impl.vertex.VertexBufferDescription;

public interface IDevice {
    /**
     * 创建一个纹理。
     *
     * @param description 纹理描述对象
     *
     * @return 新创建的纹理对象
     */
    ITexture createTexture(TextureDescription description);

    /**
     * 创建一个采样器。
     *
     * @param description 采样器描述对象
     *
     * @return 新创建的采样器对象
     */
    ISampler createSampler(SamplerDescription description);

    /**
     * 创建纹理视图。
     *
     * @param description 纹理视图描述对象
     *
     * @return 新创建的纹理视图对象
     */
    ITextureView createTextureView(TextureViewDescription description);

    /**
     * 创建帧缓冲区。
     *
     * @param description 帧缓冲区描述对象
     *
     * @return 新创建的帧缓冲区对象
     */
    IFrameBuffer createFramebuffer(FramebufferDescription description);

    /**
     * 创建一个着色器程序。
     *
     * @param description 着色器描述对象
     *
     * @return 新创建的着色器程序对象
     */
    IShaderProgram createShaderProgram(ShaderDescription description);

    /**
     * 创建顶点缓冲区
     *
     * @param description 顶点缓冲区描述对象，包含缓冲区大小和用途等参数
     *
     * @return 新创建的顶点缓冲区对象
     */
    IVertexBuffer createVertexBuffer(VertexBufferDescription description);

    /**
     * 创建缓冲区
     *
     * @param description 缓冲区描述对象，包含缓冲区大小和用途等参数
     *
     * @return 新创建的缓冲区对象
     */
    IBuffer createBuffer(BufferDescription description);

    /**
     * 创建 RenderPass
     *
     * @param builder RenderPass构建器
     *
     * @return 新创建的RenderPass对象
     */
    RenderPass createRenderPass(RenderPass.Builder builder);

    /**
     * 创建 PipelineDescriptorSet
     *
     * @param shader 着色器程序
     *
     * @return 新创建的 PipelineDescriptorSet 对象
     */
    PipelineDescriptorSet createDescriptorSet(IShaderProgram shader);

    /**
     * 创建 ComputePipeline
     *
     * @param builder ComputePipeline构建器
     *
     * @return 新创建的 ComputePipeline 对象
     */
    ComputePipeline createComputePipeline(ComputePipeline.Builder builder);

    /**
     * 创建 GraphicsPipeline
     *
     * @param builder GraphicsPipeline构建器
     *
     * @return 新创建的 GraphicsPipeline 对象
     */
    GraphicsPipeline createGraphicsPipeline(GraphicsPipeline.Builder builder);

    default ICommandBuffer createCommandBuffer() {
        return defaultCommandPool().createCommandBuffer();
    }

    ICommandPool createCommandPool(CommandPoolFlags... flags);

    ICommandPool defaultCommandPool();

    /**
     * 获取命令解码器
     */
    ICommandDecoder commandDecoder();

    /**
     * 提交命令缓冲区
     */
    void submitCommandBuffer(ICommandBuffer commandBuffer);

}