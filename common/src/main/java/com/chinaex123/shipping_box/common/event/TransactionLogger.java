package com.chinaex123.shipping_box.common.event;

import com.chinaex123.shipping_box.common.platform.PlatformConfig;
import com.chinaex123.shipping_box.common.platform.PlatformPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransactionLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionLogger.class.getName());
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Path getLogDir() {
        return PlatformPaths.getConfigDir().resolve("shipping_box/logs");
    }

    public static void logTransaction(String playerName, List<ItemStack> inputs, List<ItemStack> outputs, int virtualCurrency, Level level, ExchangeRule rule) {
        try {
            if (!PlatformConfig.isTransactionLoggingEnabled()) {
                return;
            }

            Path logDir = getLogDir();
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            String dateStr = LocalDateTime.now().format(FILE_DATE_FORMAT);
            File logFile = logDir.resolve("transaction_" + dateStr + ".log").toFile();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                StringBuilder logEntry = new StringBuilder();

                logEntry.append("[").append(LocalDateTime.now().format(LOG_TIME_FORMAT)).append("] ");

                logEntry.append("玩家：").append(playerName).append(" | ");

                logEntry.append("输入：[");
                if (inputs.isEmpty()) {
                    logEntry.append("空");
                } else {
                    for (int i = 0; i < inputs.size(); i++) {
                        ItemStack stack = inputs.get(i);
                        String itemInfo = formatItemWithRuleComponents(stack, rule, i);
                        logEntry.append(itemInfo);
                        if (i < inputs.size() - 1) logEntry.append(", ");
                    }
                }
                logEntry.append("] | ");

                logEntry.append("输出：[");
                if (virtualCurrency > 0) {
                    logEntry.append("VSS 货币：").append(virtualCurrency);
                    long validOutputCount = outputs.stream().filter(s -> !s.isEmpty() && s.getCount() > 0).count();
                    if (validOutputCount > 0) logEntry.append(", ");
                }

                boolean hasValidOutput = false;
                for (ItemStack stack : outputs) {
                    if (stack.isEmpty() || stack.getCount() <= 0) continue;

                    if (hasValidOutput) logEntry.append(", ");
                    String itemInfo = formatOutputItem(stack, rule);
                    logEntry.append(itemInfo);
                    hasValidOutput = true;
                }

                if (!hasValidOutput && virtualCurrency == 0) {
                    logEntry.append("EMPTY");
                }
                logEntry.append("]");

                writer.write(logEntry.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.warn("[Shipping Box-TransactionLogger] 写入记录交易日志时出错");
        }
    }

    private static String formatItemWithRuleComponents(ItemStack stack, ExchangeRule rule, int index) {
        String itemName = Component.translatable(stack.getItem().getDescriptionId()).getString();
        StringBuilder result = new StringBuilder();
        result.append(itemName).append(" x").append(stack.getCount());

        if (rule != null && rule.getInputs() != null && index < rule.getInputs().size()) {
            ExchangeRule.InputItem input = rule.getInputs().get(index);
            Object components = input.getComponents();

            if (components != null) {
                String componentStr = formatRuleComponents(components);
                if (!componentStr.isEmpty()) {
                    result.append(" [").append(componentStr).append("]");
                }
            }
        }

        return result.toString();
    }

    private static String formatOutputItem(ItemStack stack, ExchangeRule rule) {
        String itemName = Component.translatable(stack.getItem().getDescriptionId()).getString();
        StringBuilder result = new StringBuilder();
        result.append(itemName).append(" x").append(stack.getCount());

        if (rule != null && rule.getOutputItem() != null) {
            Object components = rule.getOutputItem().getComponents();

            if (components != null) {
                String componentStr = formatRuleComponents(components);
                if (!componentStr.isEmpty()) {
                    result.append(" [").append(componentStr).append("]");
                }
            }
        }

        return result.toString();
    }

    private static String formatRuleComponents(Object components) {
        if (components instanceof com.google.gson.JsonObject jsonObj) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            for (var entry : jsonObj.entrySet()) {
                String componentName = entry.getKey();
                var componentValue = entry.getValue();

                if (componentName.contains(":")) {
                    componentName = componentName.substring(componentName.indexOf(":") + 1);
                }

                String valueStr = formatComponentJsonValue(componentValue);

                if (!first) sb.append(", ");
                sb.append(componentName).append("=").append(valueStr);
                first = false;
            }

            return sb.toString();
        } else if (components instanceof String str) {
            return str;
        }

        return "";
    }

    private static String formatComponentJsonValue(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();

            if (obj.has("levels")) {
                var levelsObj = obj.getAsJsonObject("levels");
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (var entry : levelsObj.entrySet()) {
                    if (!first) sb.append(", ");
                    String enchId = entry.getKey();
                    if (enchId.contains(":")) {
                        enchId = enchId.substring(enchId.indexOf(":") + 1);
                    }
                    int level = entry.getValue().getAsInt();
                    sb.append(enchId).append("=").append(level);
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }

            return obj.toString();
        } else if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        return element.toString();
    }
}
