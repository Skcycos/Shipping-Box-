package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.command.CommandLogic.ForceExchangeCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 注册主命令
        dispatcher.register(Commands.literal("shipping_box")
                .requires(source -> source.hasPermission(2))

                // 子命令：force_exchange - 强制兑换
                .then(Commands.literal("force_exchange")
                        .executes(ForceExchangeCommand::execute)));
    }
}
