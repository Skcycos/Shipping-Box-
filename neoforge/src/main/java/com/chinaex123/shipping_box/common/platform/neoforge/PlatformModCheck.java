package com.chinaex123.shipping_box.common.platform.neoforge;

import net.neoforged.fml.ModList;

public class PlatformModCheck {
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
