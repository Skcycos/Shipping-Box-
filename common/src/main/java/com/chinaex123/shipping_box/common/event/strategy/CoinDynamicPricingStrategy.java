package com.chinaex123.shipping_box.common.event.strategy;

import com.chinaex123.shipping_box.common.event.DynamicPricingManager;
import com.chinaex123.shipping_box.common.event.ExchangeManager;
import com.chinaex123.shipping_box.common.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CoinDynamicPricingStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        String itemIdentifier = rule.getInputs().getFirst().getItem();
        int resetDay = rule.getOutputItem().getDynamicProperties().getDay();
        int currentSoldCount = DynamicPricingManager.getSoldCount(itemIdentifier, resetDay);

        int totalVirtualCurrencyCount = 0;
        int itemsToProcess = maxExchanges;

        for (int i = 0; i < itemsToProcess; i++) {
            int dynamicCount = rule.getOutputItem().getDynamicCount(currentSoldCount + i);
            totalVirtualCurrencyCount += dynamicCount;
        }

        DynamicPricingManager.addSoldCount(itemIdentifier, itemsToProcess, resetDay);

        int enhancedCount = ExchangeManager.applySellingPriceBoost(totalVirtualCurrencyCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}
