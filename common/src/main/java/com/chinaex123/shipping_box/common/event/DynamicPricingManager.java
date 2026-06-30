package com.chinaex123.shipping_box.common.event;

import com.chinaex123.shipping_box.common.network.PacketSoldCountSync;
import com.chinaex123.shipping_box.common.platform.PlatformNetworking;
import com.chinaex123.shipping_box.common.platform.PlatformSavedData;
import com.chinaex123.shipping_box.common.platform.PlatformServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicPricingManager {
    private static final String DATA_NAME = "dynamic_pricing_data";

    private static DimensionDataStorage getStorage() {
        MinecraftServer server = PlatformServerAccess.getCurrentServer();
        if (server != null) {
            return server.overworld().getDataStorage();
        }
        return null;
    }

    private static PricingData getPricingData() {
        DimensionDataStorage storage = getStorage();
        if (storage != null) {
            return storage.computeIfAbsent(
                    PlatformSavedData.createFactory(
                            PricingData::createNew,
                            (tag, provider) -> PricingData.loadFromNBT(tag)
                    ),
                    DATA_NAME
            );
        }
        return null;
    }

    public static void saveData() {
    }

    public static void addSoldCount(String itemIdentifier, int count) {
        PricingData data = getPricingData();
        if (data != null) {
            checkAndResetIfNeeded(itemIdentifier);

            data.addCount(itemIdentifier, count);

            int newCount = data.getCount(itemIdentifier);

            data.setDirty();

            PlatformNetworking.sendToAllPlayers(new PacketSoldCountSync(itemIdentifier, newCount));
        }
    }

    public static int getSoldCount(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data != null) {
            return data.getCount(itemIdentifier);
        }
        return 0;
    }

    private static void checkAndResetIfNeeded(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data == null) {
            return;
        }

        List<ExchangeRule> rules = ExchangeRuleRegistry.getRules();

        for (ExchangeRule rule : rules) {
            ExchangeRule.OutputItem output = rule.getOutputItem();

            if ("dynamic_pricing".equals(output.getType()) &&
                    output.getDynamicProperties() != null) {

                String ruleItemIdentifier;
                if (output.isCoin()) {
                    ruleItemIdentifier = rule.getInputs().getFirst().getItem();
                } else {
                    ruleItemIdentifier = output.getItem();
                }

                if (itemIdentifier.equals(ruleItemIdentifier)) {
                    int resetDay = output.getDynamicProperties().getDay();

                    if (resetDay == -1) {
                        data.recordSaleDay(itemIdentifier);
                        break;
                    } else if (resetDay == 0) {
                        data.resetCount(itemIdentifier);
                        data.recordSaleDay(itemIdentifier);
                        break;
                    } else if (resetDay > 0) {
                        boolean shouldReset = data.shouldResetCount(itemIdentifier, resetDay);

                        if (shouldReset) {
                            data.resetCount(itemIdentifier);
                            data.recordSaleDay(itemIdentifier);
                        } else {
                            data.recordSaleDay(itemIdentifier);
                        }
                        break;
                    }
                }
            }
        }
    }

    public static int getDaysSinceLastSale(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data != null) {
            return data.getDaysSinceLastSale(itemIdentifier);
        }
        return -1;
    }

    public static int getResetRemainingDays(String itemIdentifier) {
        List<ExchangeRule> rules = ExchangeRuleRegistry.getRules();
        for (ExchangeRule rule : rules) {
            ExchangeRule.OutputItem output = rule.getOutputItem();
            if ("dynamic_pricing".equals(output.getType()) &&
                    output.getDynamicProperties() != null) {

                String ruleItemIdentifier;
                if (output.isCoin()) {
                    ruleItemIdentifier = rule.getInputs().getFirst().getItem();
                } else {
                    ruleItemIdentifier = output.getItem();
                }

                if (itemIdentifier.equals(ruleItemIdentifier)) {
                    int resetDay = output.getDynamicProperties().getDay();

                    if (resetDay == -1) {
                        return -1;
                    } else if (resetDay == 0) {
                        return 0;
                    } else if (resetDay > 0) {
                        PricingData data = getPricingData();
                        if (data != null) {
                            return data.getResetRemainingDays(itemIdentifier, resetDay);
                        }
                    }
                    break;
                }
            }
        }
        return -1;
    }

    public static Map<String, Integer> getAllSoldCounts() {
        PricingData data = getPricingData();
        if (data != null) {
            return new HashMap<>(data.getData());
        }
        return new HashMap<>();
    }

    public static void setAllSoldCounts(Map<String, Integer> counts) {
        PricingData data = getPricingData();
        if (data != null) {
            data.setData(counts);
        }
    }

    public static void clearAllData() {
        PricingData data = getPricingData();
        if (data != null) {
            data.setData(new HashMap<>());
        }
    }

    public static int getSoldCount(String itemIdentifier, int resetDay) {
        if (resetDay == -1) {
            return getSoldCount(itemIdentifier);
        }

        PricingData data = getPricingData();
        if (data != null) {
            boolean shouldReset = data.shouldResetCount(itemIdentifier, resetDay);

            if (shouldReset) {
                data.resetCount(itemIdentifier);
                return 0;
            }

            return data.getCount(itemIdentifier);
        }
        return 0;
    }

    public static void addSoldCount(String itemIdentifier, int amount, int resetDay) {
        if (resetDay == -1) {
            addSoldCount(itemIdentifier, amount);
            return;
        }

        PricingData data = getPricingData();
        if (data != null) {
            boolean shouldReset = data.shouldResetCount(itemIdentifier, resetDay);

            if (shouldReset) {
                data.resetCount(itemIdentifier);
                data.addCount(itemIdentifier, amount);
            } else {
                data.addCount(itemIdentifier, amount);
            }

            data.setDirty();

            PlatformNetworking.sendToAllPlayers(new PacketSoldCountSync(itemIdentifier, data.getCount(itemIdentifier)));
        }
    }
}
