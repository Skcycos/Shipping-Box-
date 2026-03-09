package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.modCompat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.chinaex123.shipping_box.ModConfig;
import com.chinaex123.shipping_box.network.PacketExchangeEffects;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.chinaex123.shipping_box.event.strategy.ExchangeStrategy;
import com.chinaex123.shipping_box.event.strategy.ExchangeStrategyFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

public class ExchangeManager {

    /**
     * 执行物品兑换的核心逻辑
     * @param items 物品存储列表
     * @param level 世界实例
     * @param blockPos 方块位置
     * @param boundPlayerUUID 绑定的玩家UUID（可为null）
     */
    public static void performExchange(NonNullList<ItemStack> items, Level level, BlockPos blockPos, UUID boundPlayerUUID) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> currentItems = new ArrayList<>();
        List<ItemStack> initialItems = new ArrayList<>(); // 记录初始物品用于日志
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                currentItems.add(stack.copy());
                initialItems.add(stack.copy());
            }
        }

        if (currentItems.isEmpty()) {
            return;
        }

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged;
        int totalVirtualCurrency = 0;
        boolean hasValidExchange = false;

        // 执行兑换逻辑
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(currentItems);

            if (rule != null) {
                // 检查如果是虚拟货币兑换但模组未加载，则完全跳过处理
                if (rule.getOutputItem().isCoin() && !ViScriptShopUtil.isAvailable()) {
                    return;
                }

                int maxExchanges = getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    // 消耗输入物品
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRecipeManager.consumeInputs(rule, currentItems);
                    }

                    // 执行兑换策略
                    ExchangeStrategy strategy = ExchangeStrategyFactory.getStrategy(rule);
                    AtomicInteger currencyWrapper = new AtomicInteger(totalVirtualCurrency);
                    strategy.execute(rule, maxExchanges, level, boundPlayerUUID, results, currencyWrapper);
                    totalVirtualCurrency = currencyWrapper.get();

                    exchanged = true;
                    hasValidExchange = true;
                }
            }
        } while (exchanged);

        // 如果有虚拟货币要添加
        if (totalVirtualCurrency > 0 && boundPlayerUUID != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
            if (player != null && ViScriptShopUtil.isAvailable()) {
                // 获取玩家当前余额并开始动画
                int currentBalance = ViScriptShopUtil.getMoney(player);
                startBalanceAnimation(player, currentBalance, totalVirtualCurrency, 1);

                ViScriptShopUtil.addMoney(player, totalVirtualCurrency);
            }
        }

        // 只有当确实有有效兑换发生时才处理物品和播放音效
        if (hasValidExchange) {
            // 计算实际消耗的物品
            List<ItemStack> consumedItems = calculateConsumedItems(initialItems, currentItems);
            
            // 记录交易日志
            String playerName = "Unknown";
            if (boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null) {
                    playerName = player.getName().getString();
                }
            }
            TransactionLogger.logTransaction(boundPlayerUUID, playerName, consumedItems, results, totalVirtualCurrency, level);

            // 添加剩余物品
            results.addAll(currentItems);

            // 统一堆叠处理 - 合并相同物品
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

            // 清空并重新填充
            Collections.fill(items, ItemStack.EMPTY);
            int slotIndex = 0;

            for (ItemStack result : stackedResults) {
                if (slotIndex >= items.size()) break;

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                // 遍历所有槽位，将物品按最大堆叠数分配到各个槽位中
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

            // 播放成功音效
            serverLevel.playSound(null, blockPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            // 发送兑换成功消息到绑定玩家
            if (boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null) {
                    // 1. 发送配置的聊天消息
                    String line1 = ModConfig.COMMON.successMessageLine1.get();
                    if (line1 != null && !line1.isEmpty()) {
                        player.sendSystemMessage(Component.literal(line1));
                    }
                    
                    String line2 = ModConfig.COMMON.successMessageLine2.get();
                    if (line2 != null && !line2.isEmpty()) {
                        line2 = line2.replace("{amount}", String.valueOf(totalVirtualCurrency));
                        player.sendSystemMessage(Component.literal(line2));
                    }
                    
                    String line3 = ModConfig.COMMON.successMessageLine3.get();
                    if (line3 != null && !line3.isEmpty()) {
                        long totalBalance = 0;
                        if (ViScriptShopUtil.isAvailable()) {
                            totalBalance = ViScriptShopUtil.getMoney(player);
                        }
                        line3 = line3.replace("{total}", String.valueOf(totalBalance));
                        player.sendSystemMessage(Component.literal(line3));
                    }
                    
                    // 2. 发送特效数据包
                    PacketDistributor.sendToPlayer(player, new PacketExchangeEffects(totalVirtualCurrency));
                    
                    // 3. (可选) 仍发送旧的成功提示消息包，如果客户端仍需要它显示ActionBar消息
                    // 如果不需要旧的ActionBar提示，可以注释掉下面这行
                    // ShippingBoxNetworking.ShowSuccessMessage successPacket = new ShippingBoxNetworking.ShowSuccessMessage();
                    // PacketDistributor.sendToPlayer(player, successPacket);
                }
            }
        }
    }

    /**
     * 开始余额动画
     */
    private static void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        BalanceAnimationManager.startAnimation(player, startBalance, totalValue, exchangeAmount);
    }

    /**
     * 应用玩家出售价格属性加成到基础数量
     * <p>
     * 根据玩家的出售价格属性加成值，计算增强后的物品数量。
     * 采用智能取整策略：小数量向下取整保证平衡，大数量向上取整激励玩家。
     *
     * @param baseCount 基础物品数量
     * @param level 游戏世界实例，用于获取服务器和玩家信息
     * @param playerUUID 玩家唯一标识符
     * @return 应用属性加成后的最终物品数量
     */
    public static int applySellingPriceBoost(int baseCount, Level level, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer() != null ?
                level.getServer().getPlayerList().getPlayer(playerUUID) : null;
        if (player == null) {
            return baseCount; // 玩家不在线或服务器不可用，返回基础数量
        }

        try {
            // 获取该玩家的出售价格属性加成
            double boost = player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);

            if (boost <= 0.0) {
                return baseCount;
            }

            // 应用加成：基础数量 × (1 + 加成系数)
            double enhancedAmount = baseCount * (1.0 + boost);

            // 智能取整：小于等于5向下取整，大于5向上取整
            int result;
            if (enhancedAmount <= 5.0) {
                result = (int) Math.floor(enhancedAmount);
            } else {
                result = (int) Math.ceil(enhancedAmount);
            }

            return result;
        } catch (Exception e) {
            return baseCount;
        }
    }

    /**
     * 计算指定兑换规则可以执行的最大兑换次数
     * <p>
     * 通过检查每种输入物品的可用数量来确定限制因素，返回能够完成的最多兑换轮数。
     * 算法找出所有必需物品中最紧缺的那种，以其可支持的兑换次数作为整体上限。
     *
     * @param rule 兑换规则，包含所需的输入物品列表及其数量要求
     * @param availableStacks 当前可用的物品堆列表
     * @return 可以执行的最大兑换次数，如果无法兑换则返回0
     */
    public static int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }

    /**
     * 计算实际消耗的物品
     */
    private static List<ItemStack> calculateConsumedItems(List<ItemStack> initialItems, List<ItemStack> remainingItems) {
        List<ItemStack> consumed = new ArrayList<>();
        
        // 创建剩余物品的深拷贝列表，用于模拟扣除过程
        List<ItemStack> remainingCopy = new ArrayList<>();
        for (ItemStack stack : remainingItems) {
            remainingCopy.add(stack.copy());
        }

        for (ItemStack initStack : initialItems) {
            ItemStack stackToProcess = initStack.copy();
            int originalCount = stackToProcess.getCount();
            int currentRemaining = originalCount;
            
            // 在剩余物品中寻找匹配项并扣除
            for (int i = 0; i < remainingCopy.size(); i++) {
                ItemStack remStack = remainingCopy.get(i);
                if (ItemStack.isSameItemSameComponents(stackToProcess, remStack)) {
                    int deduct = Math.min(currentRemaining, remStack.getCount());
                    currentRemaining -= deduct;
                    remStack.shrink(deduct);
                    
                    if (remStack.isEmpty()) {
                        remainingCopy.remove(i);
                        i--;
                    }
                    
                    if (currentRemaining <= 0) break;
                }
            }
            
            // 消耗量 = 原始数量 - 剩余未匹配数量
            int consumedCount = originalCount - currentRemaining;
            
            if (consumedCount > 0) {
                ItemStack consumedStack = stackToProcess.copy();
                consumedStack.setCount(consumedCount);
                consumed.add(consumedStack);
            }
        }
        
        // 合并相同的消耗物品
        List<ItemStack> mergedConsumed = new ArrayList<>();
        for (ItemStack stack : consumed) {
            boolean merged = false;
            for (ItemStack existing : mergedConsumed) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                mergedConsumed.add(stack);
            }
        }
        
        return mergedConsumed;
    }
}
