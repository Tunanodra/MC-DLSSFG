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

package com.dgtdi.mcdlssg.shadercompat.mixin.core;

import com.google.common.collect.ImmutableList;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat.SRShaderCompatData;
import com.dgtdi.mcdlssg.common.minecraft.handler.shadercompat.ShaderCompatHandler;
import com.dgtdi.mcdlssg.shadercompat.IrisSRCompatShaderPack;
import com.dgtdi.mcdlssg.shadercompat.IrisShaderCompatUpscaleDispatcher;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Map;

@Mixin(value = ShaderPack.class, remap = false)
public class ShaderPackMixin implements IrisSRCompatShaderPack {
    @Unique
    private SRShaderCompatData mcdlssg$config;

    #if MC_VER > MC_1_20_6
    @Inject(
            method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",
            at = @At("RETURN"),
            remap = false
    )
    private void loadMCDLSSGComaptConfig(
            Path root,
            Map<?, ?> changedConfigs,
            ImmutableList<?> environmentDefines,
            boolean isZip,
            CallbackInfo ci
    )
    #else
    @Inject(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V", at = @At("RETURN"), remap = false)
    private void loadMCDLSSGComaptConfig(
            Path root,
            Map<?, ?> changedConfigs,
            ImmutableList<?> environmentDefines,
            CallbackInfo ci
    )
    #endif {
        mcdlssg$config = ShaderCompatHandler.getShaderCompatData();
        IrisShaderCompatUpscaleDispatcher.reset();
    }

    @Unique
    public SRShaderCompatData mcdlssg$getMCDLSSGComaptConfig() {
        return MCDLSSGConfig.isForceDisableShaderCompat() ? null : mcdlssg$config;
    }

    @Unique
    public boolean mcdlssg$isSupportsMCDLSSG() {
        return !MCDLSSGConfig.isForceDisableShaderCompat() && mcdlssg$config != null;
    }
}
