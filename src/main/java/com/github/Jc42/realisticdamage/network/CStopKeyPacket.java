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
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

            boolean wasUpPressed = minecraft.options.keyUp.isDown();

            if(packet.key.toLowerCase().contains("sprint")) {
                minecraft.options.keySprint.setDown(false);
            }
            if(packet.key.toLowerCase().contains("movement")) {
                Vec3 lookVec = null;
//                if (minecraft.player != null && minecraft.options.keyUp.isDown()) {
//                    lookVec = minecraft.player.getLookAngle();
//
//                    Vec3 forwardVec = new Vec3(lookVec.x, 0, lookVec.z).normalize();
//
//                    double modifiedX = forwardVec.x;
//                    double modifiedZ = forwardVec.z;
//
//                    double speed = .2;
//
//                    new Thread(() -> {
//                        if (minecraft.player != null) {
//                            while (!Thread.currentThread().isInterrupted()) {
//                                if (Math.abs(minecraft.player.getDeltaMovement().y) < .02) {
//                                    // Player has reached the peak of their jump
//
//                                    minecraft.execute(() -> {
//                                        Vec3 finalVec = new Vec3((modifiedX * speed), minecraft.player.getDeltaMovement().y,  (modifiedZ * speed));
//                                        minecraft.player.setDeltaMovement(finalVec);
//                                    });
//                                    break;
//                                }
//                            }
//                        }
//                    }).start();
//                }
                if (minecraft.player != null && RealisticDamage.keyPacketHandled) {
                    double horizontalSpeed = Math.sqrt(Math.pow(minecraft.player.getDeltaMovement().x, 2) + Math.pow(minecraft.player.getDeltaMovement().z, 2));
                   if (horizontalSpeed > Objects.requireNonNull(minecraft.player.getAttribute(Attributes.MOVEMENT_SPEED)).getBaseValue()) {
                        minecraft.options.keyUp.setDown(false);

                       RealisticDamage.keyPacketHandled = false;


                        new Thread(() -> {
                            if (minecraft.player != null) {
                                while (!Thread.currentThread().isInterrupted()) {
                                    boolean isOnGround = minecraft.player.onGround();
                                    if (isOnGround) {
                                        // Player has hit the ground

                                        minecraft.execute(() -> {
                                            // Re-enable movement keys to their original state
                                            if (!RealisticDamage.releasedMovementKeyMidair) {
                                                minecraft.options.keyUp.setDown(wasUpPressed);
                                                RealisticDamage.keyPacketHandled = true;
                                            }

                                        });
                                        break;
                                    }
                                }
                            }
                        }).start();
                    }
                }



            }
        });
        context.setPacketHandled(true);
    }


}