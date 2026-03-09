package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketExchangeEffects(int amount) implements CustomPacketPayload {
    public static final Type<PacketExchangeEffects> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "exchange_effects")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketExchangeEffects> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PacketExchangeEffects::amount,
                    PacketExchangeEffects::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketExchangeEffects packet, IPayloadContext context) {
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
        
        // Spawn a large number of particles immediately to create a "burst" effect
        // that lasts visually. For a 5-second sustained effect, we would ideally need
        // a client-side ticker, but spawning enough particles with motion can simulate it.
        // However, standard particles die quickly.
        
        // Strategy: Spawn a lot of particles that spiral inwards
        for (int i = 0; i < 200; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 2.0 + random.nextDouble() * 2.0;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double y = centerY + random.nextDouble() * 2.0;
            
            // Motion towards center
            double motionX = (centerX - x) * 0.05;
            double motionY = (centerY + 1.0 - y) * 0.05;
            double motionZ = (centerZ - z) * 0.05;

            // Use end_rod for magical feel, and totem_of_undying for "money/gold" feel
            player.level().addParticle(ParticleTypes.END_ROD, x, y, z, motionX, motionY, motionZ);
            
            if (i % 3 == 0) {
                 player.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, motionX, motionY, motionZ);
            }
        }
        
        // Spawn "money" particles directly on the player
        for (int i = 0; i < 50; i++) {
            player.level().addParticle(ParticleTypes.WAX_ON, 
                centerX + (random.nextDouble() - 0.5), 
                centerY + random.nextDouble() * 2, 
                centerZ + (random.nextDouble() - 0.5), 
                0, 0.1, 0);
        }
    }
}
