package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketOpenWebEditor(String url) implements CustomPacketPayload {
    public static final Type<PacketOpenWebEditor> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "open_web_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketOpenWebEditor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketOpenWebEditor::url,
            PacketOpenWebEditor::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketOpenWebEditor packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.url() == null || packet.url().isBlank()) {
                return;
            }
            try {
                Util.getPlatform().openUri(packet.url());
            } catch (Exception e) {
                if (context.player() != null) {
                    context.player().displayClientMessage(
                            Component.literal("Failed to open URL: " + packet.url()),
                            false
                    );
                }
            }
        }).exceptionally(e -> null);
    }
}
