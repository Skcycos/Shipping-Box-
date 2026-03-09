package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 销售计数同步数据包
 * @param itemIdentifier 物品标识符
 * @param soldCount 销售数量
 */
public record PacketSoldCountSync(String itemIdentifier, int soldCount) implements CustomPacketPayload {
    public static final Type<PacketSoldCountSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sold_count_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketSoldCountSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketSoldCountSync::itemIdentifier,
            ByteBufCodecs.INT, PacketSoldCountSync::soldCount,
            PacketSoldCountSync::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketSoldCountSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端更新销售计数缓存
            // 注意：ClientSoldCountCache 是客户端类，应该只在客户端访问
            // 但这里 handle 方法是在网络线程中调用的，且上下文保证了环境
            // 为了安全起见，通常建议将客户端逻辑隔离
            ClientSoldCountCache.updateCache(packet.itemIdentifier, packet.soldCount);
        });
    }
}
