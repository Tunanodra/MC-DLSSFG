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

package com.dgtdi.mcdlssg.common.upscale;

import com.dgtdi.mcdlssg.api.QualityPreset;
import com.dgtdi.mcdlssg.api.MCDLSSGAPI;
import com.dgtdi.mcdlssg.api.event.AlgorithmRegisterEvent;
import com.dgtdi.mcdlssg.api.platform.OperatingSystem;
import com.dgtdi.mcdlssg.api.platform.OperatingSystemType;
import com.dgtdi.mcdlssg.api.platform.Platform;
import com.dgtdi.mcdlssg.api.platform.SystemArchitecture;
import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.api.registry.AlgorithmRegistry;
import com.dgtdi.mcdlssg.api.registry.ExtraResource;
import com.dgtdi.mcdlssg.api.registry.ExtraResources;
import com.dgtdi.mcdlssg.api.utils.Requirement;
import com.dgtdi.mcdlssg.common.upscale.dlss.DLSS;
import com.dgtdi.mcdlssg.common.upscale.fsr2.FSR2;
import com.dgtdi.mcdlssg.common.upscale.none.None;
import com.dgtdi.mcdlssg.common.upscale.xess.XeSS;
import com.dgtdi.mcdlssg.core.graphics.opengl.Gl;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AlgorithmDescriptions {
    private static final List<QualityPreset> FSR_QUALITY_PRESETS = List.of(
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.fsr.aa"))
                    .setCodeName("fsr_aa")
                    .setUpscaleRatio(1f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.fsr.quality"))
                    .setCodeName("fsr_quality")
                    .setUpscaleRatio(1.5f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.fsr.balanced"))
                    .setCodeName("fsr_balanced")
                    .setUpscaleRatio(1.7f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.fsr.performance"))
                    .setCodeName("fsr_performance")
                    .setUpscaleRatio(2.0f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.fsr.ultra_performance"))
                    .setCodeName("fsr_ultra_performance")
                    .setUpscaleRatio(3.0f)
    );
    private static final List<QualityPreset> XESS_QUALITY_PRESETS = List.of(
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.ultra_performance"))
                    .setCodeName("xess_ultra_performance")
                    .setUpscaleRatio(3.0f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.performance"))
                    .setCodeName("xess_performance")
                    .setUpscaleRatio(2.3f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.balanced"))
                    .setCodeName("xess_balanced")
                    .setUpscaleRatio(2.0f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.quality"))
                    .setCodeName("xess_quality")
                    .setUpscaleRatio(1.7f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.ultra_quality"))
                    .setCodeName("xess_ultra_quality")
                    .setUpscaleRatio(1.5f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.ultra_quality_plus"))
                    .setCodeName("xess_ultra_quality_plus")
                    .setUpscaleRatio(1.3f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.xess.native_aa"))
                    .setCodeName("xess_native_aa")
                    .setUpscaleRatio(1.0f)
    );
    private static final List<QualityPreset> DLSS_QUALITY_PRESETS = List.of(
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.dlss.ultra_performance"))
                    .setCodeName("dlss_ultra_performance")
                    .setUpscaleRatio(3.0f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.dlss.performance"))
                    .setCodeName("dlss_performance")
                    .setUpscaleRatio(2.0f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.dlss.balanced"))
                    .setCodeName("dlss_balanced")
                    .setUpscaleRatio(1.724f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.dlss.quality"))
                    .setCodeName("dlss_quality")
                    .setUpscaleRatio(1.5f),
            new QualityPreset()
                    .setName(Component.translatable("mcdlssg.algo.preset.dlss.dlaa"))
                    .setCodeName("dlss_dlaa")
                    .setUpscaleRatio(1.0f)
    );
    public static final AlgorithmDescription<None> NONE = AlgorithmDescription.builder(None.class)
            .briefName("None")
            .codeName("none")
            .displayName("None")
            .requirement(Requirement.nothing())
            .build();

    public static final AlgorithmDescription<FSR2> FSR2 = AlgorithmDescription.builder(FSR2.class)
            .briefName("AMD FSR 2 (OpenGL)")
            .codeName("fsr2")
            .displayName("AMD FidelityFX Super Resolution 2 (OpenGL)")
            .requirement(
                    Requirement.nothing()
                            .requiredGlExtension("GL_KHR_shader_subgroup")
                            .glMajorVersion(4)
                            .glMinorVersion(5)
                            .isFalse(Gl::isLegacy)
                            .isTrue(Gl::isSupportDSA)
            )
            .supportJitter(true)
            .build();

    public static final AlgorithmDescription<XeSS> XESS = AlgorithmDescription.builder(XeSS.class)
            .briefName("Intel XeSS")
            .codeName("xess")
            .displayName("Intel Xe Super Sampling")
            .requirement(
                    Requirement.nothing()
                            .addSupportedOS(new OperatingSystem(SystemArchitecture.X86_64, OperatingSystemType.WINDOWS))
                            .requiredGlExtension("GL_EXT_memory_object")
                            .requiredGlExtension("GL_EXT_semaphore")
                            .glMajorVersion(4)
                            .glMinorVersion(6)
                            .requireVulkan(true)
            )
            .extraResources(
                    ExtraResources.builder()
                            .add(ExtraResource.builder("libxess.dll")
                                    .addRemote(
                                            "https://cnb.cool/187J3X1-114514/mc-mcdlssg/-/releases/download/assets/libxess.dll",
                                            "CNB Mirror"
                                    )
                                    .build()
                            )
                            .build()
            )
            .supportJitter(true)
            .qualityPresets(XESS_QUALITY_PRESETS)
            .customUpscaleRatio(false)
            .build();

    public static final AlgorithmDescription<DLSS> DLSS = AlgorithmDescription.builder(DLSS.class)
            .briefName("NVIDIA DLSS")
            .codeName("dlss")
            .displayName("NVIDIA DLSS")
            .requirement(
                    Requirement.nothing()
                            .addSupportedOS(new OperatingSystem(SystemArchitecture.X86_64, OperatingSystemType.WINDOWS))
                            .addSupportedOS(new OperatingSystem(SystemArchitecture.X86_64, OperatingSystemType.LINUX))
                            .requiredGlExtension("GL_EXT_memory_object")
                            .requiredGlExtension("GL_EXT_semaphore")
                            .glMajorVersion(4)
                            .glMinorVersion(6)
                            .requireVulkan(true)
            )
            .extraResources(
                    Platform.currentPlatform.getOS().type == OperatingSystemType.WINDOWS
                            ? ExtraResources.builder()
                            .add(ExtraResource.builder("nvngx_dlss.dll")
                                    .addRemote(
                                            "https://cnb.cool/187J3X1-114514/mc-mcdlssg/-/releases/download/assets/nvngx_dlss.dll",
                                            "CNB Mirror"
                                    )
                                    .build()
                            )
                            .build()
                            : ExtraResources.builder().build()
            )
            .supportJitter(true)
            .qualityPresets(DLSS_QUALITY_PRESETS)
            .customUpscaleRatio(false)
            .build();

    public static void registryAlgorithms() {
        AlgorithmRegistry.registry(NONE);
        AlgorithmRegistry.registry(FSR2);
        AlgorithmRegistry.registry(XESS);
        AlgorithmRegistry.registry(DLSS);
        MCDLSSGAPI.EVENT_BUS.post(new AlgorithmRegisterEvent());
    }
}
