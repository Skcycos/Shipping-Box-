package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class PlatformNetworking {
    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToAllPlayers(CustomPacketPayload packet) {
        throw new AssertionError();
    }
}
