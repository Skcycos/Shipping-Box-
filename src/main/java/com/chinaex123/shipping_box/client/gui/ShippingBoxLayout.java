package com.chinaex123.shipping_box.client.gui;

/**
 * 售货箱 GUI 布局常量。
 * <p>
 * 贴图 780×1016（4x），GUI 195×254。
 * Slot GUI 坐标 = (PS 单元坐标 + 4) / 4。
 */
public final class ShippingBoxLayout {

    private ShippingBoxLayout() {}

    public static final int TEXTURE_WIDTH = 780;
    public static final int TEXTURE_HEIGHT = 1016;
    public static final int IMAGE_WIDTH = TEXTURE_WIDTH / 4;   // 195
    public static final int IMAGE_HEIGHT = TEXTURE_HEIGHT / 4; // 254
    public static final int SLOT_STEP = 18;

    public static final int CHEST_COLS = 9;
    public static final int CHEST_ROWS = 6;
    public static final int CHEST_SLOT_COUNT = CHEST_COLS * CHEST_ROWS;

    /** (64+4)/4 */
    public static final int CHEST_START_X = 17;
    /** (120+4)/4 */
    public static final int CHEST_START_Y = 31;

    /** (64+4)/4 */
    public static final int PLAYER_INV_START_X = 17;
    /** (624+4)/4 */
    public static final int PLAYER_INV_START_Y = 157;

    /** (64+4)/4 */
    public static final int HOTBAR_START_X = 17;
    /** (872+4)/4 = 219 */
    public static final int HOTBAR_START_Y = 219;
}
