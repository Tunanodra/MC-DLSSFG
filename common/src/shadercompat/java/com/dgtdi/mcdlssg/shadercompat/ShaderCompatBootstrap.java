package com.dgtdi.mcdlssg.shadercompat;

import com.dgtdi.mcdlssg.common.workmode.SRWorkModeManager;

public final class ShaderCompatBootstrap {
    private ShaderCompatBootstrap() {
    }

    public static void register() {
        SRWorkModeManager.register(new ShaderCompatSRWorkModeProvider());
    }
}
