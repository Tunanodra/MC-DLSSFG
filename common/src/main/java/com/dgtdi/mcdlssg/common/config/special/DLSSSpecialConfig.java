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

/*
 * Super Resolution
 */
package com.dgtdi.mcdlssg.common.config.special;

import com.dgtdi.mcdlssg.api.MCDLSSGAPI;
import com.dgtdi.mcdlssg.api.config.ModConfigSpecBuilder;
import com.dgtdi.mcdlssg.api.config.values.single.EnumValue;
import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.common.config.ConfigSpecType;
import com.dgtdi.mcdlssg.common.config.enums.DLSSRenderPreset;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class DLSSSpecialConfig extends SpecialConfig {
    public EnumValue<DLSSRenderPreset> RENDER_PRESET = specBuilder.defineEnum(
            "special/dlss/render_preset",
            DLSSRenderPreset.class,
            () -> DLSSRenderPreset.K
    );

    public DLSSSpecialConfig(ModConfigSpecBuilder specBuilder) {
        super(specBuilder);
    }

    @Override
    protected void buildDescriptions(Map<String, SpecialConfigDescription<?>> map) {
        map.put(
                "render_preset",
                new SpecialConfigDescription<DLSSRenderPreset>()
                        .setKey("render_preset")
                        .setName(Component.translatable("mcdlssg.screen.config.special.dlss.renderpreset.name"))
                        .setTooltip(Component.translatable("mcdlssg.screen.config.special.dlss.renderpreset.tooltip"))
                        .setType(ConfigSpecType.ENUM)
                        .setClazz(DLSSRenderPreset.class)
                        .setDefaultValue(DLSSRenderPreset.K)
                        .setSaveConsumer((v) -> {
                            if (getSpecialConfigs().DLSS.RENDER_PRESET.get() != v) {
                                getSpecialConfigs().DLSS.RENDER_PRESET.set(v);
                                if (MCDLSSGAPI.getCurrentAlgorithmDescription() == AlgorithmDescriptions.DLSS) {
                                    MCDLSSG.recreateAlgorithm();
                                }
                            }
                        })
                        .setValue(RENDER_PRESET.get())
        );
    }
}
