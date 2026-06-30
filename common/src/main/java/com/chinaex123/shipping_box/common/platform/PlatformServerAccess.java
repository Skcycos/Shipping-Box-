package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public class PlatformServerAccess {
    @ExpectPlatform
    @Nullable
    public static MinecraftServer getCurrentServer() {
        throw new AssertionError();
    }
}
