package com.chinaex123.client;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    /** 客户端玩家登出事件处理器 **/
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSoldCountCache.clearCache();
    }

    /** 客户端 tick 驱动缓存任务 **/
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EditorIconCacheManager.getInstance().tick();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal(ShippingBox.MOD_ID)
                .then(Commands.literal("editor")
                        .then(Commands.literal("cache_icons")
                                .executes(ctx -> {
                                    EditorIconCacheManager.getInstance().startCache(false);
                                    ctx.getSource().sendSuccess(() ->
                                                    Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_icons.started"),
                                            false);
                                    return 1;
                                })
                                .then(Commands.literal("force")
                                        .executes(ctx -> {
                                            EditorIconCacheManager.getInstance().startCache(true);
                                            ctx.getSource().sendSuccess(() ->
                                                            Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_icons.force_started"),
                                                    false);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("cache_status")
                                .executes(ctx -> {
                                    var mgr = EditorIconCacheManager.getInstance();
                                    String status = mgr.getStatus().name();
                                    int processed = mgr.getProcessed();
                                    int total = mgr.getTotal();
                                    String errorMsg = mgr.getErrorMessage();

                                    Component message;
                                    if (errorMsg != null) {
                                        message = Component.translatable(
                                                "command." + ShippingBox.MOD_ID + ".editor.cache_status.with_error",
                                                status, processed, total, errorMsg
                                        );
                                    } else {
                                        message = Component.translatable(
                                                "command." + ShippingBox.MOD_ID + ".editor.cache_status",
                                                status, processed, total
                                        );
                                    }

                                    ctx.getSource().sendSuccess(() -> message, false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("cache_clear")
                                .executes(ctx -> {
                                    EditorIconCacheManager.getInstance().clearCache();
                                    ctx.getSource().sendSuccess(() ->
                                                    Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_clear.success"),
                                            false);
                                    return 1;
                                })
                        )
                )
        );
    }
}