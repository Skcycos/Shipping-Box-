package com.chinaex123.shipping_box.fabric;

import com.chinaex123.shipping_box.ShippingBoxCommon;
import net.fabricmc.api.ModInitializer;

public final class ShippingBoxFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ShippingBoxCommon.init();
    }
}
