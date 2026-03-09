package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 售货箱网络通信管理类
 * 处理多人协作时的数据同步
 */
public class ShippingBoxNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShippingBoxNetworking.class);

    /**
     * 发送销售计数同步到所有客户端玩家
     * 使用服务器广播方法，性能优于遍历方式
     *
     * @param itemIdentifier 物品标识符
     * @param soldCount 销售数量
     */
    public static void sendSoldCountSync(String itemIdentifier, int soldCount) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SoldCountSyncPacket payload = new SoldCountSyncPacket(itemIdentifier, soldCount);
            Packet<?> packet = new ClientboundCustomPayloadPacket(payload);
            // 使用服务器原生广播方法
            server.getPlayerList().broadcastAll(packet);
        }
    }

    /**
     * 显示成功消息的数据包记录类
     * 用于在网络上传输兑换成功的通知消息
     */
    public record ShowSuccessMessage() implements CustomPacketPayload {
        public static final Type<ShowSuccessMessage> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
        );

        public static final StreamCodec<FriendlyByteBuf, ShowSuccessMessage> STREAM_CODEC =
                StreamCodec.unit(new ShowSuccessMessage());

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 玩家放置物品的数据包记录类
     * 用于在网络上传输玩家在售货箱中放置物品的信息
     *
     * @param pos 方块位置
     * @param slot 槽位索引
     */
    public record PlayerPlaceItem(BlockPos pos, int slot) implements CustomPacketPayload {
        public static final Type<PlayerPlaceItem> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "player_place_item")
        );

        public static final StreamCodec<FriendlyByteBuf, PlayerPlaceItem> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, PlayerPlaceItem::pos,
                        ByteBufCodecs.INT, PlayerPlaceItem::slot,
                        PlayerPlaceItem::new
                );

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 配方同步数据包记录类
     * 用于将服务端的兑换配方同步到客户端
     * 支持分段发送以避免字符串超长问题
     *
     * @param rulesJson 配方规则列表的 JSON 字符串表示
     * @param chunkIndex 当前分片索引（从 0 开始）
     * @param totalChunks 总分片数量
     */
    public record SyncRecipes(String rulesJson, int chunkIndex, int totalChunks) implements CustomPacketPayload {
        public static final Type<SyncRecipes> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sync_recipes")
        );

        public static final StreamCodec<FriendlyByteBuf, SyncRecipes> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SyncRecipes::rulesJson,
                        ByteBufCodecs.INT, SyncRecipes::chunkIndex,
                        ByteBufCodecs.INT, SyncRecipes::totalChunks,
                        SyncRecipes::new
                );

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 注册网络数据包处理器
     *
     * @param event 负载处理器注册事件
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ShippingBox.MOD_ID);

        // 注册成功提示消息数据包
        registrar.playToClient(
                ShowSuccessMessage.TYPE,
                ShowSuccessMessage.STREAM_CODEC,
                ShippingBoxNetworking::handleShowSuccessMessage
        );

        // 注册玩家放置物品数据包
        registrar.playToServer(
                PlayerPlaceItem.TYPE,
                PlayerPlaceItem.STREAM_CODEC,
                ShippingBoxNetworking::handlePlayerPlaceItem
        );

        // 注册配方同步数据包
        registrar.playToClient(
                SyncRecipes.TYPE,
                SyncRecipes.STREAM_CODEC,
                ShippingBoxNetworking::handleSyncRecipes
        );

        // 注册新的销售计数同步数据包
        registrar.playToClient(
                SoldCountSyncPacket.TYPE,
                SoldCountSyncPacket.STREAM_CODEC,
                SoldCountSyncPacket::handle
        );
    }

    /**
     * 处理配方同步数据包
     * 在客户端接收并应用服务端发送的配方规则
     *
     * @param packet 配方同步数据包
     * @param context 网络上下文
     */
    private static void handleSyncRecipes(SyncRecipes packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                // 检查是否是分段数据
                if (packet.totalChunks() > 1) {
                    // 使用缓存来存储接收到的分片
                    RecipeChunkCache cache = RecipeChunkCache.getOrCreate(context.player());
                    cache.addChunk(packet.chunkIndex(), packet.totalChunks(), packet.rulesJson());

                    // 检查是否已接收所有分片
                    if (cache.isComplete()) {
                        String completeJson = cache.assembleJson();
                        ExchangeRecipeManager.setClientRules(completeJson);
                        cache.clear();
                    }
                } else {
                    // 单个数据包，直接应用
                    ExchangeRecipeManager.setClientRules(packet.rulesJson());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to apply recipe sync: {}", e.getMessage());
            }
        }).exceptionally(e -> null);
    }

    /**
     * 处理显示成功消息的数据包
     * 在客户端玩家界面显示兑换成功的提示信息
     *
     * @param packet 成功消息数据包
     * @param context 网络上下文
     */
    private static void handleShowSuccessMessage(ShowSuccessMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端显示成功消息
            context.player().displayClientMessage(
                    Component.translatable("message.shipping_box.exchange_success"),
                    true // 在行动栏显示
            );
        }).exceptionally(e -> null);
    }

    /**
     * 处理玩家放置物品的数据包
     * 记录玩家在指定槽位放置物品的所有权信息
     *
     * @param packet 玩家放置物品数据包
     * @param context 网络上下文
     */
    private static void handlePlayerPlaceItem(PlayerPlaceItem packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(packet.pos()) instanceof ShippingBoxBlockEntity box) {
                box.setSlotOwner(packet.slot(), context.player().getUUID());
            }
        });
    }

    /**
     * 向单个客户端玩家同步配方规则
     * 使用分段发送机制避免字符串超长问题
     *
     * @param player 目标玩家
     */
    public static void syncRecipesToClient(ServerPlayer player) {
        try {
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();

            // Minecraft 网络包字符串最大长度：32767 字符
            // 为了安全起见，我们限制在 30000 字符
            final int MAX_CHUNK_SIZE = 30000;

            if (rulesJson.length() <= MAX_CHUNK_SIZE) {
                // 如果规则较小，直接发送完整数据
                SyncRecipes packet = new SyncRecipes(rulesJson, 0, 1);
                PacketDistributor.sendToPlayer(player, packet);
            } else {
                // 分段发送
                int totalChunks = (int) Math.ceil((double) rulesJson.length() / MAX_CHUNK_SIZE);

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * MAX_CHUNK_SIZE;
                    int end = Math.min(start + MAX_CHUNK_SIZE, rulesJson.length());
                    String chunk = rulesJson.substring(start, end);

                    SyncRecipes packet = new SyncRecipes(chunk, i, totalChunks);
                    PacketDistributor.sendToPlayer(player, packet);

                    // 短暂延迟，避免网络拥塞
                    Thread.sleep(10);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to sync recipes to client: {}", e.getMessage());
        }
    }
}
