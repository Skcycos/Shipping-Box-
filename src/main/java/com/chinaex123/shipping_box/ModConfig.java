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

        public Common(ModConfigSpec.Builder builder) {
            builder.push("general");
            
            exchangeTime = builder
                    .comment("The time of day when the shipping box exchange occurs (in ticks).",
                            "Default is 0 (6:00 AM).",
                            "Range: 0 - 23999")
                    .defineInRange("exchangeTime", 0, 0, 23999);
            
            builder.pop();
        }
    }
}
