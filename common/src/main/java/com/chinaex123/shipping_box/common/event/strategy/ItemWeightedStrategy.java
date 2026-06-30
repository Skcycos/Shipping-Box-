package com.chinaex123.shipping_box.common.event.strategy;

import com.chinaex123.shipping_box.common.event.ExchangeManager;
import com.chinaex123.shipping_box.common.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ItemWeightedStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        for (int i = 0; i < maxExchanges; i++) {
            ItemStack weightedOutput = rule.getOutputItem().getRandomWeightedItem();
            if (!weightedOutput.isEmpty()) {
                int baseCount = weightedOutput.getCount();
                int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
                weightedOutput.setCount(enhancedCount);
                results.add(weightedOutput);
            }
        }
    }
}
