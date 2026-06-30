package com.chinaex123.shipping_box.common.platform.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class PlatformPaths {
    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
