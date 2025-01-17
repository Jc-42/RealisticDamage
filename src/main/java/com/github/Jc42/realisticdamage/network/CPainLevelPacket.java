package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.PainCapabilityProvider;
import com.github.Jc42.realisticdamage.Wound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;


public class CPainLevelPacket {
    //private float CHRONIC_PAIN_LEVEL;
    private float ADRENALINE_LEVEL;
    private ArrayList<Wound> WOUNDS;

    public CPainLevelPacket(/*float chronicPainLevel,*/ float adrenalineLevel, ArrayList<Wound> wounds) {
        //this.CHRONIC_PAIN_LEVEL = chronicPainLevel;
        this.ADRENALINE_LEVEL = adrenalineLevel;
        this.WOUNDS = wounds;
    }

    public void encode(FriendlyByteBuf buffer) {
        //buffer.writeFloat(this.CHRONIC_PAIN_LEVEL);
        buffer.writeFloat(this.ADRENALINE_LEVEL);

        // Write the size of the wounds list
        buffer.writeInt(WOUNDS.size());
        // Write each wound's data to the buffer
        for (Wound wound : WOUNDS) {
            buffer.writeNbt(wound.serializeNBT());
        }
    }

    public CPainLevelPacket(FriendlyByteBuf buffer) {
        this(/*buffer.readFloat(),*/ buffer.readFloat(), readWounds(buffer));
    }

    private static ArrayList<Wound> readWounds(FriendlyByteBuf buffer) {
        int woundCount = buffer.readInt();
        ArrayList<Wound> wounds = new ArrayList<>();
        for (int i = 0; i < woundCount; i++) {
            Wound wound = new Wound("Laceration", 1, "Head");
            wound.deserializeNBT(buffer.readNbt());
            wounds.add(wound);
        }
        return wounds;
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
                    //pain.setChronicPainLevel(packet.CHRONIC_PAIN_LEVEL);
                    pain.setAdrenalineLevel(packet.ADRENALINE_LEVEL);

                    // Clear existing wounds and add the ones from the packet
                    pain.getWounds().clear();
                    pain.getWounds().addAll(packet.WOUNDS);
                });
            }
        });
    }
}
