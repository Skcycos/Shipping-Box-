package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record PacketEditorReadFile(String requestId, String relativePath) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEditorReadFile.class);

    public static final Type<PacketEditorReadFile> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorReadFile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFile::requestId,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFile::relativePath,
            PacketEditorReadFile::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorReadFile packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String content = "{\"rules\":[]}";
            String error = null;
            boolean ok = true;

            try {
                Path base = getBaseDir();
                Files.createDirectories(base);
                String rel = packet.relativePath() == null || packet.relativePath().isBlank() ? "editor.json" : packet.relativePath();
                if (!rel.endsWith(".json")) {
                    rel = rel + ".json";
                }
                Path relPath = Path.of(rel).normalize();
                Path target = base.resolve(relPath).normalize();
                if (relPath.isAbsolute() || !target.startsWith(base)) {
                    ok = false;
                    error = "Invalid path";
                    LOGGER.warn("Web editor read blocked by path validation. base={}, rel={}", base, rel);
                } else if (Files.exists(target) && Files.isRegularFile(target)) {
                    content = Files.readString(target, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                ok = false;
                error = e.getMessage();
            }

            if (context.player() instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new PacketEditorReadFileResult(packet.requestId(), ok, content, error == null ? "" : error)
                );
            }
        }).exceptionally(e -> null);
    }

    private static Path getBaseDir() {
        if (ModList.get().isLoaded("kubejs")) {
            return FMLPaths.GAMEDIR.get().resolve("kubejs/data/shipping_box/exchange_rules").normalize();
        }
        return FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules").normalize();
    }
}
