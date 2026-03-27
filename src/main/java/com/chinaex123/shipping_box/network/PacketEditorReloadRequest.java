package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketEditorReloadRequest() implements CustomPacketPayload {
    public static final Type<PacketEditorReloadRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_reload_request")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorReloadRequest> STREAM_CODEC =
            StreamCodec.unit(new PacketEditorReloadRequest());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorReloadRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                context.player().getServer().execute(() -> {
                    try {
                        context.player().getServer().getCommands().performPrefixedCommand(
                                context.player().getServer().createCommandSourceStack(),
                                "reload"
                        );
                    } catch (Exception ignored) {}
                });
                context.player().displayClientMessage(Component.literal("Reload queued"), false);
            } catch (Exception e) {
                context.player().displayClientMessage(Component.literal("Failed to queue reload"), false);
            }
        }).exceptionally(e -> null);
    }
}
