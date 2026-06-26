package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 客户端事件监听
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    /**
     * 玩家登出事件监听器 (客户端)
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSoldCountCache.clearCache();
    }

    /**
     * 客户端 tick 驱动缓存任务
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EditorIconCacheManager.getInstance().tick();
    }

    /**
     * 注册客户端专用命令（editor 缓存相关）
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal(ShippingBox.MOD_ID)
            .then(Commands.literal("editor")
                .then(Commands.literal("cache_icons")
                    .executes(ctx -> {
                        EditorIconCacheManager.getInstance().startCache(false);
                        ctx.getSource().sendSuccess(() -> Component.literal("Icon cache task started (or already running). Use /" + ShippingBox.MOD_ID + " editor cache_status to check."), false);
                        return 1;
                    })
                    .then(Commands.literal("force")
                        .executes(ctx -> {
                            EditorIconCacheManager.getInstance().startCache(true);
                            ctx.getSource().sendSuccess(() -> Component.literal("Force icon cache regeneration started."), false);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("cache_status")
                    .executes(ctx -> {
                        var mgr = EditorIconCacheManager.getInstance();
                        StringBuilder sb = new StringBuilder("Icon cache status: ");
                        sb.append(mgr.getStatus()).append(" (").append(mgr.getProcessed()).append("/").append(mgr.getTotal()).append(")");
                        if (mgr.getErrorMessage() != null) sb.append(" error: ").append(mgr.getErrorMessage());
                        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                        return 1;
                    })
                )
                .then(Commands.literal("cache_clear")
                    .executes(ctx -> {
                        EditorIconCacheManager.getInstance().clearCache();
                        ctx.getSource().sendSuccess(() -> Component.literal("Icon cache cleared."), false);
                        return 1;
                    })
                )
            )
        );
    }
}
