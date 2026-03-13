package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AutoShippingBoxMenu extends ChestMenu {
    private final AutoShippingBoxBlockEntity blockEntity;
    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(MenuType.GENERIC_9x6, id, playerInventory, blockEntity, 6);
        this.blockEntity = blockEntity;
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
    }

    /**
     * 快速移动物品堆（Shift 点击）
     * <p>
     * 实现玩家 Shift 点击物品时在容器和玩家背包之间的快速转移：
     * - 从自动售货箱槽位（0-53）→ 移动到玩家背包（54-末尾）
     * - 从玩家背包槽位（54-末尾）→ 移动到自动售货箱（0-53）
     * 
     * @param player 执行操作的玩家
     * @param index 被点击的槽位索引
     * @return 原始的物品堆副本，如果无法移动则返回 ItemStack.EMPTY
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        // 获取被点击的槽位
        Slot slot = this.slots.get(index);
        
        // 如果槽位为空，直接返回空
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        // 获取槽位中的物品及其副本
        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();

        // 判断物品来源并移动到对应区域
        if (index < 54) {
            // 物品来自自动售货箱（前 54 个槽位）
            // 尝试移动到玩家背包（从第 54 个槽位开始到末尾）
            // 参数 true 表示从后向前检查可用槽位
            if (!this.moveItemStackTo(itemstack, 54, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 物品来自玩家背包（54 号槽位之后）
            // 尝试移动到自动售货箱（从第 0 个槽位到第 53 个槽位）
            // 参数 false 表示从前向后检查可用槽位
            if (!this.moveItemStackTo(itemstack, 0, 54, false)) {
                return ItemStack.EMPTY;
            }
        }

        // 根据物品是否全部移走来更新槽位
        if (itemstack.isEmpty()) {
            // 物品已全部移走，清空槽位（通过玩家操作设置）
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            // 物品还有剩余，标记槽位已更改
            slot.setChanged();
        }

        // 返回原始物品的副本
        return itemstack1;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (storedLevel != null && !storedLevel.isClientSide && player instanceof ServerPlayer) {
            storedLevel.playSound(
                    null,
                    storedPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS,
                    0.5F,
                    storedLevel.random.nextFloat() * 0.1F + 0.9F
            );
        }
    }

    public AutoShippingBoxBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
