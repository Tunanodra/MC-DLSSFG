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

package com.dgtdi.mcdlssg.irisapi.mixin.composite;

import com.dgtdi.mcdlssg.irisapi.NamedCompositePass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = {"net.irisshaders.iris.pipeline.CompositeRenderer$Pass"},remap = false)
public class CompositeRendererPassMixin implements NamedCompositePass {
    @Unique
    private String mcdlssg$name0;

    #if MC_VER <= MC_1_20_1
    @Override
    public String mcdlssg$getName() {
        return mcdlssg$name0;
    }

    @Override
    public void mcdlssg$setName(String name) {
        mcdlssg$name0 = name;
    }
    #else
    @Shadow
    String name;

    @Override
    public String mcdlssg$getName() {
        return name;
    }

    @Override
    public void mcdlssg$setName(String name) {
        this.name = name;
    }
    #endif
}
