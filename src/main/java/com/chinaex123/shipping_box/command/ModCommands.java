package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipping_box")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("force_exchange")
                        .executes(ModCommands::executeForceExchange)));
    }

    private static int executeForceExchange(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            
            // 先获取玩家实体
            var entity = source.getEntity();
            if (!(entity instanceof ServerPlayer player)) {
                source.sendFailure(Component.translatable("command.shipping_box.not_player")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            // 获取玩家视线指向的方块（最大距离 20 方块）
            HitResult hitResult = player.pick(20.0D, 0.0F, false);
            
            if (hitResult.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("command.shipping_box.no_block_target")
                        .withStyle(ChatFormatting.RED), true);
                return 0;
            }

            BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
            BlockEntity blockEntity = player.level().getBlockEntity(pos);

            if (blockEntity instanceof ShippingBoxBlockEntity shippingBox) {
                shippingBox.forceExchange();
                player.displayClientMessage(Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString())
                        .withStyle(ChatFormatting.GOLD), true);
                return 1;
            } else if (blockEntity instanceof AutoShippingBoxBlockEntity autoShippingBox) {
                autoShippingBox.forceExchange();
                player.displayClientMessage(Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString())
                        .withStyle(ChatFormatting.GOLD), true);
                return 1;
            } else {
                player.displayClientMessage(Component.translatable("command.shipping_box.invalid_block_entity")
                        .withStyle(ChatFormatting.RED), true);
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error executing command: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
