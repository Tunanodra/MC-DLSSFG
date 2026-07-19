package com.dgtdi.mcdlssg.common.workmode;

public final class HackWorkModeBootstrap {
    private HackWorkModeBootstrap() {
    }

    public static void register() {
        SRWorkModeManager.register(new HackSRWorkModeProvider());
    }
}
