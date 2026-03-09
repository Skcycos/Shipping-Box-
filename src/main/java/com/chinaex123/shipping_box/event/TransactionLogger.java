package com.chinaex123.shipping_box.event;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class TransactionLogger {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // 日志目录路径：config/shipping_box/logs/
    private static final Path LOG_DIR = FMLPaths.CONFIGDIR.get().resolve("shipping_box/logs");

    /**
     * 记录交易日志
     * 
     * @param playerUUID 玩家UUID
     * @param playerName 玩家名称（如果已知，否则可传null）
     * @param inputs 消耗的物品列表
     * @param outputs 获得的物品列表
     * @param virtualCurrency 获得的虚拟货币数量
     * @param level 世界实例（用于获取时间等信息）
     */
    public static void logTransaction(UUID playerUUID, String playerName, List<ItemStack> inputs, List<ItemStack> outputs, int virtualCurrency, Level level) {
        try {
            // 确保日志目录存在
            if (!Files.exists(LOG_DIR)) {
                Files.createDirectories(LOG_DIR);
            }

            // 获取当天的日志文件
            String dateStr = LocalDateTime.now().format(FILE_DATE_FORMAT);
            File logFile = LOG_DIR.resolve("transaction_" + dateStr + ".log").toFile();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                StringBuilder logEntry = new StringBuilder();
                
                // 时间戳
                logEntry.append("[").append(LocalDateTime.now().format(LOG_TIME_FORMAT)).append("] ");
                
                // 玩家信息
                logEntry.append("Player: ").append(playerName != null ? playerName : "Unknown").append(" (").append(playerUUID).append(") | ");
                
                // 输入物品
                logEntry.append("Inputs: [");
                for (int i = 0; i < inputs.size(); i++) {
                    ItemStack stack = inputs.get(i);
                    logEntry.append(stack.getItem().getDescriptionId()).append(" x").append(stack.getCount());
                    if (i < inputs.size() - 1) logEntry.append(", ");
                }
                logEntry.append("] | ");
                
                // 输出物品
                logEntry.append("Outputs: [");
                if (virtualCurrency > 0) {
                    logEntry.append("Virtual Currency: ").append(virtualCurrency);
                    if (!outputs.isEmpty()) logEntry.append(", ");
                }
                for (int i = 0; i < outputs.size(); i++) {
                    ItemStack stack = outputs.get(i);
                    logEntry.append(stack.getItem().getDescriptionId()).append(" x").append(stack.getCount());
                    if (i < outputs.size() - 1) logEntry.append(", ");
                }
                logEntry.append("]");
                
                // 写入文件
                writer.write(logEntry.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace(); // 在控制台打印错误，但不中断游戏逻辑
        }
    }
}
