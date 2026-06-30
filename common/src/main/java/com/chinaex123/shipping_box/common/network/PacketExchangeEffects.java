package com.chinaex123.shipping_box.common.network;

import com.chinaex123.shipping_box.common.ShippingBoxCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketExchangeEffects(int amount) implements CustomPacketPayload {
    public static final Type<PacketExchangeEffects> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBoxCommon.MOD_ID, "exchange_effects")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketExchangeEffects> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PacketExchangeEffects::amount,
                    PacketExchangeEffects::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
