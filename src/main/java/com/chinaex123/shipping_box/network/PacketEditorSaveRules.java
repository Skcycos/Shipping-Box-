package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public record PacketEditorSaveRules(String requestId, String relativePath, String rulesJson) implements CustomPacketPayload {
    public static final Type<PacketEditorSaveRules> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_save_rules")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorSaveRules> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                try {
                    buf.writeUtf(packet.requestId());
                    buf.writeUtf(packet.relativePath());
                    buf.writeByteArray(compress(packet.rulesJson));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to compress editor rules", e);
                }
            },
            (buf) -> {
                try {
                    String requestId = buf.readUtf();
                    String relativePath = buf.readUtf();
                    byte[] compressed = buf.readByteArray();
                    String json = decompress(compressed);
                    return new PacketEditorSaveRules(requestId, relativePath, json);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to decompress editor rules", e);
                }
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorSaveRules packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            JsonObject obj;
            try {
                obj = JsonParser.parseString(packet.rulesJson()).getAsJsonObject();
            } catch (JsonParseException e) {
                context.player().displayClientMessage(Component.literal("Invalid JSON"), false);
                return;
            } catch (Exception e) {
                context.player().displayClientMessage(Component.literal("Invalid payload"), false);
                return;
            }

            if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
                context.player().displayClientMessage(Component.literal("Expected {\"rules\": [...]}"), false);
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new PacketEditorSaveRulesResult(packet.requestId(), false, "", "Expected {\"rules\": [...]}"));
                }
                return;
            }

            try {
                Path base = FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules").normalize();
                Files.createDirectories(base);
                String rel = packet.relativePath() == null || packet.relativePath().isBlank() ? "editor.json" : packet.relativePath();
                if (!rel.endsWith(".json")) {
                    rel = rel + ".json";
                }
                Path file = base.resolve(rel).normalize();
                if (!file.startsWith(base)) {
                    throw new IllegalArgumentException("Invalid path");
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, packet.rulesJson(), StandardCharsets.UTF_8);
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new PacketEditorSaveRulesResult(packet.requestId(), true, file.toString(), ""));
                }
            } catch (Exception e) {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new PacketEditorSaveRulesResult(packet.requestId(), false, "", e.getMessage()));
                }
            }
        }).exceptionally(e -> null);
    }

    private static byte[] compress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        ByteArrayInputStream in = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(in)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
