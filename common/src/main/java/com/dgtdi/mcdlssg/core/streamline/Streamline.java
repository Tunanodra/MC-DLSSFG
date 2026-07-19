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

package com.dgtdi.mcdlssg.core.streamline;

import com.dgtdi.mcdlssg.api.platform.OperatingSystemType;
import com.dgtdi.mcdlssg.api.platform.Platform;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.core.NativeLibManager;
import com.dgtdi.mcdlssg.core.MCDLSSGConstants;
import org.lwjgl.system.Configuration;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.nio.file.Path;

public final class Streamline {
    private static boolean initAttempted;
    private static StreamlineSession defaultSession;
    private static volatile boolean dlssGAvailable = true;

    private Streamline() {
    }

    public static boolean isSupportedPlatform() {
        return Platform.currentPlatform.getOS().type == OperatingSystemType.WINDOWS;
    }

    public static boolean isNativeAvailable() {
        return NativeLibManager.LIB_SUPER_RESOLUTION_STREAMLINE != null
                && NativeLibManager.LIB_SUPER_RESOLUTION_STREAMLINE.available;
    }

    public static boolean isInitialized() {
        return defaultSession != null && !defaultSession.isClosed();
    }

    public static boolean isSupportedOnCurrentVersion() {
        return true;
    }

    public static synchronized boolean prepareEarly() {
        Path nativeDir = MCDLSSGConstants.NATIVE_LIBRARIES_DIR.getPath().toAbsolutePath();
        return prepareEarly(StreamlineInitConfig.defaultConfig(
                nativeDir,
                MCDLSSGConstants.ERROR_DIR.getPath().toAbsolutePath()
        ));
    }

    public static synchronized boolean prepareEarly(StreamlineInitConfig config) {
        if (!isSupportedOnCurrentVersion() || !isSupportedPlatform()
                || !MCDLSSGConfig.isEnableDLSSFrameGeneration()) {
            return false;
        }
        if (NativeLibManager.LIB_STREAMLINE_INTERPOSER == null
                || NativeLibManager.LIB_SUPER_RESOLUTION_STREAMLINE == null) {
            return false;
        }

        Path nativeDir = MCDLSSGConstants.NATIVE_LIBRARIES_DIR.getPath();
        NativeLibManager.extract(nativeDir);
        Path mainLib = NativeLibManager.LIB_SUPER_RESOLUTION.getTargetPath(nativeDir).toAbsolutePath();
        Path interposer = NativeLibManager.LIB_STREAMLINE_INTERPOSER.getTargetPath(nativeDir).toAbsolutePath();
        Path common = NativeLibManager.LIB_STREAMLINE_COMMON.getTargetPath(nativeDir).toAbsolutePath();
        Path nativeBridge = NativeLibManager.LIB_SUPER_RESOLUTION_STREAMLINE.getTargetPath(nativeDir).toAbsolutePath();
        if (!interposer.toFile().isFile() || !common.toFile().isFile() || !nativeBridge.toFile().isFile()) {
            MCDLSSG.LOGGER.warn("DLSS Frame Generation runtime libraries are missing. Open the MCDLSSG config screen and enable DLSS Frame Generation to download them automatically, then restart the game.");
            return false;
        }

        Configuration.VULKAN_LIBRARY_NAME.set(interposer.toString());
        if (mainLib.toFile().isFile()) {
            System.load(mainLib.toString());
        }
        System.load(common.toString());
        System.load(interposer.toString());
        System.load(nativeBridge.toString());
        NativeLibManager.LIB_SUPER_RESOLUTION_STREAMLINE.available = true;
        return initEarly(config);
    }

    public static synchronized boolean initEarly() {
        Path nativeDir = MCDLSSGConstants.NATIVE_LIBRARIES_DIR.getPath().toAbsolutePath();
        return initEarly(StreamlineInitConfig.defaultConfig(
                nativeDir,
                MCDLSSGConstants.DATA_DIR.getPath().toAbsolutePath()
        ));
    }

    public static synchronized boolean initEarly(StreamlineInitConfig config) {
        if (!isSupportedOnCurrentVersion() || !isSupportedPlatform() || !isNativeAvailable()) {
            return false;
        }
        if (defaultSession != null && defaultSession.isClosed()) {
            defaultSession.close();
            defaultSession = null;
            initAttempted = false;
        }
        if (isInitialized()) {
            return true;
        }
        if (initAttempted) {
            return false;
        }
        initAttempted = true;
        try {
            defaultSession = open(config);
        } catch (StreamlineException exception) {
            MCDLSSG.LOGGER.error("Streamline init failed. result={} ({})",
                    StreamlineResult.nameOf(exception.result()), exception.result());
            return false;
        } catch (RuntimeException exception) {
            MCDLSSG.LOGGER.error("Streamline init failed.", exception);
            return false;
        }
        MCDLSSG.LOGGER.info("Streamline initialized.");
        return true;
    }

    public static synchronized void shutdown() {
        if (defaultSession == null) {
            return;
        }
        defaultSession.close();
        defaultSession = null;
        initAttempted = false;
    }

    public static StreamlineSession open(StreamlineInitConfig config) {
        if (!isSupportedPlatform()) {
            throw new UnsupportedOperationException("Streamline is only available on Windows Vulkan");
        }
        if (!isNativeAvailable()) {
            throw new IllegalStateException("Streamline native library is not loaded");
        }
        return StreamlineSession.open(config);
    }

    public static long createVkInstance(long createInfoAddress) {
        return StreamlineNative.nCreateVkInstance(createInfoAddress);
    }

    public static long createVkDevice(long instanceAddress, long physicalDeviceAddress, long createInfoAddress) {
        return StreamlineNative.nCreateVkDevice(instanceAddress, physicalDeviceAddress, createInfoAddress);
    }

    public static int setVulkanInfo(long instanceAddress, long physicalDeviceAddress, long deviceAddress, int graphicsQueueFamilyIndex) {
        return setVulkanInfo(
                instanceAddress,
                physicalDeviceAddress,
                deviceAddress,
                graphicsQueueFamilyIndex,
                graphicsQueueFamilyIndex,
                graphicsQueueFamilyIndex
        );
    }

    public static int setVulkanInfo(
            long instanceAddress,
            long physicalDeviceAddress,
            long deviceAddress,
            int graphicsQueueFamilyIndex,
            int computeQueueFamilyIndex,
            int opticalFlowQueueFamilyIndex
    ) {
        if (!isInitialized()) {
            return -1;
        }
        StreamlineTypes.VulkanInfo info = new StreamlineTypes.VulkanInfo();
        info.instance = instanceAddress;
        info.physicalDevice = physicalDeviceAddress;
        info.device = deviceAddress;
        info.computeQueueFamily = computeQueueFamilyIndex;
        info.graphicsQueueFamily = graphicsQueueFamilyIndex;
        info.opticalFlowQueueFamily = opticalFlowQueueFamilyIndex;
        int result = defaultSession.setVulkanInfo(info);
        if (result != 0 && result != 19) {
            MCDLSSG.LOGGER.error("slSetVulkanInfo failed. result={} ({})", StreamlineResult.nameOf(result), result);
        }
        return result;
    }

    public static int getNewFrameToken(int frameIndex, StreamlineTypes.FrameToken outToken) {
        if (!isInitialized()) {
            return -1;
        }
        return defaultSession.getNewFrameToken(frameIndex, outToken);
    }

    public static int setConstants(
            StreamlineTypes.Constants constants,
            StreamlineTypes.FrameToken frame,
            StreamlineTypes.Viewport viewport
    ) {
        if (!isInitialized()) {
            return -1;
        }
        return defaultSession.setConstants(constants, frame, viewport);
    }

    public static int getLastVkResult() {
        return StreamlineNative.nGetLastVkResult();
    }

    public static boolean isFeatureSupported(int feature, VkPhysicalDevice physicalDevice) {
        if (!isInitialized() || physicalDevice == null) {
            return false;
        }
        boolean[] supported = new boolean[1];
        return defaultSession.isFeatureSupported(feature, physicalDevice.address(), supported) == 0 && supported[0];
    }

    public static boolean isDLSSGSupported() {
        return dlssGAvailable && isDLSSGSupported(null);
    }

    public static boolean isDLSSGAvailable() {
        return dlssGAvailable;
    }

    public static void markDLSSGUnavailable(String reason) {
        if (dlssGAvailable) {
            dlssGAvailable = false;
            MCDLSSG.LOGGER.warn("DLSS Frame Generation is unavailable: {}", reason);
        }
    }

    public static boolean isDLSSGSupported(VkPhysicalDevice physicalDevice) {
        if (!isInitialized()) {
            return false;
        }
        if (physicalDevice != null && !isFeatureSupported(StreamlineFeature.DLSS_G, physicalDevice)) {
            return false;
        }
        StreamlineTypes.FeatureRequirements requirements = new StreamlineTypes.FeatureRequirements();
        return defaultSession.getFeatureRequirements(StreamlineFeature.DLSS_G, requirements) == 0;
    }

    public static StreamlineTypes.FeatureRequirements getDLSSGRequirements() {
        if (!isInitialized()) {
            return null;
        }
        StreamlineTypes.FeatureRequirements requirements = new StreamlineTypes.FeatureRequirements();
        return defaultSession.getFeatureRequirements(StreamlineFeature.DLSS_G, requirements) == 0
                ? requirements
                : null;
    }

    public static StreamlineSession session() {
        if (!isInitialized()) {
            throw new IllegalStateException("Streamline is not initialized");
        }
        return defaultSession;
    }

    public static int setDLSSGOptions(boolean enabled, int framesToGenerate) {
        if (!isInitialized()) {
            return -1;
        }
        StreamlineTypes.DlssGOptions options = new StreamlineTypes.DlssGOptions();
        options.mode = enabled ? StreamlineTypes.DlssGMode.ON : StreamlineTypes.DlssGMode.OFF;
        options.numFramesToGenerate = Math.max(1, framesToGenerate);
        return defaultSession.dlssGSetOptions(new StreamlineTypes.Viewport(0), options);
    }

    public static StreamlineDLSSGState getDLSSGState() {
        if (!isInitialized()) {
            return null;
        }
        StreamlineTypes.DlssGState nativeState = new StreamlineTypes.DlssGState();
        int result = defaultSession.dlssGGetState(new StreamlineTypes.Viewport(0), nativeState, null);
        if (result != 0) {
            return null;
        }
        StreamlineDLSSGState state = new StreamlineDLSSGState();
        state.estimatedVramUsage = nativeState.estimatedVramUsage;
        state.status = nativeState.status;
        state.minWidthOrHeight = nativeState.minWidthOrHeight;
        state.numFramesActuallyPresented = nativeState.numFramesActuallyPresented;
        state.numFramesToGenerateMax = nativeState.numFramesToGenerateMax;
        state.vsyncSupportAvailable = nativeState.vsyncSupportAvailable != 0;
        state.dynamicMfgSupported = nativeState.dynamicMfgSupported != 0;
        return state;
    }
}
