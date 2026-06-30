package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

public class PlatformPaths {
    @ExpectPlatform
    public static Path getConfigDir() {
        throw new AssertionError();
    }
}
