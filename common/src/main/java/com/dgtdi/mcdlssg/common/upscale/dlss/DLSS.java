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

package com.dgtdi.mcdlssg.common.upscale.dlss;

import com.dgtdi.mcdlssg.api.InitializationDescription;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.common.upscale.VulkanInteropAlgorithm;
import com.dgtdi.mcdlssg.core.RenderSystems;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanCommandBuffer;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanDevice;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanTexture;
import com.dgtdi.mcdlssg.core.ngx.NgxConstants;
import com.dgtdi.mcdlssg.core.ngx.NgxDLSSCreateParams;
import com.dgtdi.mcdlssg.core.ngx.NgxFeature;
import com.dgtdi.mcdlssg.core.ngx.NgxImageSubresourceRange;
import com.dgtdi.mcdlssg.core.ngx.NgxInitializer;
import com.dgtdi.mcdlssg.core.ngx.NgxParameters;
import com.dgtdi.mcdlssg.core.ngx.NgxResourceVK;
import com.dgtdi.mcdlssg.core.ngx.NgxVKDLSSEvalParams;
import com.dgtdi.mcdlssg.core.ngx.NgxVulkan;

public class DLSS extends VulkanInteropAlgorithm {
    private NgxFeature ngxDlssFeature;
    private NgxParameters ngxParameters;

    @Override
    protected boolean isVulkanInteropReady() {
        return ngxDlssFeature != null
                && ngxDlssFeature.isValid()
                && ngxParameters != null
                && ngxParameters.isValid();
    }

    @Override
    protected void onInteropResourcesCreated() {
        recreateNgxContext(initDesc);
    }

    @Override
    protected void onBeforeInteropResourcesDestroyed() {
        destroyNgxContext();
    }

    @Override
    protected void dispatchVulkanUpscale(
            VulkanCommandBuffer commandBuffer,
            InFlightFrameResourcesSet inFlightFrameResourcesSet
    ) {
        dispatchNgxContext(commandBuffer, inFlightFrameResourcesSet);
    }

    private void recreateNgxContext(InitializationDescription desc) {
        VulkanDevice vulkanDevice = RenderSystems.vulkan().device();
        if (!NgxInitializer.initializeIfSupported()) {
            throw new IllegalStateException("NGX is unavailable for the current GPU");
        }

        NgxParameters parameters = new NgxParameters();
        int parametersResult = NgxVulkan.getCapabilityParameters(parameters);
        requireNgxSuccess("NVSDK_NGX_VULKAN_GetCapabilityParameters", parametersResult);

        NgxFeature feature = new NgxFeature();
        VulkanCommandBuffer commandBuffer = vulkanDevice.createCommandBuffer();
        try {
            configureNgxRenderPreset(parameters);

            NgxDLSSCreateParams createParams = new NgxDLSSCreateParams();
            createParams.feature.width = RenderHandlerManager.getRenderWidth();
            createParams.feature.height = RenderHandlerManager.getRenderHeight();
            createParams.feature.targetWidth = RenderHandlerManager.getScreenWidth();
            createParams.feature.targetHeight = RenderHandlerManager.getScreenHeight();
            createParams.featureCreateFlags = createNgxFeatureFlags(desc);

            commandBuffer.begin();
            int createResult = NgxVulkan.createDLSS(
                    commandBuffer.getNativeCommandBuffer().address(),
                    1,
                    1,
                    feature,
                    parameters,
                    createParams
            );
            commandBuffer.end();
            requireNgxSuccess("NGX_VULKAN_CREATE_DLSS_EXT", createResult);

            vulkanDevice.submitCommandBuffer(commandBuffer);
            commandBuffer.waitForFence();

            ngxParameters = parameters;
            ngxDlssFeature = feature;
        } catch (RuntimeException | Error e) {
            feature.close();
            parameters.close();
            throw e;
        } finally {
            commandBuffer.destroy();
        }
    }

    private int createNgxFeatureFlags(InitializationDescription desc) {
        int flags = NgxConstants.DLSS_FLAG_MV_LOW_RES;
        if (desc.isAutoExposure()) {
            flags |= NgxConstants.DLSS_FLAG_AUTO_EXPOSURE;
        }
        if (desc.isHdrInput()) {
            flags |= NgxConstants.DLSS_FLAG_HDR;
        }
        if (desc.isMotionJittered()) {
            flags |= NgxConstants.DLSS_FLAG_MV_JITTERED;
        }
        return flags;
    }

    private void configureNgxRenderPreset(NgxParameters parameters) {
        int preset = MCDLSSGConfig.SPECIAL.DLSS.RENDER_PRESET.get().getCode();
        parameters.setInt("DLSS.Hint.Render.Preset.DLAA", preset);
        parameters.setInt("DLSS.Hint.Render.Preset.Quality", preset);
        parameters.setInt("DLSS.Hint.Render.Preset.Balanced", preset);
        parameters.setInt("DLSS.Hint.Render.Preset.Performance", preset);
        parameters.setInt("DLSS.Hint.Render.Preset.UltraPerformance", preset);
        parameters.setInt("DLSS.Hint.Render.Preset.UltraQuality", preset);
    }

    private void destroyNgxContext() {
        if (ngxDlssFeature != null) {
            int result = ngxDlssFeature.release();
            if (!NgxConstants.succeeded(result)) {
                MCDLSSG.LOGGER.error("Failed to release the DLSS NGX feature. Result: {}", result);
            }
            ngxDlssFeature = null;
        }
        if (ngxParameters != null) {
            int result = ngxParameters.destroy();
            if (!NgxConstants.succeeded(result)) {
                MCDLSSG.LOGGER.error("Failed to destroy the DLSS NGX parameters. Result: {}", result);
            }
            ngxParameters = null;
        }
    }

    private void dispatchNgxContext(
            VulkanCommandBuffer commandBuffer,
            InFlightFrameResourcesSet inFlightFrameResourcesSet
    ) {
        if (ngxDlssFeature == null || ngxParameters == null) {
            return;
        }

        try (
                NgxResourceVK color = createNgxTextureResource(inFlightFrameResourcesSet.inputColorVkTexture, true);
                NgxResourceVK depth = createNgxTextureResource(inFlightFrameResourcesSet.inputDepthVkTexture, false);
                NgxResourceVK motionVectors = createNgxTextureResource(
                        inFlightFrameResourcesSet.inputMotionVectorsVkTexture,
                        true
                );
                NgxResourceVK exposure = createNgxTextureResource(inFlightFrameResourcesSet.inputExposureVkTexture, false);
                NgxResourceVK output = createNgxTextureResource(inFlightFrameResourcesSet.outputColorVkTexture, true)
        ) {
            NgxVKDLSSEvalParams evalParams = new NgxVKDLSSEvalParams();
            evalParams.feature.inputColor = color;
            evalParams.feature.output = output;
            evalParams.feature.sharpness = MCDLSSGConfig.getSharpness();
            evalParams.depth = depth;
            evalParams.motionVectors = motionVectors;
            evalParams.exposureTexture = exposure;
            evalParams.jitterOffsetX = inFlightFrameResourcesSet.frameData.jitterOffset().x;
            evalParams.jitterOffsetY = inFlightFrameResourcesSet.frameData.jitterOffset().y;
            evalParams.renderSubrectDimensions.width = inFlightFrameResourcesSet.frameData.renderWidth();
            evalParams.renderSubrectDimensions.height = inFlightFrameResourcesSet.frameData.renderHeight();
            evalParams.motionVectorScaleX = inFlightFrameResourcesSet.frameData.renderSize().x;
            evalParams.motionVectorScaleY = inFlightFrameResourcesSet.frameData.renderSize().y;
            evalParams.reset = consumeHistoryReset() ? 1 : 0;
            evalParams.preExposure = inFlightFrameResourcesSet.frameData.preExposure();
            evalParams.exposureScale = 1.0f;
            evalParams.frameTimeDeltaInMsec = inFlightFrameResourcesSet.frameData.frameTimeDelta();

            int evaluateResult = NgxVulkan.evaluateDLSS(
                    commandBuffer.getNativeCommandBuffer().address(),
                    ngxDlssFeature,
                    ngxParameters,
                    evalParams
            );
            if (!NgxConstants.succeeded(evaluateResult)) {
                MCDLSSG.LOGGER.error("NGX DLSS evaluation failed. Result: {}", evaluateResult);
            }
        }
    }

    private NgxResourceVK createNgxTextureResource(VulkanTexture texture, boolean readWrite) {
        NgxImageSubresourceRange subresourceRange = new NgxImageSubresourceRange();
        subresourceRange.aspectMask = texture.getAspectMask();
        subresourceRange.baseMipLevel = 0;
        subresourceRange.levelCount = texture.getMipmapSettings().getLevels();
        subresourceRange.baseArrayLayer = 0;
        subresourceRange.layerCount = 1;
        return NgxVulkan.createImageViewResourceVK(
                texture.getImageView(),
                texture.handle(),
                subresourceRange,
                texture.getTextureFormat().vk(),
                texture.getWidth(),
                texture.getHeight(),
                readWrite
        );
    }

    private static void requireNgxSuccess(String operation, int result) {
        if (!NgxConstants.succeeded(result)) {
            throw new IllegalStateException(operation + " failed. NGX result: " + result);
        }
    }
}
