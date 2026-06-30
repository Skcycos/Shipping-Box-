package com.chinaex123.shipping_box.common.platform.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlatformNetworking {
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToAllPlayers(CustomPacketPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }
}
