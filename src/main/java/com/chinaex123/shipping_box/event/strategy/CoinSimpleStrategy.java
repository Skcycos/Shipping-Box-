package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CoinSimpleStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        // 普通虚拟货币模式：使用固定数量
        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
        // 应用属性加成
        int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}
