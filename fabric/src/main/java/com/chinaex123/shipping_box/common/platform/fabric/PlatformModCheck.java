package com.chinaex123.shipping_box.common.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

public class PlatformModCheck {
    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
