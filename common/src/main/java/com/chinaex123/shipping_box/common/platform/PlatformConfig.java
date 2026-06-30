package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class PlatformConfig {
    @ExpectPlatform
    public static int getExchangeTime() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isExchangeEffectsEnabled() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isTransactionLoggingEnabled() {
        throw new AssertionError();
    }
}
