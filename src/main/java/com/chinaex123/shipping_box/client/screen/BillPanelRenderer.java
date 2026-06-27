package com.chinaex123.shipping_box.client.screen;

import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 账单面板渲染器；
 * <p>
 * 职责：
 * 1. 读取容器 0-53 槽位的物品，模拟兑换流程计算「预计收益」（仅当匹配规则的输出为虚拟货币/爬爬币时）；
 * 2. 统计已放入物品总个数；
 * 3. 由配置的结算 tick 换算为「明早 HH:MM」文本；
 * 4. 在指定坐标处用 {@link GuiGraphics} 绘制半透明深色面板与文本。
 * <p>
 * 预计收益为「基础预估」：不含 selling_price_boost 属性加成与节气浮动
 * （客户端缺少 ServerPlayer/Level 上下文，无法精确计算）。
 */
public final class BillPanelRenderer {

    /** 面板宽度（像素） */
    public static final int PANEL_WIDTH = 92;
    /** 面板内边距 */
    private static final int PADDING = 6;
    /** 面板背景颜色（半透明深灰） */
    private static final int BG_COLOR = 0xCC1A1A1A;
    /** 面板边框颜色（浅灰） */
    private static final int BORDER_COLOR = 0xFF555555;

    private BillPanelRenderer() {
    }

    /**
     * 根据菜单当前内容计算账单数据。
     *
     * @param menu 当前打开的容器菜单
     * @return 账单数据
     */
    public static BillData compute(AbstractContainerMenu menu) {
        // 收集 0-53 槽位的非空物品副本
        List<ItemStack> current = new ArrayList<>();
        int itemsPlaced = 0;
        for (int i = 0; i < 54; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                current.add(stack.copy());
                itemsPlaced += stack.getCount();
            }
        }

        boolean hasCoinRule = false;
        int estimatedEarnings = 0;

        if (!current.isEmpty()) {
            // 模拟实际兑换循环，仅累计虚拟货币收益
            List<ExchangeRule> rules = ExchangeRecipeManager.getRules();
            if (rules != null && !rules.isEmpty()) {
                boolean exchanged;
                int safety = 0;
                do {
                    exchanged = false;
                    ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(current);
                    if (rule == null) {
                        break;
                    }
                    ExchangeRule.OutputItem output = rule.getOutputItem();
                    if (output == null) {
                        break;
                    }

                    // 仅对虚拟货币输出规则累计收益
                    if (!output.isCoin()) {
                        // 非虚拟货币规则：不显示预计收益，结束模拟
                        break;
                    }

                    int maxExchanges = ExchangeManager.getMaxExchanges(rule, current);
                    if (maxExchanges <= 0) {
                        break;
                    }

                    hasCoinRule = true;

                    if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                        // 虚拟货币 + 动态定价：按累计销量逐件查阈值
                        String itemIdentifier = rule.getInputs().getFirst().getItem();
                        int sold = ClientSoldCountCache.getCachedSoldCount(itemIdentifier);
                        for (int i = 0; i < maxExchanges; i++) {
                            estimatedEarnings += output.getDynamicCount(sold + i);
                        }
                    } else {
                        // 普通虚拟货币：固定数量 × 次数
                        estimatedEarnings += output.getCount() * maxExchanges;
                    }

                    // 消耗输入物品，继续下一轮匹配
                    for (int i = 0; i < maxExchanges; i++) {
                        current = ExchangeRecipeManager.consumeInputs(rule, current);
                    }
                    exchanged = true;

                    if (++safety > 1000) {
                        break;
                    }
                } while (exchanged);
            }
        }

        String settlementTime = formatSettlementTime(CommonConfig.EXCHANGE_TIME.get());
        return new BillData(hasCoinRule, estimatedEarnings, itemsPlaced, settlementTime);
    }

    /**
     * 将游戏 tick（0-23999）换算为现实钟点文本 "HH:MM"。
     * MC 中 0 tick = 06:00，1000 tick = 1 小时。
     */
    private static String formatSettlementTime(int tick) {
        int totalMinutes = (6 * 60) + (tick * 60 / 1000);
        int hours = Math.floorMod(totalMinutes / 60, 24);
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * 在指定坐标绘制账单面板。
     *
     * @param gg    GuiGraphics 绘制器
     * @param panelX 面板左上角 x
     * @param panelY 面板左上角 y
     * @param data  账单数据
     * @param font  字体
     */
    public static void render(GuiGraphics gg, int panelX, int panelY, BillData data, Font font) {
        int panelHeight = computePanelHeight(data);

        // 背景
        gg.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BG_COLOR);
        // 边框（上/下/左/右 各 1px）
        gg.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, BORDER_COLOR);
        gg.fill(panelX, panelY + panelHeight - 1, panelX + PANEL_WIDTH, panelY + panelHeight, BORDER_COLOR);
        gg.fill(panelX, panelY, panelX + 1, panelY + panelHeight, BORDER_COLOR);
        gg.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BORDER_COLOR);

        int textX = panelX + PADDING;
        int textY = panelY + PADDING;

        // 标题
        gg.drawString(font, Component.translatable("screen.shipping_box.bill_title"), textX, textY, 0xFFE0C060, false);
        textY += font.lineHeight + 4;

        // 分隔线
        gg.fill(panelX + 2, textY - 2, panelX + PANEL_WIDTH - 2, textY - 1, 0xFF333333);
        textY += 2;

        // 预计收益（仅虚拟货币规则）
        if (data.hasCoinRule) {
            gg.drawString(font, Component.translatable("screen.shipping_box.bill_estimated_earnings"),
                    textX, textY, 0xFFAAAAAA, false);
            textY += font.lineHeight + 1;
            gg.drawString(font, Component.literal("§e" + data.estimatedEarnings + "◎ §7§o(预估)"),
                    textX + 2, textY, 0xFFFFFFE0, false);
            textY += font.lineHeight + 4;
        }

        // 已放入件数
        gg.drawString(font, Component.translatable("screen.shipping_box.bill_items_placed"),
                textX, textY, 0xFFAAAAAA, false);
        textY += font.lineHeight + 1;
        gg.drawString(font, Component.literal("§a" + data.itemsPlaced),
                textX + 2, textY, 0xFFFFFFFF, false);
        textY += font.lineHeight + 4;

        // 结算时间
        gg.drawString(font, Component.translatable("screen.shipping_box.bill_settlement_time", data.settlementTime),
                textX, textY, 0xFFAAAAAA, false);
    }

    /** 根据是否显示预计收益计算面板高度，与 render 的绘制内容严格对应 */
    private static int computePanelHeight(BillData data) {
        int lh = fontLineHeight();
        int h = PADDING;                       // 顶部内边距
        h += lh + 4;                           // 标题
        h += 2;                                // 分隔线间距
        if (data.hasCoinRule) {
            h += lh + 1 + lh + 4;              // 收益标签 + 数值
        }
        h += lh + 1 + lh + 4;                  // 已放入标签 + 数值
        h += lh;                               // 结算时间
        h += PADDING;                          // 底部内边距
        return h;
    }

    private static int fontLineHeight() {
        return 9; // Minecraft 默认字体行高
    }
}
