package com.chinaex123.shipping_box.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 将 ItemStack 渲染为 PNG 字节数组。
 * 当前实现复用 WebEditorLocalServer 的图标提取（产生正确的库存图标外观）。
 * 未来可替换为真正的 GL framebuffer 渲染。
 */
public class ItemIconPngRenderer {

    public static final int DEFAULT_SIZE = 32;

    public static byte[] renderStackToPng(ItemStack stack, int size) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        try {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) return null;

            // 复用现有的可靠图标提取逻辑
            return com.chinaex123.shipping_box.web.WebEditorLocalServer.extractIconForItemAsBytes(id);
        } catch (Exception e) {
            return null;
        }
    }
}