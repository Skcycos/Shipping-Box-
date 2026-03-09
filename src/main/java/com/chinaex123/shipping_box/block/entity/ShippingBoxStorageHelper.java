package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.PlayerStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ShippingBoxStorageHelper {

    private final ShippingBoxBlockEntity blockEntity;
    private final PlayerStorageManager playerStorageManager;

    public ShippingBoxStorageHelper(ShippingBoxBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.playerStorageManager = PlayerStorageManager.getInstance();
    }

    /**
     * 收集所有需要处理的物品
     */
    public Map<UUID, List<ItemStack>> collectItemsToProcess(NonNullList<ItemStack> sharedItems, Map<Integer, UUID> slotOwners) {
        Map<UUID, List<ItemStack>> playerItemsToProcess = new HashMap<>();

        // 收集共享存储的物品
        for (int i = 0; i < sharedItems.size(); i++) {
            ItemStack stack = sharedItems.get(i);
            if (!stack.isEmpty()) {
                UUID owner = slotOwners.get(i);
                if (owner != null) {
                    playerItemsToProcess.computeIfAbsent(owner, k -> new ArrayList<>()).add(stack.copy());
                }
            }
        }

        // 收集玩家独立存储的物品
        for (UUID playerUUID : playerStorageManager.getAllPlayerUUIDs()) {
            NonNullList<ItemStack> playerItems = playerStorageManager.getPlayerStorage(playerUUID);
            for (ItemStack stack : playerItems) {
                if (!stack.isEmpty()) {
                    playerItemsToProcess.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(stack.copy());
                }
            }
        }

        return playerItemsToProcess;
    }

    /**
     * 清空所有存储
     */
    public void clearAllStorage(NonNullList<ItemStack> sharedItems) {
        Collections.fill(sharedItems, ItemStack.EMPTY);
        playerStorageManager.clearAllStorages();
    }

    /**
     * 执行批量兑换
     */
    public Set<UUID> processExchange(Map<UUID, List<ItemStack>> playerItemsToProcess, ServerLevel serverLevel, BlockPos worldPosition) {
        Map<UUID, List<ItemStack>> playerResults = new HashMap<>();
        Set<UUID> successfulPlayers = new HashSet<>();

        for (Map.Entry<UUID, List<ItemStack>> entry : playerItemsToProcess.entrySet()) {
            UUID playerUUID = entry.getKey();
            List<ItemStack> items = entry.getValue();

            // 使用共享的兑换逻辑
            NonNullList<ItemStack> tempStorage = NonNullList.withSize(54, ItemStack.EMPTY);
            // 将物品复制到临时存储
            for (int i = 0; i < Math.min(items.size(), tempStorage.size()); i++) {
                tempStorage.set(i, items.get(i).copy());
            }

            // 执行兑换
            ExchangeManager.performExchange(tempStorage, serverLevel, worldPosition, playerUUID);

            // 收集结果
            List<ItemStack> results = new ArrayList<>();
            for (ItemStack stack : tempStorage) {
                if (!stack.isEmpty()) {
                    results.add(stack.copy());
                }
            }

            if (!results.isEmpty()) {
                playerResults.put(playerUUID, results);
                successfulPlayers.add(playerUUID);
            }
        }

        // 分配结果
        distributeResults(playerResults);
        
        return successfulPlayers;
    }

    /**
     * 分配兑换结果回玩家存储
     */
    private void distributeResults(Map<UUID, List<ItemStack>> playerResults) {
        for (Map.Entry<UUID, List<ItemStack>> resultEntry : playerResults.entrySet()) {
            UUID playerUUID = resultEntry.getKey();
            List<ItemStack> results = resultEntry.getValue();

            NonNullList<ItemStack> playerStorage = playerStorageManager.getPlayerStorage(playerUUID);
            int slotIndex = 0;

            // 遍历该玩家的所有结果物品
            for (ItemStack result : results) {
                // 如果存储已满，停止分配
                if (slotIndex >= playerStorage.size()) {
                    break;
                }

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                // 将大堆叠物品分割成标准堆叠大小
                while (remainingCount > 0 && slotIndex < playerStorage.size()) {
                    // 寻找空槽位放置物品
                    if (playerStorage.get(slotIndex).isEmpty()) {
                        int stackSize = Math.min(remainingCount, maxStackSize);
                        ItemStack newStack = result.copy();
                        newStack.setCount(stackSize);
                        playerStorage.set(slotIndex, newStack);
                        remainingCount -= stackSize;
                    }
                    slotIndex++;
                }
            }
        }
    }

    /**
     * 通知成功的玩家
     */
    public void notifySuccessfulPlayers(ServerLevel serverLevel, Set<UUID> successfulPlayers, BlockPos worldPosition) {
        if (!successfulPlayers.isEmpty()) {
            serverLevel.playSound(null, worldPosition,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            for (UUID playerUUID : successfulPlayers) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    // 向特定玩家播放声音（无视距离）
                    player.playNotifySound(
                            SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                            SoundSource.BLOCKS,
                            0.5F,
                            1.0F
                    );

                    // 发送个性化的成功消息
                    player.displayClientMessage(Component.translatable("message.shipping_box.exchange_success"), true);
                }
            }
        }
    }
}
