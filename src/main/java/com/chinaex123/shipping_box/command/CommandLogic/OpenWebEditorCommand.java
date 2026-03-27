package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.network.PacketStartLocalWebEditor;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.security.SecureRandom;
import java.util.Base64;

public class OpenWebEditorCommand {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new PacketStartLocalWebEditor(generateToken()));
        player.displayClientMessage(Component.literal("已请求在本机启动 Web 编辑器并打开浏览器。"), false);

        return 1;
    }

    private static String generateToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
