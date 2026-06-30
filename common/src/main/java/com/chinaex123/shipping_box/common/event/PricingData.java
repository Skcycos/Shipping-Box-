package com.chinaex123.shipping_box.common.event;

import com.chinaex123.shipping_box.common.platform.PlatformServerAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class PricingData extends SavedData {

    public static PricingData createNew() {
        return new PricingData();
    }

    public static PricingData loadFromNBT(CompoundTag tag) {
        PricingData pricingData = new PricingData();
        pricingData.loadDataFromNBT(tag);
        return pricingData;
    }

    private final Map<String, Integer> data = new HashMap<>();
    private Map<String, Long> lastSaleDays = new HashMap<>();

    public PricingData() {
        super();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag dataTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            dataTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("sales_data", dataTag);

        CompoundTag dayTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : lastSaleDays.entrySet()) {
            dayTag.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("sale_days", dayTag);

        return tag;
    }

    public void loadDataFromNBT(CompoundTag tag) {
        if (tag.contains("sales_data")) {
            CompoundTag dataTag = tag.getCompound("sales_data");
            for (String key : dataTag.getAllKeys()) {
                data.put(key, dataTag.getInt(key));
            }
        }

        if (tag.contains("sale_days")) {
            CompoundTag dayTag = tag.getCompound("sale_days");
            for (String key : dayTag.getAllKeys()) {
                lastSaleDays.put(key, dayTag.getLong(key));
            }
        }
    }

    public Map<String, Integer> getData() {
        return data;
    }

    public void setData(Map<String, Integer> newData) {
        data.clear();
        data.putAll(newData);
        setDirty();
    }

    public void addCount(String item, int count) {
        int current = data.getOrDefault(item, 0);
        data.put(item, current + count);
        recordSaleDay(item);
        setDirty();
    }

    public void resetCount(String item) {
        data.put(item, 0);
        lastSaleDays.remove(item);
        setDirty();
    }

    public int getCount(String item) {
        return data.getOrDefault(item, 0);
    }

    public boolean shouldResetCount(String item, int resetDay) {
        Long lastSaleDay = lastSaleDays.get(item);

        if (lastSaleDay == null) {
            recordSaleDay(item);
            return false;
        }

        long currentDay = getCurrentGameDay();
        long daysPassed = currentDay - lastSaleDay;

        return daysPassed >= resetDay;
    }

    public void recordSaleDay(String item) {
        long currentDay = getCurrentGameDay();
        lastSaleDays.put(item, currentDay);
    }

    private long getCurrentGameDay() {
        MinecraftServer server = PlatformServerAccess.getCurrentServer();
        if (server != null) {
            return server.overworld().getDayTime() / 24000L;
        }
        return 0;
    }

    public int getDaysSinceLastSale(String item) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) {
            return -1;
        }

        long currentDay = getCurrentGameDay();
        return (int) (currentDay - lastSaleDay);
    }

    public int getResetRemainingDays(String item, int resetDay) {
        int daysPassed = getDaysSinceLastSale(item);
        if (daysPassed == -1) {
            return resetDay;
        }
        return resetDay - daysPassed;
    }

    public void recordResetDay(String itemIdentifier, long day) {
        lastSaleDays.put(itemIdentifier, day);
        setDirty();
    }

    public Long getLastResetDay(String itemIdentifier) {
        return lastSaleDays.get(itemIdentifier);
    }
}
