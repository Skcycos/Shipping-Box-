package com.chinaex123.shipping_box.common.event;

import java.util.ArrayList;
import java.util.List;

public class ExchangeRuleRegistry {
    private static List<ExchangeRule> currentRules = new ArrayList<>();

    public static List<ExchangeRule> getRules() {
        return currentRules;
    }

    public static void setRules(List<ExchangeRule> rules) {
        currentRules = rules;
    }
}
