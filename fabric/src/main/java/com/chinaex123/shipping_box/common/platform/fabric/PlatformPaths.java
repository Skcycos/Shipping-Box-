package com.chinaex123.shipping_box.common.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class PlatformPaths {
    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
