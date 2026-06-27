package com.chinaex123.shipping_box.client.screen;

/**
 * 账单面板数据；
 * 由 {@link BillPanelRenderer} 根据容器内物品与已同步的兑换规则计算得出，
 * 供 Screen 在左侧面板渲染时使用。
 */
public class BillData {

    /** true 表示当前存在输出为虚拟货币/爬爬币的匹配规则，应显示预计收益行 */
    public final boolean hasCoinRule;

    /** 预计收益（基础预估，不含售价加成/节气浮动）；当 hasCoinRule 为 false 时无意义 */
    public final int estimatedEarnings;

    /** 已放入物品的总个数（54 格内所有堆叠 count 之和） */
    public final int itemsPlaced;

    /** 结算时间文本，如 "明早 06:00" */
    public final String settlementTime;

    public BillData(boolean hasCoinRule, int estimatedEarnings, int itemsPlaced, String settlementTime) {
        this.hasCoinRule = hasCoinRule;
        this.estimatedEarnings = estimatedEarnings;
        this.itemsPlaced = itemsPlaced;
        this.settlementTime = settlementTime;
    }

    public static BillData empty(String settlementTime) {
        return new BillData(false, 0, 0, settlementTime);
    }
}
