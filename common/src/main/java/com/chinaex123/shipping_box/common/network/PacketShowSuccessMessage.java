package com.chinaex123.shipping_box.common.network;

import com.chinaex123.shipping_box.common.ShippingBoxCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketShowSuccessMessage() implements CustomPacketPayload {
    public static final Type<PacketShowSuccessMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBoxCommon.MOD_ID, "show_success_message")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketShowSuccessMessage> STREAM_CODEC =
            StreamCodec.unit(new PacketShowSuccessMessage());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
