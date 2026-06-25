package com.chinaex123.shipping_box.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chinaex123.shipping_box.ShippingBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import com.chinaex123.shipping_box.util.ItemIconPngRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端图标缓存管理器
 * 按 tick 限制逐步将物品/方块图标渲染并保存为 PNG，避免卡顿。
 */
public class EditorIconCacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EditorIconCacheManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int ICON_SIZE = 32;           // 图标尺寸，可改
    public static final int ICONS_PER_TICK = 3;       // 每tick最大处理数，调低可减少卡顿

    private static final EditorIconCacheManager INSTANCE = new EditorIconCacheManager();

    private final Path cacheRoot;
    private final Path itemsDir;
    private final Path blocksDir;
    private final Path manifestFile;

    private final List<CacheEntry> pendingEntries = new ArrayList<>();
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private volatile Status status = Status.IDLE;
    private volatile String errorMessage = null;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean forceMode = false;

    private UUID bossBarId;

    private EditorIconCacheManager() {
        this.cacheRoot = FMLPaths.GAMEDIR.get()
                .resolve("config")
                .resolve(ShippingBox.MOD_ID)
                .resolve("editor_icon_cache");
        this.itemsDir = cacheRoot.resolve("items");
        this.blocksDir = cacheRoot.resolve("blocks");
        this.manifestFile = cacheRoot.resolve("manifest.json");

        // Load persisted status on startup
        loadStatusFromManifest();
    }

    private void updateBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        float progress = total.get() > 0 ? (float) processed.get() / total.get() : 0f;
        String title = "正在缓存图标... (" + processed.get() + " / " + total.get() + ")";

        LerpingBossEvent event = new LerpingBossEvent(
            bossBarId,
            Component.literal(title),
            progress,
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS,
            false, false, false
        );

        try {
            // Use reflection because events field is not public
            java.lang.reflect.Field eventsField = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, LerpingBossEvent> events = (java.util.Map<java.util.UUID, LerpingBossEvent>) eventsField.get(mc.gui.getBossOverlay());
            events.put(bossBarId, event);
        } catch (Exception e) {
            // Fallback: do nothing if reflection fails
        }
    }

    private void removeBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        try {
            java.lang.reflect.Field eventsField = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, LerpingBossEvent> events = (java.util.Map<java.util.UUID, LerpingBossEvent>) eventsField.get(mc.gui.getBossOverlay());
            events.remove(bossBarId);
        } catch (Exception e) {
            // ignore
        }
        bossBarId = null;
    }

    private void loadStatusFromManifest() {
        try {
            if (Files.exists(manifestFile)) {
                String json = Files.readString(manifestFile, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                String statusStr = obj.has("status") ? obj.get("status").getAsString() : "";
                if ("ready".equals(statusStr) || "done".equals(statusStr)) {
                    this.status = Status.DONE;
                    // We don't know exact counts from manifest easily, but can set total from arrays
                    int itemCount = obj.has("items") ? obj.getAsJsonArray("items").size() : 0;
                    int blockCount = obj.has("blocks") ? obj.getAsJsonArray("blocks").size() : 0;
                    this.total.set(itemCount + blockCount);
                    this.processed.set(this.total.get());
                    LOGGER.info("[IconCache] Loaded persisted ready state from manifest ({} items, {} blocks)", itemCount, blockCount);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[IconCache] Failed to load status from manifest: {}", e.getMessage());
        }
    }

    public static EditorIconCacheManager getInstance() {
        return INSTANCE;
    }

    public enum Status {
        IDLE, RUNNING, DONE, ERROR
    }

    private record CacheEntry(ResourceLocation id, boolean isBlock, String displayName, String fileName) {}

    public void startCache(boolean force) {
        if (running.get()) {
            if (force) {
                stopCurrentTask();
                clearCacheInternal(false);
            } else {
                LOGGER.info("[IconCache] 缓存任务已在运行中 (processed {}/{})", processed.get(), total.get());
                return;
            }
        }

        // 无论 force 模式，始终确保输出目录存在
        ensureDirectories();

        if (force) {
            forceMode = true;
            clearCacheInternal(false);
        } else {
            forceMode = false;
        }

        prepareEntries();
        if (pendingEntries.isEmpty()) {
            status = Status.DONE;
            total.set(0);
            processed.set(0);
            writeManifest();
            return;
        }

        total.set(pendingEntries.size());
        processed.set(0);
        status = Status.RUNNING;
        errorMessage = null;
        running.set(true);

        bossBarId = UUID.randomUUID();
        updateBossBar();

        LOGGER.info("[IconCache] 开始缓存任务，共 {} 个条目 (force={})", total.get(), force);
    }

    public void stopCurrentTask() {
        if (running.compareAndSet(true, false)) {
            status = Status.IDLE;
            removeBossBar();
            LOGGER.info("[IconCache] 缓存任务已停止");
        }
    }

    public void clearCache() {
        stopCurrentTask();
        clearCacheInternal(true);
    }

    /**
     * 确保缓存输出目录存在（幂等）。
     */
    private void ensureDirectories() {
        try {
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
        } catch (Exception e) {
            LOGGER.error("[IconCache] 创建缓存目录失败", e);
        }
    }

    private void clearCacheInternal(boolean resetStatus) {
        removeBossBar();
        try {
            if (Files.exists(cacheRoot)) {
                Files.walk(cacheRoot)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                        });
            }
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
            LOGGER.info("[IconCache] 缓存目录已清空");
        } catch (Exception e) {
            LOGGER.error("[IconCache] 清空缓存失败", e);
        }

        pendingEntries.clear();
        processed.set(0);
        total.set(0);

        if (resetStatus) {
            status = Status.IDLE;
            errorMessage = null;
        }
    }

    private void prepareEntries() {
        pendingEntries.clear();

        // Items
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item == Items.AIR) return;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, false, display, fileName));
        });

        // Blocks (as item icon)
        BuiltInRegistries.BLOCK.forEach(block -> {
            Item item = block.asItem();
            if (item == Items.AIR) return;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, true, display, fileName));
        });

        LOGGER.info("[IconCache] 准备完成，共 {} 个缓存条目", pendingEntries.size());
    }

    private String toSafeFileName(ResourceLocation id) {
        return (id.getNamespace() + "_" + id.getPath()).replace(':', '_').replace('/', '_');
    }

    /**
     * 由 ClientTickEvent 调用，每 tick 处理有限数量
     */
    public void tick() {
        if (!running.get() || status != Status.RUNNING) return;

        int processedThisTick = 0;
        while (processedThisTick < ICONS_PER_TICK && !pendingEntries.isEmpty()) {
            CacheEntry entry = pendingEntries.remove(0);

            try {
                ItemStack stack = entry.isBlock()
                        ? new ItemStack(BuiltInRegistries.BLOCK.get(entry.id()).asItem())
                        : new ItemStack(BuiltInRegistries.ITEM.get(entry.id()));

                byte[] png = ItemIconPngRenderer.renderStackToPng(stack, ICON_SIZE);
                if (png == null || png.length == 0) {
                    // Fallback: generate a simple placeholder icon so the cache doesn't completely fail
                    png = createPlaceholderPng(ICON_SIZE, entry.id().hashCode());
                    LOGGER.warn("[IconCache] 使用占位符图标 for {}", entry.id());
                }

                Path targetDir = entry.isBlock() ? blocksDir : itemsDir;
                Path targetFile = targetDir.resolve(entry.fileName() + ".png");
                Files.write(targetFile, png);

            } catch (Exception e) {
                LOGGER.warn("[IconCache] 处理 {} 失败: [{}] {}",
                        entry.id(), e.getClass().getSimpleName(), e.getMessage());
                LOGGER.debug("[IconCache] 详细堆栈", e);
            } finally {
                processed.incrementAndGet();
                processedThisTick++;
            }
        }

        if (pendingEntries.isEmpty()) {
            running.set(false);
            status = Status.DONE;
            writeManifest();
            removeBossBar();
            LOGGER.info("[IconCache] 缓存任务完成，共处理 {} 个", processed.get());
        } else if (running.get()) {
            updateBossBar();
        }
    }

    private void writeManifest() {
        try {
            Files.createDirectories(cacheRoot);

            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("modid", ShippingBox.MOD_ID);
            root.addProperty("iconSize", ICON_SIZE);
            root.addProperty("iconsPerTick", ICONS_PER_TICK);
            root.addProperty("status", "ready");
            root.addProperty("generatedAt", System.currentTimeMillis());

            JsonArray itemsArr = new JsonArray();
            JsonArray blocksArr = new JsonArray();

            // 重新扫描以填充 manifest（简单实现，实际可从已生成文件构建）
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item == Items.AIR) return;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) return;
                String fileName = toSafeFileName(id) + ".png";
                Path file = itemsDir.resolve(fileName);
                if (Files.exists(file)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id.toString());
                    entry.addProperty("displayName", new ItemStack(item).getHoverName().getString());
                    entry.addProperty("path", "/icon/cache/items/" + fileName);
                    itemsArr.add(entry);
                }
            });

            BuiltInRegistries.BLOCK.forEach(block -> {
                Item item = block.asItem();
                if (item == Items.AIR) return;
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id == null) return;
                String fileName = toSafeFileName(id) + ".png";
                Path file = blocksDir.resolve(fileName);
                if (Files.exists(file)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id.toString());
                    entry.addProperty("displayName", new ItemStack(item).getHoverName().getString());
                    entry.addProperty("path", "/icon/cache/blocks/" + fileName);
                    blocksArr.add(entry);
                }
            });

            root.add("items", itemsArr);
            root.add("blocks", blocksArr);

            // tag → 代表性图标：遍历所有 item tag，取第一个已有缓存的物品图标
            JsonArray tagsArr = new JsonArray();
            JsonObject tagIcons = new JsonObject();
            BuiltInRegistries.ITEM.getTags().forEach(pair -> {
                var tagKey = pair.getFirst();
                ResourceLocation loc = tagKey.location();
                if (loc == null) return;
                String tagId = "#" + loc.getNamespace() + ":" + loc.getPath();
                tagsArr.add(tagId);
                // 找 tag 中第一个有缓存的物品
                var holderSet = BuiltInRegistries.ITEM.getTag(tagKey);
                if (holderSet.isPresent()) {
                    for (var holder : holderSet.get()) {
                        Item item = holder.value();
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                        if (itemId == null) continue;
                        String fileName = toSafeFileName(itemId) + ".png";
                        if (Files.exists(itemsDir.resolve(fileName))) {
                            tagIcons.addProperty(tagId, "/icon/cache/items/" + fileName);
                            break;
                        }
                    }
                }
            });
            root.add("tags", tagsArr);
            root.add("tagIcons", tagIcons);

            Files.writeString(manifestFile, GSON.toJson(root), StandardCharsets.UTF_8);
            LOGGER.info("[IconCache] manifest.json 已生成");

        } catch (Exception e) {
            LOGGER.error("[IconCache] 写入 manifest 失败", e);
            status = Status.ERROR;
            errorMessage = e.getMessage();
        }
    }

    public Status getStatus() {
        return status;
    }

    public int getProcessed() {
        return processed.get();
    }

    public int getTotal() {
        return total.get();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Path getCacheRoot() {
        return cacheRoot;
    }

    public Path getManifestFile() {
        return manifestFile;
    }

    /**
     * Create a simple colored placeholder PNG when icon rendering fails.
     * Public so ItemIconPngRenderer can call it directly as a fallback.
     */
    public static byte[] createPlaceholderPng(int size, int seed) {
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            // Simple color based on seed
            int r = (seed >> 16) & 0xFF;
            int g = (seed >> 8) & 0xFF;
            int b = seed & 0xFF;
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setPixelRGBA(x, y, color);
                }
            }
            // Encode as valid PNG via writeToChannel
            byte[] bytes = encodePng(image);
            image.close();
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        } catch (Exception e) {
            LOGGER.warn("[IconCache] Failed to create placeholder PNG", e);
        }
        return new byte[0];
    }

    /**
     * Encode NativeImage as valid PNG bytes via writeToFile (writeToChannel is private in NeoForge).
     */
    private static byte[] encodePng(NativeImage image) {
        try {
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("icon_", ".png");
            image.writeToFile(tempFile);
            byte[] result = java.nio.file.Files.readAllBytes(tempFile);
            java.nio.file.Files.deleteIfExists(tempFile);
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            LOGGER.warn("[IconCache] Failed to encode PNG via temp file", e);
        }
        return null;
    }
}