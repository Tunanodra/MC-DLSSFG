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

package com.dgtdi.mcdlssg.common.config.special;

import com.dgtdi.mcdlssg.api.MCDLSSGAPI;
import com.dgtdi.mcdlssg.api.config.ModConfigSpecBuilder;
import com.dgtdi.mcdlssg.api.config.values.single.BooleanValue;
import com.dgtdi.mcdlssg.api.config.values.single.EnumValue;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.ConfigSpecType;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import com.dgtdi.mcdlssg.thirdparty.fsr2.common.Fsr2Version;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

public class FSR2SpecialConfig extends SpecialConfig {

    public EnumValue<Fsr2Version> VERSION = specBuilder.defineEnum(
            "special/fsr2/version",
            Fsr2Version.class,
            () -> Fsr2Version.V233
    );
    public BooleanValue FP16 = this.specBuilder.defineBoolean(
            "special/fsr2/fp16",
            () -> false,
            ""
    );

    public FSR2SpecialConfig(ModConfigSpecBuilder specBuilder) {
        super(specBuilder);
    }

    @Override
    protected void buildDescriptions(Map<String, SpecialConfigDescription<?>> map) {
        map.put(
                "fp16",
                new SpecialConfigDescription<Boolean>()
                        .setValue(getSpecialConfigs().FSR2.FP16.get())
                        .setKey("fp16")
                        .setName(Component.translatable("mcdlssg.screen.config.special.fsr2.fp16.name"))
                        .setTooltip(Component.translatable("mcdlssg.screen.config.special.fsr2.fp16.tooltip"))
                        .setType(ConfigSpecType.BOOLEAN)
                        .setSaveConsumer((v) -> {
                            if (getSpecialConfigs().FSR2.FP16.get() != v) {
                                getSpecialConfigs().FSR2.FP16.set(v);
                                if (MCDLSSGAPI.getCurrentAlgorithmDescription() == AlgorithmDescriptions.FSR2) {
                                    MCDLSSG.recreateAlgorithm();
                                }
                            }
                        })
                        .setDefaultValue(true)
        );
        map.put(
                "version",
                new SpecialConfigDescription<>()
                        .setValue(getSpecialConfigs().FSR2.VERSION.get())
                        .setDefaultValue(Fsr2Version.V233)
                        .setValueNameSupplier((variant) -> switch ((Fsr2Version) variant) {
                            case V233 -> Optional.of(Component.literal("2.3.3"));
                            case V221 -> Optional.of(Component.literal("2.2.1"));
                        })
                        .setName(Component.translatable("mcdlssg.screen.config.special.fsr2.version.name"))
                        .setTooltip(Component.translatable("mcdlssg.screen.config.special.fsr2.version.tooltip"))
                        .setKey("version")
                        .setSaveConsumer((v) -> {
                            if (getSpecialConfigs().FSR2.VERSION.get() != v) {
                                getSpecialConfigs().FSR2.VERSION.set(v);
                                if (MCDLSSGAPI.getCurrentAlgorithmDescription() == AlgorithmDescriptions.FSR2) {
                                    MCDLSSG.recreateAlgorithm();
                                }
                            }
                        })
                        .setType(ConfigSpecType.ENUM)
                        .setClazz(Fsr2Version.class)
        );
    }
}
