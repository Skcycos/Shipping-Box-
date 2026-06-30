package com.chinaex123.shipping_box.common.platform.fabric;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public class PlatformServerAccess {
    private static MinecraftServer server;

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return server;
    }

    public static void setServer(MinecraftServer server) {
        PlatformServerAccess.server = server;
    }
}
