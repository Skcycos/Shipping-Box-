package com.chinaex123.shipping_box.common.platform.neoforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PlatformSavedData {
    public static <T extends SavedData> SavedData.Factory<T> createFactory(
            Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer
    ) {
        return new SavedData.Factory<>(constructor, deserializer);
    }
}
