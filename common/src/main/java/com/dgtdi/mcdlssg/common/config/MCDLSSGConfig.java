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

package com.dgtdi.mcdlssg.common.config;

import com.mojang.blaze3d.systems.RenderSystem;
import com.dgtdi.mcdlssg.api.AbstractAlgorithm;
import com.dgtdi.mcdlssg.api.config.ModConfigSpec;
import com.dgtdi.mcdlssg.api.config.ModConfigSpecBuilder;
import com.dgtdi.mcdlssg.api.config.values.list.StringListValue;
import com.dgtdi.mcdlssg.api.config.values.single.BooleanValue;
import com.dgtdi.mcdlssg.api.config.values.single.EnumValue;
import com.dgtdi.mcdlssg.api.config.values.single.FloatValue;
import com.dgtdi.mcdlssg.api.config.values.single.StringValue;
import com.dgtdi.mcdlssg.api.platform.OperatingSystem;
import com.dgtdi.mcdlssg.api.platform.OperatingSystemType;
import com.dgtdi.mcdlssg.api.platform.Platform;
import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.api.registry.AlgorithmRegistry;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.enums.CaptureMode;
import com.dgtdi.mcdlssg.common.config.enums.DLSSGMotionVectorMode;
import com.dgtdi.mcdlssg.common.config.enums.InternalTextureFormat;
import com.dgtdi.mcdlssg.common.config.enums.InteropSyncMode;
import com.dgtdi.mcdlssg.common.config.special.SpecialConfigs;
import com.dgtdi.mcdlssg.common.minecraft.B3DVulkanBridge;
import com.dgtdi.mcdlssg.common.minecraft.handler.RenderHandlerManager;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;
import com.dgtdi.mcdlssg.common.workmode.SRWorkModeState;
import com.dgtdi.mcdlssg.core.MCDLSSGConstants;
import com.dgtdi.mcdlssg.core.graphics.GpuVendor;
import com.dgtdi.mcdlssg.core.graphics.GraphicsCapabilities;
import com.dgtdi.mcdlssg.core.graphics.impl.texture.TextureFormat;
import com.dgtdi.mcdlssg.core.graphics.opengl.GlDebug;
import com.dgtdi.mcdlssg.core.graphics.vulkan.VulkanDebug;
import com.dgtdi.mcdlssg.core.gui.MaterialTheme;
import com.dgtdi.mcdlssg.core.gui.SchemeVariant;
import com.dgtdi.mcdlssg.core.utils.Color;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MCDLSSGConfig {
    public static final ModConfigSpec SPEC;
    public static final SpecialConfigs SPECIAL;
    public static final BooleanValue ENABLE_UPSCALE;
    public static final FloatValue UPSCALE_RATIO;
    public static final StringValue UPSCALE_ALGO;
    public static final FloatValue SHARPNESS;
    public static final EnumValue<CaptureMode> CAPTURE_MODE;
    public static final BooleanValue DEBUG_DUMP_SHADER;
    public static final BooleanValue SKIP_INIT_VULKAN;
    public static final BooleanValue ENABLE_RENDER_DOC;
    public static final BooleanValue ENABLE_IMGUI;
    public static final BooleanValue GENERATE_MOTION_VECTORS;
    public static final BooleanValue PAUSE_GAME_ON_GUI;
    public static final StringListValue INJECT_POST_CHAIN_BLACKLIST;
    public static final BooleanValue ENABLE_COMPAT_SHADER_COMPILER;
    public static final BooleanValue ENABLE_DETAILED_PROFILING;
    public static final BooleanValue ENABLE_DEBUG;
    public static final BooleanValue DISABLE_UPSCALE_ON_VANILLA;
    public static final BooleanValue FORCE_DISABLE_SHADER_COMPAT;
    public static final EnumValue<InternalTextureFormat> INTERNAL_TEXTURE_FORMAT;
    public static final EnumValue<MaterialTheme> THEME;
    public static final EnumValue<SchemeVariant> THEME_SCHEME_VARIANT;
    public static final FloatValue THEME_CONTRAST_LEVEL;
    public static final StringValue THEME_COLOR;

    public static final EnumValue<InteropSyncMode> INTEROP_SYNC_MODE;
    public static final BooleanValue ENABLE_EXPERIMENTAL_FEATURES;
    public static final BooleanValue ENABLE_DLSS_FRAME_GENERATION;
    public static final FloatValue DLSS_FRAME_GENERATION_MULTIPLIER;
    public static final BooleanValue DLSS_FRAME_GENERATION_UI_RECOMPOSITION;
    public static final BooleanValue DLSS_FRAME_GENERATION_SHOW_INDICATOR;
    public static final EnumValue<DLSSGMotionVectorMode> DLSS_FRAME_GENERATION_MV_MODE;
    public static final BooleanValue DLSS_FRAME_GENERATION_MV_DILATION;
    public static final BooleanValue DLSS_FRAME_GENERATION_MV_DEADZONE;

    public static final OperatingSystemType CURRENT_OS_TYPE = new OperatingSystem().type;
    public static final Runnable resolutionChangeCallback;

    static {
        ModConfigSpecBuilder builder = new ModConfigSpecBuilder();

        Supplier<String> defaultAlgoSupplier = () -> getDefaultAlgorithm().codeName;

        ENABLE_UPSCALE = builder.defineBoolean(
                "enable_upscale",
                () -> true,
                "Enable super-resolution upscaling"
        );
        UPSCALE_RATIO = builder.defineFloat(
                "upscale_ratio",
                () -> 1.7f,
                "Upscale ratio factor",
                value -> value >= 0.5f && value <= 4.0f
        );
        UPSCALE_ALGO = builder.defineString(
                "upscale_algo",
                defaultAlgoSupplier,
                "Algorithm used for upscaling",
                value -> {
                    if (value == null) {
                        return false;
                    }
                    AlgorithmDescription<?> algo = AlgorithmRegistry.getDescriptionByID(value);
                    return algo != null && algo.getExtraResources().checkAll(MCDLSSGConstants.NATIVE_LIBRARIES_DIR).isEmpty();
                }
        );
        SHARPNESS = builder.defineFloat(
                "sharpness",
                () -> 0.55f,
                "Sharpness adjustment factor",
                value -> value >= 0.0f && value <= 1.0f
        );

        CAPTURE_MODE = builder.defineEnum(
                "capture_mode",
                CaptureMode.class,
                () -> CaptureMode.A,
                "Screen capture mode"
        );

        PAUSE_GAME_ON_GUI = builder.defineBoolean(
                "pause_game_on_gui",
                () -> false,
                "Pause game when GUI is open"
        );

        INJECT_POST_CHAIN_BLACKLIST = builder.defineStringList(
                "inject_post_chain_blacklist",
                ArrayList::new,
                "List of post-processing chains to skip injection",
                value -> value != null && !value.isEmpty()
        );

        INTEROP_SYNC_MODE = builder.defineEnum(
                "interop_sync_mode",
                InteropSyncMode.class,
                () -> InteropSyncMode.LowLatency,
                ""
        );

        THEME = builder.defineEnum(
                "theme",
                MaterialTheme.class,
                () -> MaterialTheme.Light,
                "Interface theme"
        );

        THEME_COLOR = builder.defineString(
                "theme_color",
                () -> "#78DC77",
                "Primary color for the interface theme",
                value -> value != null && value.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")
        );

        THEME_SCHEME_VARIANT = builder.defineEnum(
                "theme_scheme_variant",
                SchemeVariant.class,
                () -> SchemeVariant.FIDELITY,
                "Color scheme variant for the interface theme"
        );

        THEME_CONTRAST_LEVEL = builder.defineFloat(
                "theme_contrast_level",
                () -> 0.0f,
                "Contrast level for the interface theme (-1.0 to 1.0)",
                value -> value >= -1.0f && value <= 1.0f
        );

        DEBUG_DUMP_SHADER = builder.defineBoolean(
                "debug/debug_dump_shader",
                () -> false,
                "Dump shaders for debugging purposes"
        );

        SKIP_INIT_VULKAN = builder.defineBoolean(
                "debug/skip_init_vulkan",
                () -> !(CURRENT_OS_TYPE == OperatingSystemType.ANDROID || CURRENT_OS_TYPE == OperatingSystemType.MACOS),
                "Skip Vulkan initialization (auto-set based on OS)"
        );

        ENABLE_RENDER_DOC = builder.defineBoolean(
                "debug/enable_render_doc",
                () -> (CURRENT_OS_TYPE == OperatingSystemType.WINDOWS || CURRENT_OS_TYPE == OperatingSystemType.LINUX) && Platform.currentPlatform.isDevelopmentEnvironment(),
                "Enable RenderDoc integration (auto-disabled on incompatible OS)"
        );

        ENABLE_IMGUI = builder.defineBoolean(
                "debug/enable_imgui",
                () -> (CURRENT_OS_TYPE == OperatingSystemType.WINDOWS || CURRENT_OS_TYPE == OperatingSystemType.LINUX) && Platform.currentPlatform.isDevelopmentEnvironment(),
                "Enable ImGui debug interface (auto-disabled on incompatible OS)"
        );

        ENABLE_DEBUG = builder.defineBoolean(
                "debug/enable_debug",
                () -> false,
                "Enable debug mode"
        );
        ENABLE_EXPERIMENTAL_FEATURES = builder.defineBoolean(
                "experiment/enable_experimental_features",
                () -> false,
                "Enable experimental features"
        );
        ENABLE_DLSS_FRAME_GENERATION = builder.defineBoolean(
                "dlss_frame_generation/enabled",
                () -> false,
                "Enable NVIDIA DLSS Frame Generation on the Forge 1.20.1 Windows presentation backend."
        );
        DLSS_FRAME_GENERATION_MULTIPLIER = builder.defineFloat(
                "dlss_frame_generation/multiplier",
                () -> 2.0f,
                "Target DLSS Frame Generation multiplier (2x-6x, clamped to what the GPU driver supports).",
                value -> value >= 2.0f && value <= 6.0f
        );
        DLSS_FRAME_GENERATION_UI_RECOMPOSITION = builder.defineBoolean(
                "dlss_frame_generation/ui_recomposition",
                () -> true,
                "Recompose HUD and GUI after generated frames."
        );
        DLSS_FRAME_GENERATION_SHOW_INDICATOR = builder.defineBoolean(
                "dlss_frame_generation/show_indicator",
                () -> true,
                "Show the DLSS Frame Generation mode and frame rate indicator on screen."
        );
        DLSS_FRAME_GENERATION_MV_MODE = builder.defineEnum(
                "dlss_frame_generation/motion_vector_mode",
                DLSSGMotionVectorMode.class,
                () -> DLSSGMotionVectorMode.AUTO,
                "Motion vector source for DLSS Frame Generation: AUTO (shaderpack or depth reprojection), REPROJECTION (depth reprojection only), DISABLED (let DLSS G estimate internally)."
        );
        DLSS_FRAME_GENERATION_MV_DILATION = builder.defineBoolean(
                "dlss_frame_generation/motion_vector_dilation",
                () -> true,
                "Dilate motion vectors into invalid regions (cutout holes)."
        );
        DLSS_FRAME_GENERATION_MV_DEADZONE = builder.defineBoolean(
                "dlss_frame_generation/motion_vector_deadzone",
                () -> true,
                "Zero out sub-pixel motion vector noise."
        );

        GENERATE_MOTION_VECTORS = builder.defineBoolean(
                "experiment/generate_motion_vectors",
                () -> false,
                "Generate motion vectors for advanced effects"
        );

        ENABLE_COMPAT_SHADER_COMPILER = builder.defineBoolean(
                "compat_shader_compiler",
                () -> {
                    try {
                        if (GL.getCapabilities() == null) {
                            return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                    return RenderSystem.isOnRenderThread() ? (
                            GraphicsCapabilities.detectGpuVendor() == GpuVendor.Intel ||
                            !GraphicsCapabilities.hasGLExtension("GL_ARB_gl_spirv") ||
                            (GraphicsCapabilities.getGLVersion()[0] >= 4 && GraphicsCapabilities.getGLVersion()[1] < 2)
                    ) : false;
                },
                "This option enables the use of a compatibility shader compiler for compiling shaders when set to true."
        );

        ENABLE_DETAILED_PROFILING = builder.defineBoolean(
                "debug/enable_detailed_profiling",
                () -> false,
                "Enable more detailed performance profiling for advanced analysis."
        );
        FORCE_DISABLE_SHADER_COMPAT = builder.defineBoolean(
                "force_disable_shader_compat",
                () -> false,
                "Force disable shader pack compatibility mode."
        );
        FORCE_DISABLE_SHADER_COMPAT.onChange((oldValue, newValue) -> {
            SRWorkModeManager.reloadShaderPack();
        });
        DISABLE_UPSCALE_ON_VANILLA = builder.defineBoolean(
                "disable_upscale_on_vanilla",
                () -> false,
                "Disable Super Resolution when using vanilla rendering."
        );

        INTERNAL_TEXTURE_FORMAT = builder.defineEnum(
                "internal_texture_format",
                InternalTextureFormat.class,
                () -> InternalTextureFormat.AUTO,
                "The precision of the internal texture format affects video memory consumption: higher precision results in greater consumption, while lower precision leads to smaller consumption. Note: Excessively low precision may cause noticeable color banding in the image."
        );
        INTERNAL_TEXTURE_FORMAT.onChange(
                (oldValue, newValue) ->
                        MCDLSSG.recreateAlgorithm()
        );

        SPECIAL = new SpecialConfigs(builder);
        Path configPath = MCDLSSGConstants.CONFIG_FILE;
        builder.configPath(configPath);
        SPEC = builder.build();
        resolutionChangeCallback = () -> {
            RenderHandlerManager.resize();
            Minecraft.getInstance().gameRenderer.resize(
                    RenderHandlerManager.getScreenWidth(),
                    RenderHandlerManager.getScreenHeight()
            );
            MCDLSSG.getInstance().forceResize(
                    RenderHandlerManager.getScreenWidth(),
                    RenderHandlerManager.getScreenHeight()
            );

        };
    }

    public static AlgorithmDescription<?> getDefaultAlgorithm() {
        if (B3DVulkanBridge.isB3DVulkanBackend()) {
            return AlgorithmDescriptions.NONE;
        }
        try {
            GL.getCapabilities();
        } catch (Exception e) {
            return AlgorithmDescriptions.NONE;
        }
        for (AlgorithmDescription<?> algorithmDescription : AlgorithmRegistry.getAlgorithmMap().values()) {
            if (algorithmDescription.requirement.check().support()) {
                return algorithmDescription;
            }
        }

        MCDLSSG.LOGGER.info("你的硬件不支持所有算法????"); //最逆天的一集
        return AlgorithmDescriptions.NONE;
    }

    public static float getRenderScaleFactor() {
        return ENABLE_UPSCALE.get() ? 1 / UPSCALE_RATIO.get() : 1;
    }

    public static AlgorithmDescription<?> getUpscaleAlgorithm() {
        String algoName = UPSCALE_ALGO.get();
        AlgorithmDescription<?> algo = AlgorithmRegistry.getDescriptionByID(algoName);

        if (algo == null) {
            algo = getDefaultAlgorithm();
            UPSCALE_ALGO.set(algo.codeName);
        }

        // rendering 初始化前不做 support 检查——Vulkan/GL caps 未就绪会误报，
        // 旧实现里还会 setUpscaleAlgorithm 触发 createAlgorithm 级联失败。
        if (!MCDLSSG.isRenderingInitialized) {
            return algo;
        }

        if (!algo.requirement.check().support() && !Platform.currentPlatform.isDevelopmentEnvironment()) {
            MCDLSSG.LOGGER.warn("算法 {} 不支持，回退到默认算法", algo.displayName);
            AlgorithmDescription<?> defaultAlgo = getDefaultAlgorithm();
            UPSCALE_ALGO.set(defaultAlgo.codeName);
            return defaultAlgo;
        }

        return algo;
    }

    public static synchronized boolean setUpscaleAlgorithm(AlgorithmDescription<?> newAlgo) {
        if (newAlgo == null) {
            newAlgo = getDefaultAlgorithm();
        }

        String algoName = UPSCALE_ALGO.get();
        AlgorithmDescription<?> currentAlgo = AlgorithmRegistry.getDescriptionByID(algoName);

        if (currentAlgo == newAlgo) {
            return true;
        }

        AbstractAlgorithm oldAlgorithmInstance = MCDLSSG.currentAlgorithm;
        AlgorithmDescription<?> oldDescription = MCDLSSG.algorithmDescription;

        try {
            UPSCALE_ALGO.set(newAlgo.codeName);
            MCDLSSG.algorithmDescription = newAlgo;

            if (!MCDLSSG.createAlgorithm()) {
                throw new RuntimeException("创建算法失败");
            }

            if (oldAlgorithmInstance != null) {
                try {
                    oldAlgorithmInstance.destroy();
                    return true;
                } catch (Exception e) {
                    MCDLSSG.LOGGER.error("销毁旧算法时出错", e);
                }
            }

        } catch (Exception e) {
            MCDLSSG.LOGGER.error("切换到算法 {} 失败，尝试回滚", newAlgo.displayName, e);

            UPSCALE_ALGO.set(oldDescription != null ? oldDescription.codeName : AlgorithmDescriptions.NONE.codeName);
            MCDLSSG.algorithmDescription = oldDescription;
            MCDLSSG.currentAlgorithm = oldAlgorithmInstance;

            if (oldAlgorithmInstance == null && oldDescription != null) {
                try {
                    if (!MCDLSSG.createAlgorithm()) {
                        fallbackToNone();
                    }
                } catch (Exception ex) {
                    fallbackToNone();
                }
            }
        }
        return false;
    }

    private static void fallbackToNone() {
        MCDLSSG.LOGGER.error("所有回滚尝试失败，使用NONE算法");
        UPSCALE_ALGO.set(AlgorithmDescriptions.NONE.codeName);
        MCDLSSG.algorithmDescription = AlgorithmDescriptions.NONE;
        MCDLSSG.createAlgorithm();
    }

    public static boolean isEnableUpscaleOriginal() {
        return ENABLE_UPSCALE.get();
    }

    public static boolean isEnableUpscale() {
        if (MCDLSSGConfig.isDisableUpscaleOnVanilla()) {
            SRWorkModeState state = SRWorkModeManager.getCurrentState();
            return isEnableUpscaleOriginal() && (
                    state.shaderPackInUse() ||
                            state.shaderPackLoading()
            );
        }
        return isEnableUpscaleOriginal();
    }

    public static void setEnableUpscale(boolean value) {
        boolean resolutionChanged = isEnableUpscale() != value;
        ENABLE_UPSCALE.set(value);
        if (resolutionChanged) {
            resolutionChangeCallback.run();
            if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
                SRWorkModeManager.reloadShaderPack();
            }
        }

    }

    public static float getSharpness() {
        return SHARPNESS.get();
    }

    public static void setSharpness(float value) {
        SHARPNESS.set(value);
    }

    public static CaptureMode getCaptureMode() {
        return CAPTURE_MODE.get();
    }

    public static void setCaptureMode(CaptureMode value) {
        CAPTURE_MODE.set(value);
    }

    public static float getUpscaleRatio() {
        return Math.max(UPSCALE_RATIO.get(), getMinUpscaleRatio());
    }

    public static void setUpscaleRatio(float value) {
        boolean resolutionChanged = getUpscaleRatio() != value;
        value = Math.max(value, getMinUpscaleRatio());
        UPSCALE_RATIO.set(value);
        if (resolutionChanged) {
            resolutionChangeCallback.run();
        }
    }

    public static boolean isDebugDumpShader() {
        return DEBUG_DUMP_SHADER.get();
    }

    public static void setDebugDumpShader(boolean value) {
        DEBUG_DUMP_SHADER.set(value);
    }

    public static boolean isSkipInitVulkan() {
        return SKIP_INIT_VULKAN.get();
    }

    public static void setSkipInitVulkan(boolean value) {
        SKIP_INIT_VULKAN.set(value);
    }

    public static boolean isEnableRenderDoc() {
        return ENABLE_RENDER_DOC.get();
    }

    public static void setEnableRenderDoc(boolean value) {
        ENABLE_RENDER_DOC.set(value);
    }

    public static boolean isEnableImgui() {
        return ENABLE_IMGUI.get();
    }

    public static void setEnableImgui(boolean value) {
        ENABLE_IMGUI.set(value);
    }

    public static boolean isGenerateMotionVectors() {
        return GENERATE_MOTION_VECTORS.get() || isEnableDLSSFrameGeneration();
    }

    public static void setGenerateMotionVectors(boolean value) {
        GENERATE_MOTION_VECTORS.set(value);
    }

    public static boolean isEnableDLSSFrameGeneration() {
        return ENABLE_DLSS_FRAME_GENERATION.get();
    }

    public static void setEnableDLSSFrameGeneration(boolean value) {
        ENABLE_DLSS_FRAME_GENERATION.set(value);
    }

    public static int getDLSSFrameGenerationFramesToGenerate() {
        return Math.max(1, Math.round(DLSS_FRAME_GENERATION_MULTIPLIER.get()) - 1);
    }

    public static void setDLSSFrameGenerationMultiplier(float value) {
        DLSS_FRAME_GENERATION_MULTIPLIER.set(Math.max(2.0f, Math.min(6.0f, value)));
    }

    public static boolean isDLSSFrameGenerationUIRecompositionEnabled() {
        return DLSS_FRAME_GENERATION_UI_RECOMPOSITION.get();
    }

    public static void setDLSSFrameGenerationUIRecompositionEnabled(boolean value) {
        DLSS_FRAME_GENERATION_UI_RECOMPOSITION.set(value);
    }

    public static boolean isDLSSFrameGenerationIndicatorVisible() {
        return DLSS_FRAME_GENERATION_SHOW_INDICATOR.get();
    }

    public static void setDLSSFrameGenerationIndicatorVisible(boolean value) {
        DLSS_FRAME_GENERATION_SHOW_INDICATOR.set(value);
    }

    public static DLSSGMotionVectorMode getDLSSFrameGenerationMotionVectorMode() {
        return DLSS_FRAME_GENERATION_MV_MODE.get();
    }

    public static void setDLSSFrameGenerationMotionVectorMode(DLSSGMotionVectorMode value) {
        DLSS_FRAME_GENERATION_MV_MODE.set(value);
    }

    public static boolean isDLSSFrameGenerationMotionVectorDilationEnabled() {
        return DLSS_FRAME_GENERATION_MV_DILATION.get();
    }

    public static boolean isDLSSFrameGenerationMotionVectorDeadzoneEnabled() {
        return DLSS_FRAME_GENERATION_MV_DEADZONE.get();
    }

    public static boolean isPauseGameOnGui() {
        return PAUSE_GAME_ON_GUI.get();
    }

    public static void setPauseGameOnGui(boolean value) {
        PAUSE_GAME_ON_GUI.set(value);
    }

    public static List<String> getInjectPostChainBlackList() {
        return INJECT_POST_CHAIN_BLACKLIST.get();
    }

    public static void setInjectPostChainBlackList(List<String> value) {
        INJECT_POST_CHAIN_BLACKLIST.set(value);
    }

    public static boolean isEnableCompatShaderCompiler() {
        return ENABLE_COMPAT_SHADER_COMPILER.get() || ENABLE_COMPAT_SHADER_COMPILER.getDefault();
    }

    public static void setEnableCompatShaderCompiler(boolean value) {
        ENABLE_COMPAT_SHADER_COMPILER.set(value);
    }



    public static boolean isEnableDetailedProfiling() {
        return ENABLE_DETAILED_PROFILING.get();
    }

    public static void setEnableDetailedProfiling(boolean value) {
        ENABLE_DETAILED_PROFILING.set(value);
    }

    public static boolean isEnableDebug() {
        return ENABLE_DEBUG.get();
    }

    public static void setEnableDebug(boolean value) {
        ENABLE_DEBUG.set(value);
        GlDebug.setEnabled(value);
        VulkanDebug.setEnabled(value);
    }

    public static boolean isForceDisableShaderCompat() {
        return FORCE_DISABLE_SHADER_COMPAT.get();
    }

    public static void setForceDisableShaderCompat(boolean value) {
        FORCE_DISABLE_SHADER_COMPAT.set(value);
    }

    public static boolean isDisableUpscaleOnVanilla() {
        return DISABLE_UPSCALE_ON_VANILLA.get();
    }

    public static void setDisableUpscaleOnVanilla(boolean value) {
        boolean lastEnableUpscale = isEnableUpscale();
        DISABLE_UPSCALE_ON_VANILLA.set(value);
        if (lastEnableUpscale != isEnableUpscale()) {
            resolutionChangeCallback.run();
        }
    }

    public static boolean isEnableExperimentalFeatures() {
        return ENABLE_EXPERIMENTAL_FEATURES.get();
    }

    public static void setEnableExperimentalFeatures(boolean value) {
        ENABLE_EXPERIMENTAL_FEATURES.set(value);
    }





    public static String getInternalTextureFormatGlslFormatQualifier() {
        return getInternalTextureFormat().getGlslFormatQualifier();
    }

    public static TextureFormat getInternalTextureFormat() {
        //user settings > shaderPack > default
        if (INTERNAL_TEXTURE_FORMAT.get() == InternalTextureFormat.AUTO) {
            SRWorkModeState state = SRWorkModeManager.getCurrentState();
            TextureFormat format = state.internalTextureFormat();
            return format == null ? TextureFormat.RGBA16F : format;
        }
        return INTERNAL_TEXTURE_FORMAT.get().format();
    }

    public static void setInternalTextureFormat(InternalTextureFormat format) {
        INTERNAL_TEXTURE_FORMAT.set(format);
    }

    public static MaterialTheme getTheme() {
        return THEME.get();
    }

    public static void setTheme(MaterialTheme value) {
        THEME.set(value);
    }

    public static InteropSyncMode getInteropSyncMode() {
        return INTEROP_SYNC_MODE.get();
    }

    public static void setInteropSyncMode(InteropSyncMode value) {
        INTEROP_SYNC_MODE.set(value);
    }

    public static float getMinUpscaleRatio() {
        if (SRWorkModeManager.isCurrentMode(SRWorkModeManager.SHADER_COMPAT)) {
            return 1.0f;
        }
        if (
                getUpscaleAlgorithm().equals(AlgorithmDescriptions.DLSS) ||
                        getUpscaleAlgorithm().equals(AlgorithmDescriptions.XESS)

        ) {
            return 1.0f;
        }
        return 0.5f;
        /*
        int maxSize = 16384;
        if (Minecraft.getInstance().getWindow() == null) return 0.1f;
        double maxWidth = 1 / ((double) maxSize / Minecraft.getInstance().getWindow().getScreenWidth());
        double maxHeight = 1 / ((double) maxSize / Minecraft.getInstance().getWindow().getScreenHeight());
        return (float) Math.max(maxWidth, maxHeight);
        */
    }

    public static Color getThemeColor() {
        String colorStr = THEME_COLOR.get();
        try {
            return Color.from(colorStr);
        } catch (IllegalArgumentException e) {
            MCDLSSG.LOGGER.warn("无效的主题颜色配置: {}，使用默认颜色", colorStr);
            return Color.from("#78DC77");
        }
    }

    public static void setThemeColor(Color color) {
        THEME_COLOR.set(color.hex());
    }

    public static SchemeVariant getThemeSchemeVariant() {
        return THEME_SCHEME_VARIANT.get();
    }

    public static void setThemeSchemeVariant(SchemeVariant value) {
        THEME_SCHEME_VARIANT.set(value);
    }

    public static float getThemeContrastLevel() {
        return THEME_CONTRAST_LEVEL.get();
    }

    public static void setThemeContrastLevel(float value) {
        THEME_CONTRAST_LEVEL.set(Math.max(-1.0f, Math.min(1.0f, value)));
    }
}
