package com.chinaex123.client;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.screen.AutoShippingBoxScreen;
import com.chinaex123.shipping_box.client.screen.ShippingBoxScreen;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端模组总线事件处理；
 * 将自定义 MenuType 绑定到对应的 Screen，使售货箱界面渲染左侧账单面板。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SHIPPING_BOX.get(), ShippingBoxScreen::new);
        event.register(ModMenuTypes.AUTO_SHIPPING_BOX.get(), AutoShippingBoxScreen::new);
    }
}
