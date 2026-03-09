package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 配方同步数据包记录类
 * 用于将服务端的兑换配方同步到客户端
 *
 * @param rulesJson 配方规则列表的JSON字符串表示
 */
public record PacketSyncRecipes(String rulesJson) implements CustomPacketPayload {
    public static final Type<PacketSyncRecipes> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sync_recipes")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketSyncRecipes> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PacketSyncRecipes::rulesJson,
                    PacketSyncRecipes::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketSyncRecipes packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                // 在客户端设置配方规则
                // 注意：ExchangeRecipeManager 可能包含客户端逻辑，应确保只在客户端调用
                ExchangeRecipeManager.setClientRules(packet.rulesJson());
            } catch (Exception e) {
                // 静默处理同步错误
            }
        }).exceptionally(e -> null);
    }
}
