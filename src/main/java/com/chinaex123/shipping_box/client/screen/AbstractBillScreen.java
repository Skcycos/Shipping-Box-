package com.chinaex123.shipping_box.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * 售货箱界面的共享基类；
 * <p>
 * 在原版 9×6 箱子背景的基础上，将整体右移以在左侧腾出空间绘制 {@link BillPanelRenderer} 账单面板。
 * 子类只需提供对应的菜单类型与标题即可。
 */
public abstract class AbstractBillScreen<M extends ChestMenu> extends AbstractContainerScreen<M> {

    private static final ResourceLocation CONTAINER_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** 面板与箱子之间的间距 */
    private static final int GAP = 4;

    protected final int containerRows;

    /** 缓存上一次计算的账单数据与槽位签名，避免每帧重算 */
    private BillData cachedBill;
    private int cachedSignature;

    protected AbstractBillScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.containerRows = menu.getRowCount();
        this.imageHeight = 114 + this.containerRows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // 整体右移，为左侧账单面板腾出空间；并对整体（面板+间距+箱子）重新居中
        int offset = BillPanelRenderer.PANEL_WIDTH + GAP;
        int totalWidth = this.imageWidth + offset;
        this.leftPos = (this.width - totalWidth) / 2 + offset;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // super.render 已包含 renderBackground / renderBg / 槽位 / 标签
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // 账单面板绘制在箱子左侧（位置不与槽位重叠）
        BillData bill = getBillData();
        int panelX = this.leftPos - BillPanelRenderer.PANEL_WIDTH - GAP;
        int panelY = this.topPos;
        BillPanelRenderer.render(guiGraphics, panelX, panelY, bill, this.font);
        // 渲染工具提示
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 使用 leftPos/topPos 而非重新居中，保证与左移后的槽位对齐
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, this.imageWidth, this.containerRows * 18 + 17);
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y + this.containerRows * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    /** 获取账单数据，槽位未变化时复用缓存 */
    private BillData getBillData() {
        int signature = computeSlotSignature();
        if (cachedBill == null || signature != cachedSignature) {
            cachedBill = BillPanelRenderer.compute(this.menu);
            cachedSignature = signature;
        }
        return cachedBill;
    }

    /** 计算槽位内容签名（仅用于变更检测） */
    private int computeSlotSignature() {
        int hash = 0;
        for (int i = 0; i < 54; i++) {
            var stack = this.menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                hash = hash * 31 + stack.getItem().hashCode();
                hash = hash * 31 + stack.getCount();
                hash = hash * 31 + stack.getHoverName().getString().hashCode();
            }
        }
        return hash;
    }
}
