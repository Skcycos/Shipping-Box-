package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class PlatformModCheck {
    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError();
    }
}
