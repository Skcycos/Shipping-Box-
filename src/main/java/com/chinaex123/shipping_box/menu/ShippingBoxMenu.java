package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/**
 * 普通售货箱菜单。继承 AbstractContainerMenu，构造时直接按 ShippingBoxLayout 坐标 addSlot，
 * 不依赖 ChestMenu 的默认布局，不 clear，不 rebuild。
 */
public class ShippingBoxMenu extends AbstractContainerMenu {

    private final UUID playerUUID;
    private final ShippingBoxBlockEntity blockEntity;
    private final Container shippingContainer;

    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    // ──────── 客户端构造器 ────────

    public ShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.playerUUID = buf.readUUID();
        this.blockEntity = findBlockEntity(buf.readBlockPos());
        this.shippingContainer = new SimpleContainer(54);
        addAllSlots(playerInventory);
    }

    // ──────── 服务端构造器 ────────

    public ShippingBoxMenu(int id, Inventory playerInventory, ShippingBoxBlockEntity blockEntity, UUID playerUUID) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.playerUUID = playerUUID;
        this.blockEntity = blockEntity;
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
        this.shippingContainer = new PlayerSpecificContainer(blockEntity, playerUUID);
        this.shippingContainer.startOpen(playerInventory.player);
        addAllSlots(playerInventory);
    }

    private ShippingBoxBlockEntity findBlockEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBoxBlockEntity sbe) return sbe;
        }
        return null;
    }

    // ──────── 一次性创建所有槽位（正确坐标，不移动） ────────

    private void addAllSlots(Inventory playerInventory) {
        // 主容器槽位 (0-53)：9×6
        for (int row = 0; row < ShippingBoxLayout.CHEST_ROWS; row++) {
            for (int col = 0; col < ShippingBoxLayout.CHEST_COLS; col++) {
                this.addSlot(new Slot(this.shippingContainer,
                        col + row * ShippingBoxLayout.CHEST_COLS,
                        ShippingBoxLayout.CHEST_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.CHEST_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        // 玩家背包槽位 (54-80)：9×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        ShippingBoxLayout.PLAYER_INV_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.PLAYER_INV_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        // 快捷栏槽位 (81-89)：9×1
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    ShippingBoxLayout.HOTBAR_START_X + col * ShippingBoxLayout.SLOT_STEP,
                    ShippingBoxLayout.HOTBAR_START_Y));
        }
    }

    // ──────── Container ────────

    private record PlayerSpecificContainer(ShippingBoxBlockEntity blockEntity, UUID playerUUID) implements Container {
        @Override public int getContainerSize() { return 54; }
        @Override public boolean isEmpty() {
            return blockEntity.getPlayerItems(playerUUID).stream().allMatch(ItemStack::isEmpty);
        }
        @Override public ItemStack getItem(int slot) {
            return blockEntity.getItemForPlayer(slot, playerUUID);
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            return blockEntity.removeItemForPlayer(slot, amount, playerUUID);
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return blockEntity.removeItemForPlayer(slot, 1, playerUUID);
        }
        @Override public void setItem(int slot, ItemStack stack) {
            blockEntity.setItemForPlayer(slot, stack, playerUUID);
        }
        @Override public void setChanged() { blockEntity.setChanged(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { blockEntity.getPlayerItems(playerUUID).clear(); }
    }

    // ──────── stillValid ────────

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ──────── quickMoveStack ────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();
        if (index < 54) {
            if (!this.moveItemStackTo(itemstack, 54, 90, true))
                return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(itemstack, 0, 54, false)) {
            return ItemStack.EMPTY;
        }
        if (itemstack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return itemstack1;
    }

    // ──────── removed ────────

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.shippingContainer.stopOpen(player);
        if (storedLevel != null && !storedLevel.isClientSide && player instanceof ServerPlayer) {
            storedLevel.playSound(null, storedPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS, 0.5F,
                    storedLevel.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    // ──────── Getters ────────

    public UUID getPlayerUUID() { return playerUUID; }
    public ShippingBoxBlockEntity getBlockEntity() { return blockEntity; }
}
