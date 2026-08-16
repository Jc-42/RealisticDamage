package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.RealisticDamage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.network.CustomPayloadEvent;
import java.util.Objects;

public class StopKeyPacket implements CustomPacketPayload {
    public static final Type<StopKeyPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "stop_key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StopKeyPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> packet.encode(buf),
                    StopKeyPacket::new
            );

    private final String key;

    public StopKeyPacket(String key) {
        this.key = key;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.key);
    }

    public StopKeyPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StopKeyPacket packet, CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            if (context.isClientSide()) {
                handleClientSide(packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleClientSide(StopKeyPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            boolean wasUpPressed = minecraft.options.keyUp.isDown();

            if (packet.key.toLowerCase().contains("sprint")) {
                minecraft.options.keySprint.setDown(false);
            }
            if (packet.key.toLowerCase().contains("movement")) {
                if (RealisticDamage.keyPacketHandled) {
                    double horizontalSpeed = Math.sqrt(Math.pow(minecraft.player.getDeltaMovement().x, 2) + Math.pow(minecraft.player.getDeltaMovement().z, 2));
                    if (horizontalSpeed > Objects.requireNonNull(minecraft.player.getAttribute(Attributes.MOVEMENT_SPEED)).getBaseValue()) {
                        minecraft.options.keyUp.setDown(false);
                        RealisticDamage.keyPacketHandled = false;

                        new Thread(() -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                boolean isOnGround = minecraft.player.onGround();
                                if (isOnGround) {
                                    minecraft.execute(() -> {
                                        if (!RealisticDamage.releasedMovementKeyMidair) {
                                            minecraft.options.keyUp.setDown(wasUpPressed);
                                            RealisticDamage.keyPacketHandled = true;
                                        }
                                    });
                                    break;
                                }
                            }
                        }).start();
                    }
                }
            }
            if (packet.key.toLowerCase().contains("use")) {
                //Only cancel if the player isn't in any GUI's
                if (minecraft.screen == null) {
                    minecraft.options.keyUse.setDown(false);
                }
            }
            if (packet.key.toLowerCase().contains("jump")) {
                minecraft.options.keyJump.setDown(false);
            }
        }
    }
}