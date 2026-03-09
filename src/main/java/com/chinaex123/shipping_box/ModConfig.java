package com.chinaex123.shipping_box;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ModConfigSpec.IntValue exchangeTime;
        public final ModConfigSpec.ConfigValue<String> successMessageLine1;
        public final ModConfigSpec.ConfigValue<String> successMessageLine2;
        public final ModConfigSpec.ConfigValue<String> successMessageLine3;

        public Common(ModConfigSpec.Builder builder) {
            builder.push("general");
            
            exchangeTime = builder
                    .comment("The time of day when the shipping box exchange occurs (in ticks).",
                            "Default is 0 (6:00 AM).",
                            "Range: 0 - 23999")
                    .defineInRange("exchangeTime", 0, 0, 23999);

            builder.push("messages");
            successMessageLine1 = builder
                    .comment("Line 1 of the exchange success message.")
                    .define("successMessageLine1", "黎明时分，星之精灵已取走货物。");
            successMessageLine2 = builder
                    .comment("Line 2 of the exchange success message. Use {amount} for earnings.")
                    .define("successMessageLine2", "昨日收益：{amount} 硬币");
            successMessageLine3 = builder
                    .comment("Line 3 of the exchange success message. Use {total} for total wealth.")
                    .define("successMessageLine3", "累计财富：{total} 硬币");
            builder.pop();
            
            builder.pop();
        }
    }
}
