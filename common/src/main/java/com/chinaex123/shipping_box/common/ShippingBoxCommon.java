package com.chinaex123.shipping_box.common;

/**
 * Platform-neutral entry point for the future Architectury split.
 *
 * <p>Move rule parsing, validation, exchange calculation, and shared API
 * contracts here before wiring Fabric and NeoForge to the same core logic.
 */
public final class ShippingBoxCommon {
    public static final String MOD_ID = "shipping_box";

    private ShippingBoxCommon() {
    }

    public static void init() {
        // Shared initialization will live here after the NeoForge code is split.
    }
}
