package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.api.ShippingBoxAPI;
import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.event.strategy.ExchangeStrategy;
import com.chinaex123.shipping_box.event.strategy.ExchangeStrategyFactory;
import com.chinaex123.shipping_box.compat.EclipticSeasons.EclipticSeasonsUtil;
import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.network.PacketExchangeEffects;
import com.chinaex123.shipping_box.network.PacketShowSuccessMessage;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class ExchangeManager {

    /**
     * 执行物品兑换的核心逻辑
     * @param items 物品存储列表
     * @param level 世界实例
     * @param blockPos 方块位置
     * @param boundPlayerUUID 绑定的玩家 UUID（可为 null）
     */
    public static void performExchange(NonNullList<ItemStack> items, Level level, BlockPos blockPos, UUID boundPlayerUUID) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> initialItems = new ArrayList<>();
        NonNullList<ItemStack> initialSnapshot = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        List<ItemStack> currentItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            initialSnapshot.set(i, stack.copy());
            if (!stack.isEmpty()) {
                initialItems.add(stack.copy());
                currentItems.add(stack.copy());
            }
        }

        if (currentItems.isEmpty()) {
            return;
        }

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged;
        int totalVirtualCurrency = 0;
        boolean hasValidExchange = false;
        ExchangeRule lastMatchedRule = null;

        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRuleParser.findMatchingRule(ExchangeRecipeManager.getRules(), currentItems);

            if (rule != null) {
                lastMatchedRule = rule;

                if (rule.getOutputItem().isCoin() && !ViScriptShopUtil.isAvailable()) {
                    return;
                }

                if (rule.getOutputItem().getEclipticSeasonsProperties() != null) {
                    var ecsProps = rule.getOutputItem().getEclipticSeasonsProperties();
                    if (ecsProps.isSeasonal_only() &&
                            !EclipticSeasonsUtil.isInSeasons(level, ecsProps.getSeason())) {
                        break;
                    }
                }

                int maxExchanges = ExchangeCalculator.getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRuleParser.consumeInputs(rule, currentItems);
                    }

                    ExchangeStrategy strategy = ExchangeStrategyFactory.getStrategy(rule);
                    AtomicInteger currencyWrapper = new AtomicInteger(totalVirtualCurrency);
                    strategy.execute(rule, maxExchanges, level, boundPlayerUUID, results, currencyWrapper);
                    totalVirtualCurrency = currencyWrapper.get();

                    exchanged = true;
                    hasValidExchange = true;
                }
            }
        } while (exchanged);

        if (hasValidExchange) {
            List<ItemStack> consumedItems = ExchangeCalculator.calculateConsumedItems(initialItems, currentItems);

            if (ShippingBoxAPI.onExchange(
                    null,
                    level,
                    ExchangeCalculator.createNonNullList(consumedItems),
                    ExchangeCalculator.createNonNullList(results),
                    totalVirtualCurrency,
                    lastMatchedRule)) {
                for (int i = 0; i < items.size(); i++) {
                    items.set(i, initialSnapshot.get(i).copy());
                }
                return;
            }

            if (totalVirtualCurrency > 0 && boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null && ViScriptShopUtil.isAvailable()) {
                    int currentBalance = ViScriptShopUtil.getMoney(player);
                    startBalanceAnimation(player, currentBalance, totalVirtualCurrency, 1);
                    ViScriptShopUtil.addMoney(player, totalVirtualCurrency);
                }
            }

            results.addAll(currentItems);

            List<ItemStack> stackedResults = new ArrayList<>();
            for (ItemStack stack : results) {
                if (stack.isEmpty()) continue;

                boolean merged = false;
                for (ItemStack existingStack : stackedResults) {
                    if (ItemStack.isSameItemSameComponents(existingStack, stack)) {
                        int maxStackSize = existingStack.getMaxStackSize();
                        int spaceAvailable = maxStackSize - existingStack.getCount();
                        int amountToMerge = Math.min(stack.getCount(), spaceAvailable);

                        if (amountToMerge > 0) {
                            existingStack.grow(amountToMerge);
                            stack.shrink(amountToMerge);
                            merged = true;

                            if (stack.isEmpty()) {
                                break;
                            }
                        }
                    }
                }

                if (!stack.isEmpty()) {
                    stackedResults.add(stack);
                }
            }

            Collections.fill(items, ItemStack.EMPTY);
            int slotIndex = 0;

            for (ItemStack result : stackedResults) {
                if (slotIndex >= items.size()) break;

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                while (remainingCount > 0 && slotIndex < items.size()) {
                    if (items.get(slotIndex).isEmpty()) {
                        int stackSize = Math.min(remainingCount, maxStackSize);
                        ItemStack newStack = result.copy();
                        newStack.setCount(stackSize);
                        items.set(slotIndex, newStack);
                        remainingCount -= stackSize;
                    }
                    slotIndex++;
                }
            }

            serverLevel.playSound(null, blockPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            if (boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new PacketShowSuccessMessage());

                    if (CommonConfig.ENABLE_EXCHANGE_EFFECTS.get()) {
                        PacketDistributor.sendToPlayer(player, new PacketExchangeEffects(totalVirtualCurrency));
                    }
                }
            }

            if (CommonConfig.ENABLE_TRANSACTION_LOGGING.get()) {
                String playerName = "Unknown";
                if (boundPlayerUUID != null) {
                    ServerPlayer logPlayer = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                    if (logPlayer != null) {
                        playerName = logPlayer.getName().getString();
                    }
                }
                TransactionLogger.logTransaction(playerName, consumedItems, results, totalVirtualCurrency, level, lastMatchedRule);
            }
        }
    }
    /**
     * 开始余额动画
     */
    private static void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        BalanceAnimationManager.startAnimation(player, startBalance, totalValue, exchangeAmount);
    }

    public static int applySellingPriceBoost(int baseCount, ExchangeRule rule, Level level, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer() != null ?
                level.getServer().getPlayerList().getPlayer(playerUUID) : null;
        if (player == null) {
            return baseCount;
        }

        int enhancedCount = baseCount;

        try {
            // 应用玩家属性加成
            double boost = player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);

            if (boost != 0.0) {
                // 应用加成：基础数量 × (1 + 加成系数)
                double enhancedAmount = enhancedCount * (1.0 + boost);

                // 处理负数情况：如果结果小于 0，返回 0
                if (enhancedAmount < 0) {
                    enhancedCount = 0;
                } else {
                    // 智能取整：小于等于 5 向下取整，大于 5 向上取整
                    if (enhancedAmount <= 5.0) {
                        enhancedCount = (int) Math.floor(enhancedAmount);
                    } else {
                        enhancedCount = (int) Math.ceil(enhancedAmount);
                    }
                }
            }

            // 如果有兑换规则，检查是否需要应用节气加成/减益
            if (rule != null && rule.getOutputItem() != null &&
                    rule.getOutputItem().getEclipticSeasonsProperties() != null) {

                var ecsProps = rule.getOutputItem().getEclipticSeasonsProperties();

                // 检查节气模组是否可用
                if (EclipticSeasonsUtil.isAvailable()) {
                    // 获取当前季节并检查是否在目标季节列表中
                    boolean isInSeason = EclipticSeasonsUtil.isInSeasons(level, ecsProps.getSeason());

                    if (isInSeason) {
                        // 应季：应用加成（价格更高）
                        int bonus = ecsProps.getAdd_season_bonus();
                        if (bonus > 0) {
                            double seasonEnhancedAmount = enhancedCount * (1.0 + (bonus / 100.0));
                            enhancedCount = (int) Math.ceil(seasonEnhancedAmount);
                        }
                    } else {
                        // 非应季：应用减益（价格更低）
                        int penalty = ecsProps.getReduce_season_bonus();
                        if (penalty > 0) {
                            double reducedAmount = enhancedCount * (1.0 - (penalty / 100.0));
                            enhancedCount = Math.max(0, (int) Math.floor(reducedAmount));
                        }
                    }
                }
            }

            return enhancedCount;
        } catch (Exception e) {
            return baseCount;
        }
    }
}
