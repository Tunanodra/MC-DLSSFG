/*
 * Super Resolution
 * Copyright (c) 2025-2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.dgtdi.mcdlssg.common.config.special;

import com.dgtdi.mcdlssg.api.config.ModConfigSpecBuilder;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import com.dgtdi.mcdlssg.core.impl.Pair;

import java.util.HashMap;
import java.util.Map;

public class SpecialConfigs {
    public FSR2SpecialConfig FSR2;
    public DLSSSpecialConfig DLSS;

    public transient Map<String, Pair<SpecialConfig, String>> description = new HashMap<>();

    public SpecialConfigs(ModConfigSpecBuilder builder) {
        builder.comment("special", "Algorithm special configuration");
        FSR2 = new FSR2SpecialConfig(builder);
        DLSS = new DLSSSpecialConfig(builder);
        description.put("fsr2", Pair.of(FSR2, AlgorithmDescriptions.FSR2.getDisplayName()));
        description.put("dlss", Pair.of(DLSS, AlgorithmDescriptions.DLSS.getDisplayName()));
    }
}
