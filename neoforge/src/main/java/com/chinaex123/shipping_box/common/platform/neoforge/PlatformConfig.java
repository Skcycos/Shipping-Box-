package com.chinaex123.shipping_box.common.platform.neoforge;

import com.chinaex123.shipping_box.config.CommonConfig;

public class PlatformConfig {
    public static int getExchangeTime() {
        return CommonConfig.EXCHANGE_TIME.get();
    }

    public static boolean isExchangeEffectsEnabled() {
        return CommonConfig.ENABLE_EXCHANGE_EFFECTS.get();
    }

    public static boolean isTransactionLoggingEnabled() {
        return CommonConfig.ENABLE_TRANSACTION_LOGGING.get();
    }
}
