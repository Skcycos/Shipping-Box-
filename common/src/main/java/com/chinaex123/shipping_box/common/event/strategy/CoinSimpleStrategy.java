package com.chinaex123.shipping_box.common.event.strategy;

import com.chinaex123.shipping_box.common.event.ExchangeManager;
import com.chinaex123.shipping_box.common.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CoinSimpleStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
        int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}
