package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
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
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.translatable("command.shipping_box.not_player"));
                return 0;
            }

            // 获取玩家视线指向的方块（最大距离 20 方块）
            HitResult hitResult = player.pick(20.0D, 0.0F, false);
            
            if (hitResult.getType() != HitResult.Type.BLOCK) {
                source.sendFailure(Component.translatable("command.shipping_box.no_block_target"));
                return 0;
            }

            BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
            BlockEntity blockEntity = player.level().getBlockEntity(pos);

            if (blockEntity instanceof ShippingBoxBlockEntity shippingBox) {
                shippingBox.forceExchange();
                source.sendSuccess(() -> Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString()), true);
                return 1;
            } else if (blockEntity instanceof AutoShippingBoxBlockEntity autoShippingBox) {
                autoShippingBox.forceExchange();
                source.sendSuccess(() -> Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString()), true);
                return 1;
            } else {
                source.sendFailure(Component.translatable("command.shipping_box.invalid_block_entity"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error executing command: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
