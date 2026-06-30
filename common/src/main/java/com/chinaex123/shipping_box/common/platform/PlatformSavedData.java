package com.chinaex123.shipping_box.common.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.level.saveddata.SavedData;

public class PlatformSavedData {
    @ExpectPlatform
    public static <T extends SavedData> SavedData.Factory<T> createFactory(
            java.util.function.Supplier<T> constructor,
            java.util.function.BiFunction<net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup.Provider, T> deserializer
    ) {
        throw new AssertionError();
    }
}
