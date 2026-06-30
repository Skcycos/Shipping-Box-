package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.common.network.PacketExchangeEffects;
import com.chinaex123.shipping_box.common.network.PacketShowSuccessMessage;
import com.chinaex123.shipping_box.common.network.PacketSoldCountSync;
import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PacketHandlers {

    public static void handleShowSuccess(PacketShowSuccessMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                context.player().displayClientMessage(
                        Component.translatable("message.shipping_box.exchange_success"),
                        true
                );
            }
        }).exceptionally(e -> null);
    }

    public static void handleSoldCountSync(PacketSoldCountSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientSoldCountCache.updateCache(packet.itemIdentifier(), packet.soldCount());
        });
    }

    public static void handleExchangeEffects(PacketExchangeEffects packet, IPayloadContext context) {
        if (!CommonConfig.ENABLE_EXCHANGE_EFFECTS.get()) {
            return;
        }

        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                spawnMagicCircleEffects(player);
            }
        });
    }

    private static void spawnMagicCircleEffects(Player player) {
        RandomSource random = player.level().random;
        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();

        for (int i = 0; i < 150; i++) {
            double startX = centerX + (random.nextDouble() - 0.5) * 0.3;
            double startY = centerY + random.nextDouble() * 0.5;
            double startZ = centerZ + (random.nextDouble() - 0.5) * 0.3;

            double speed = 0.3 + random.nextDouble() * 0.4;
            double angle = random.nextDouble() * 2 * Math.PI;
            double horizontalSpeed = speed * 0.7;
            double verticalSpeed = speed * 0.8 + random.nextDouble() * 0.3;

            double motionX = Math.cos(angle) * horizontalSpeed;
            double motionY = verticalSpeed;
            double motionZ = Math.sin(angle) * horizontalSpeed;

            player.level().addParticle(ParticleTypes.WAX_ON, startX, startY, startZ, motionX, motionY, motionZ);

            if (i % 5 == 0) {
                player.level().addParticle(ParticleTypes.END_ROD, startX, startY, startZ, motionX, motionY, motionZ);
            }
        }

        for (int ring = 0; ring < 4; ring++) {
            int particlesPerRing = 100;
            double ringRadius = 1.8 + ring * 0.6;

            for (int i = 0; i < particlesPerRing; i++) {
                double angle = (i / (double) particlesPerRing) * 2 * Math.PI;
                double spiralOffset = ring * 0.5;
                angle += spiralOffset;

                double startY = centerY + 1.2 + ring * 0.4;
                double startX = centerX + Math.cos(angle) * ringRadius * 0.2;
                double startZ = centerZ + Math.sin(angle) * ringRadius * 0.2;

                double motionX = Math.cos(angle) * 0.25;
                double motionY = 0.08 + random.nextDouble() * 0.12;
                double motionZ = Math.sin(angle) * 0.25;

                if (i % 3 == 0) {
                    player.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING, startX, startY, startZ, motionX, motionY, motionZ);
                } else if (i % 3 == 1) {
                    player.level().addParticle(ParticleTypes.END_ROD, startX, startY, startZ, motionX, motionY, motionZ);
                } else {
                    player.level().addParticle(ParticleTypes.WAX_ON, startX, startY, startZ, motionX, motionY, motionZ);
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 2.5;

            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + 1.5 + random.nextDouble() * 1.5;
            double z = centerZ + Math.sin(angle) * radius;

            double motionX = (random.nextDouble() - 0.5) * 0.1;
            double motionY = -0.05 - random.nextDouble() * 0.1;
            double motionZ = (random.nextDouble() - 0.5) * 0.1;

            if (i % 4 == 0) {
                player.level().addParticle(ParticleTypes.WAX_OFF, x, y, z, motionX, motionY, motionZ);
            } else {
                player.level().addParticle(ParticleTypes.GLOW, x, y, z, motionX, motionY, motionZ);
            }
        }
    }
}
