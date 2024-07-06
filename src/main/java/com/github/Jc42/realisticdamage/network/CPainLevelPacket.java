package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.PainCapabilityProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class CPainLevelPacket {
    private final float CHRONIC_PAIN_LEVEL;
    private final float ADRENALINE_LEVEL;

    public CPainLevelPacket(float chronicPainLevel, float adrenalineLevel) {
        this.CHRONIC_PAIN_LEVEL = chronicPainLevel;
        this.ADRENALINE_LEVEL = adrenalineLevel;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.CHRONIC_PAIN_LEVEL);
        buffer.writeFloat(this.ADRENALINE_LEVEL);
    }

    public CPainLevelPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(CPainLevelPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleClientSide(packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleClientSide(CPainLevelPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    pain.setChronicPainLevel(packet.CHRONIC_PAIN_LEVEL);
                    pain.setAdrenalineLevel(packet.ADRENALINE_LEVEL);
                });
            }
        });
    }

}
