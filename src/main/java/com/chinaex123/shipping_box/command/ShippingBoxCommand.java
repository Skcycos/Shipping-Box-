package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ShippingBoxCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shippingbox")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("debug")
                .then(Commands.literal("exchange")
                    .executes(ShippingBoxCommand::executeExchange)
                )
            )
        );
    }

    private static int executeExchange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof Player)) {
            source.sendFailure(Component.literal("Only players can execute this command."));
            return 0;
        }

        Player player = (Player) source.getEntity();
        ServerLevel level = source.getLevel();
        
        // Ray trace to find the block the player is looking at
        HitResult hitResult = player.pick(20.0D, 0.0F, false);
        
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            
            boolean triggered = false;
            
            if (blockEntity instanceof ShippingBoxBlockEntity) {
                ((ShippingBoxBlockEntity) blockEntity).forceExchange();
                triggered = true;
            } else if (blockEntity instanceof AutoShippingBoxBlockEntity) {
                ((AutoShippingBoxBlockEntity) blockEntity).forceExchange();
                triggered = true;
            }
            
            if (triggered) {
                source.sendSuccess(() -> Component.literal("Successfully triggered exchange for Shipping Box at " + pos.toShortString()), true);
                return 1;
            }
        }
        
        source.sendFailure(Component.literal("You must be looking at a Shipping Box."));
        return 0;
    }
}
