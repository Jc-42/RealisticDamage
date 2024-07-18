package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.PainCapabilityProvider;
import com.github.Jc42.realisticdamage.RealisticDamage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class CStopKeyPacket {
    private final String key;

    public CStopKeyPacket(String key) {
        this.key = key;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.key);
    }

    public CStopKeyPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    public static void handle(CStopKeyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleClientSide(packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleClientSide(CStopKeyPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
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
                if (packet.key.toLowerCase().contains("attack")) {
                    //Only cancel if the player isn't in any GUI's
                    if (minecraft.screen == null) {
                        minecraft.options.keyAttack.setDown(false);
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
        });
    }
}