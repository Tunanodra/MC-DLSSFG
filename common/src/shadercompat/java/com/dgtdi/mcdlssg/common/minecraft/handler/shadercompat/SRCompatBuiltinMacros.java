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

package com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat;

import com.dgtdi.mcdlssg.api.MCDLSSGAPI;
import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.api.registry.AlgorithmRegistry;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.config.enums.DLSSRenderPreset;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmManager;

import java.util.*;

public class SRCompatBuiltinMacros {

    public static void addMacros(JsonMacroPreprocessor preprocessor) {
        // region V1
        preprocessor.addMacro("SR_INSTALLED", "1");

        Map<AlgorithmDescription<?>, Integer> idMap = new HashMap<>();
        List<AlgorithmDescription<?>> algorithms = new ArrayList<>(AlgorithmRegistry.getAlgorithmMap().values());

        AlgorithmRegistry.getAlgorithmMap().values().forEach((desc) -> {
            int id = algorithms.indexOf(desc) + 0x546F0;
            idMap.put(desc, id);
            preprocessor.addMacro("SR_ALGO_" + desc.codeName.toUpperCase(), Integer.toString(id));
        });

        Arrays.stream(DLSSRenderPreset.values()).toList().forEach((preset) -> {
            preprocessor.addMacro("SR_ALGO_DLSS_RENDERPRESET_" + preset.toString(), Integer.toString(preset.getCode()));
        });

        AlgorithmDescription<?> description = MCDLSSG.algorithmDescription;

        if (MCDLSSGConfig.isEnableUpscaleOriginal()) {
            AlgorithmDescription<?> selectedAlgorithm = description != null
                    ? description
                    : MCDLSSGConfig.getUpscaleAlgorithm();
            preprocessor.addMacro("SR_ENABLE", "1");
            preprocessor.addMacro("SR_DISABLE", "0");
            preprocessor.addMacro("SR_ALGO_SUPPORTS_JITTER",
                    AlgorithmManager.supportsJitter(selectedAlgorithm) ? "1" : "0");
            preprocessor.addMacro("SR_USING_ALGO", Integer.toString(idMap.get(selectedAlgorithm)));
            preprocessor.addMacro("SR_SHOULD_APPLY_SCALE", "1");
            preprocessor.addMacro("SR_SHOULD_APPLY_JITTER", "1");
            preprocessor.addMacro("SR_SCALED_WIDTH",
                    Integer.toString(MCDLSSGAPI.getRenderWidth()));
            preprocessor.addMacro("SR_SCALED_HEIGHT",
                    Integer.toString(MCDLSSGAPI.getRenderHeight()));
            preprocessor.addMacro("SR_SCREEN_WIDTH",
                    Integer.toString(MCDLSSGAPI.getScreenWidth()));
            preprocessor.addMacro("SR_SCREEN_HEIGHT",
                    Integer.toString(MCDLSSGAPI.getScreenHeight()));
            preprocessor.addMacro("SR_UPSCALE_RATIO",
                    Float.toString(MCDLSSGConfig.getUpscaleRatio()));
            preprocessor.addMacro("SR_RENDER_SCALE_FACTOR",
                    Float.toString(MCDLSSGConfig.getRenderScaleFactor()));
            preprocessor.addMacro("SR_JITTER_SEQUENCE_LENGTH",
                    Integer.toString(AlgorithmManager.getConfiguredJitterSequenceLength()));
            preprocessor.addMacro("SR_ALGO_DLSS_RENDERPRESET",
                    selectedAlgorithm.equals(AlgorithmDescriptions.DLSS) ?
                            Integer.toString(MCDLSSGConfig.SPECIAL.DLSS.RENDER_PRESET.get().getCode()) :
                            "0");
        } else {
            preprocessor.addMacro("SR_ENABLE", "0");
            preprocessor.addMacro("SR_DISABLE", "1");
            preprocessor.addMacro("SR_ALGO_SUPPORTS_JITTER", "0");
            preprocessor.addMacro("SR_USING_ALGO", "0");
            preprocessor.addMacro("SR_SHOULD_APPLY_SCALE", "0");
            preprocessor.addMacro("SR_SHOULD_APPLY_JITTER", "0");
            preprocessor.addMacro("SR_SCALED_WIDTH",
                    Integer.toString(MCDLSSGAPI.getScreenWidth()));
            preprocessor.addMacro("SR_SCALED_HEIGHT",
                    Integer.toString(MCDLSSGAPI.getScreenHeight()));
            preprocessor.addMacro("SR_SCREEN_WIDTH",
                    Integer.toString(MCDLSSGAPI.getScreenWidth()));
            preprocessor.addMacro("SR_SCREEN_HEIGHT",
                    Integer.toString(MCDLSSGAPI.getScreenHeight()));
            preprocessor.addMacro("SR_UPSCALE_RATIO", Float.toString(1.0f));
            preprocessor.addMacro("SR_RENDER_SCALE_FACTOR", Float.toString(1.0f));
            preprocessor.addMacro("SR_JITTER_SEQUENCE_LENGTH", "0");
        }
        // endregion

        // region V2
        preprocessor.addMacro("SR_CONFIG_SCHEMA_VERSION", "2");

        if (MCDLSSGConfig.isEnableUpscaleOriginal()) {
            preprocessor.addMacro("SR_UPSCALE_RATIO_HALF",
                    Float.toString(MCDLSSGConfig.getUpscaleRatio() * 0.5F));
            preprocessor.addMacro("SR_RENDER_SCALE_FACTOR_HALF",
                    Float.toString(MCDLSSGConfig.getRenderScaleFactor() * 0.5F));
        } else {
            preprocessor.addMacro("SR_UPSCALE_RATIO_HALF", Float.toString(0.5F));
            preprocessor.addMacro("SR_RENDER_SCALE_FACTOR_HALF", Float.toString(0.5F));
        }
        // endregion
    }
}
