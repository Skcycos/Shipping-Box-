package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
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
import org.jetbrains.annotations.NotNull;

/**
 * 自动售货箱菜单。继承 AbstractContainerMenu，构造时直接按 ShippingBoxLayout 坐标 addSlot。
 */
public class AutoShippingBoxMenu extends AbstractContainerMenu {

    private final AutoShippingBoxBlockEntity blockEntity;
    private final Container shippingContainer;

    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    // ──────── 客户端构造器 ────────

    public AutoShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        this.blockEntity = findBlockEntity(buf.readBlockPos());
        this.shippingContainer = new SimpleContainer(54);
        addAllSlots(playerInventory);
    }

    // ──────── 服务端构造器 ────────

    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        this.blockEntity = blockEntity;
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
        this.shippingContainer = blockEntity; // AutoShippingBoxBlockEntity 本身就是 Container
        this.shippingContainer.startOpen(playerInventory.player);
        addAllSlots(playerInventory);
    }

    private AutoShippingBoxBlockEntity findBlockEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AutoShippingBoxBlockEntity abe) return abe;
        }
        return null;
    }

    // ──────── 一次性创建所有槽位 ────────

    private void addAllSlots(Inventory playerInventory) {
        // 主容器槽位 (0-53)
        for (int row = 0; row < ShippingBoxLayout.CHEST_ROWS; row++) {
            for (int col = 0; col < ShippingBoxLayout.CHEST_COLS; col++) {
                this.addSlot(new Slot(this.shippingContainer,
                        col + row * ShippingBoxLayout.CHEST_COLS,
                        ShippingBoxLayout.CHEST_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.CHEST_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        // 玩家背包槽位 (54-80)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        ShippingBoxLayout.PLAYER_INV_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.PLAYER_INV_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        // 快捷栏槽位 (81-89)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    ShippingBoxLayout.HOTBAR_START_X + col * ShippingBoxLayout.SLOT_STEP,
                    ShippingBoxLayout.HOTBAR_START_Y));
        }
    }

    // ──────── stillValid ────────

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ──────── quickMoveStack ────────

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();
        if (index < 54) {
            if (!this.moveItemStackTo(itemstack, 54, 90, true))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(itemstack, 0, 54, false))
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

    public AutoShippingBoxBlockEntity getBlockEntity() { return blockEntity; }
}
