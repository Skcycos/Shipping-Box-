package com.chinaex123.shipping_box.common.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class BalanceAnimationManager {

    private static final Map<UUID, AnimationState> animationStates = new HashMap<>();

    private static class AnimationState {
        final int startBalance;
        final int totalValue;
        final int exchangeAmount;
        int currentStep = 0;
        final int maxSteps = 20;

        AnimationState(int startBalance, int totalValue, int exchangeAmount) {
            this.startBalance = startBalance;
            this.totalValue = totalValue;
            this.exchangeAmount = exchangeAmount;
        }
    }

    public static void startAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    public static void tick(MinecraftServer server) {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, AnimationState> entry : animationStates.entrySet()) {
            UUID playerId = entry.getKey();
            AnimationState state = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                toRemove.add(playerId);
                continue;
            }

            int currentBalance = state.startBalance + (int) ((state.totalValue / 20.0) * state.currentStep);
            int increment = state.totalValue;

            player.displayClientMessage(Component.translatable("message.shipping_box.viscriptshop.balance_animation",
                    currentBalance,
                    increment), true);

            state.currentStep++;

            if (state.currentStep > state.maxSteps) {
                toRemove.add(playerId);
            }
        }

        for (UUID playerId : toRemove) {
            animationStates.remove(playerId);
        }
    }
}
