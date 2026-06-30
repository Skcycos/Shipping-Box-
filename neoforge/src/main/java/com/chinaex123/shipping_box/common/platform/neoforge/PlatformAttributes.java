package com.chinaex123.shipping_box.common.platform.neoforge;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import net.minecraft.server.level.ServerPlayer;

public class PlatformAttributes {
    public static double getSellingPriceBoost(ServerPlayer player) {
        return player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);
    }
}
