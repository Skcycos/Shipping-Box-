package com.chinaex123.client.screen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShippingBoxScreen extends AbstractContainerScreen<ShippingBoxMenu> {

    private static final ResourceLocation TEXTURE_ZH =
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "textures/gui/shipping_box_zh_cn.png");
    private static final ResourceLocation TEXTURE_EN =
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "textures/gui/shipping_box_en_us.png");

    private static ResourceLocation selectTexture() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return "zh_cn".equals(lang) ? TEXTURE_ZH : TEXTURE_EN;
    }

    public ShippingBoxScreen(ShippingBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ShippingBoxLayout.IMAGE_WIDTH;   // 195
        this.imageHeight = ShippingBoxLayout.IMAGE_HEIGHT; // 254
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不渲染标题和物品栏文字
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // 将 780×1016 高清贴图缩放渲染为 195×254 GUI
        graphics.blit(
                selectTexture(),
                x, y,
                ShippingBoxLayout.IMAGE_WIDTH,   // 屏幕渲染宽度 195
                ShippingBoxLayout.IMAGE_HEIGHT,  // 屏幕渲染高度 254
                0.0F, 0.0F,                       // UV 起始
                ShippingBoxLayout.TEXTURE_WIDTH,  // 纹理采样宽度 780
                ShippingBoxLayout.TEXTURE_HEIGHT, // 纹理采样高度 1016
                ShippingBoxLayout.TEXTURE_WIDTH,  // PNG 真实宽度 780
                ShippingBoxLayout.TEXTURE_HEIGHT  // PNG 真实高度 1016
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
