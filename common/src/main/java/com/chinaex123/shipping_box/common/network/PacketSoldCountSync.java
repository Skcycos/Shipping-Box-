package com.chinaex123.shipping_box.common.network;

import com.chinaex123.shipping_box.common.ShippingBoxCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketSoldCountSync(String itemIdentifier, int soldCount) implements CustomPacketPayload {
    public static final Type<PacketSoldCountSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBoxCommon.MOD_ID, "sold_count_sync")
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
}
