package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;

public class PlatformAttributes {
    @ExpectPlatform
    public static double getSellingPriceBoost(ServerPlayer player) {
        throw new AssertionError();
    }
}
