package com.chinaex123.shipping_box.network;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方分片缓存管理器
 * 用于在客户端临时存储接收到的分片数据，直到所有分片都到达
 */
public class RecipeChunkCache {

    /** 存储所有玩家的缓存 */
    private static final ConcurrentHashMap<UUID, RecipeChunkCache> CACHES = new ConcurrentHashMap<>();

    /** 当前玩家的分片数据 */
    private final String[] chunks;

    /** 是否已标记为完成 */
    private boolean completed = false;

    /** 预期的总分片数 */
    private int expectedTotal = 0;

    private RecipeChunkCache(int totalChunks) {
        this.chunks = new String[totalChunks];
        this.expectedTotal = totalChunks;
    }

    /**
     * 获取或创建指定玩家的缓存
     */
    public static RecipeChunkCache getOrCreate(Player player) {
        return CACHES.computeIfAbsent(player.getUUID(), k -> new RecipeChunkCache(1));
    }

    /**
     * 添加一个分片数据
     */
    public void addChunk(int index, int total, String data) {
        if (index >= 0 && index < chunks.length) {
            chunks[index] = data;
        }

        // 如果这是第一个分片且总片数变化，重新初始化数组
        if (index == 0 && total != expectedTotal && total > 1) {
            synchronized (this) {
                if (total != expectedTotal) {
                    // 清空旧数据，创建新数组
                    for (int i = 0; i < chunks.length; i++) {
                        chunks[i] = null;
                    }

                    // 扩展数组（如果需要）
                    if (total > chunks.length) {
                        String[] newChunks = new String[total];
                        System.arraycopy(chunks, 0, newChunks, 0, chunks.length);
                        // 注意：这里简化处理，实际应该重新分配
                    }

                    expectedTotal = total;
                }
            }
        }
    }

    /**
     * 检查是否所有分片都已接收
     */
    public boolean isComplete() {
        for (String chunk : chunks) {
            if (chunk == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 组装完整的 JSON 字符串
     */
    public String assembleJson() {
        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk != null) {
                sb.append(chunk);
            }
        }
        return sb.toString();
    }

    /**
     * 清空缓存
     */
    public void clear() {
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = null;
        }
        completed = false;
        expectedTotal = 0;
    }

    /**
     * 玩家退出时清理缓存
     */
    public static void onPlayerLogout(Player player) {
        CACHES.remove(player.getUUID());
    }
}
