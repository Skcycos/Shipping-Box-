package com.chinaex123.shipping_box.client.screen;

import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 普通售货箱界面；
 * 在原版箱子界面上额外渲染左侧账单面板（预计收益/已放入件数/结算时间）。
 */
public class ShippingBoxScreen extends AbstractBillScreen<ShippingBoxMenu> {

    public ShippingBoxScreen(ShippingBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
