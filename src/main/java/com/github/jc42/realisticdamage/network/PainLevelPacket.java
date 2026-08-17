package com.github.jc42.realisticdamage.network;

import com.github.jc42.realisticdamage.PainCapability;
import com.github.jc42.realisticdamage.RealisticDamage;
import com.github.jc42.realisticdamage.Wound;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;


public class PainLevelPacket implements CustomPacketPayload {
    public static final Type<PainLevelPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "pain_level"));

    public static final StreamCodec<ByteBuf, PainLevelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    p -> p.ADRENALINE_LEVEL,
                    Wound.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    p -> p.WOUNDS,
                    PainLevelPacket::new
            );

    //private float CHRONIC_PAIN_LEVEL;
    private float ADRENALINE_LEVEL;
    private List<Wound> WOUNDS;

    public PainLevelPacket(/*float chronicPainLevel,*/ float adrenalineLevel, List<Wound> wounds) {
        //this.CHRONIC_PAIN_LEVEL = chronicPainLevel;
        this.ADRENALINE_LEVEL = adrenalineLevel;
        this.WOUNDS = wounds;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PainLevelPacket packet, IPayloadContext context) {
        handleClientSide(packet);
    }

    private static void handleClientSide(PainLevelPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            PainCapability pain = minecraft.player.getData(RealisticDamage.PAIN);
            //pain.setChronicPainLevel(packet.CHRONIC_PAIN_LEVEL);
            pain.setAdrenalineLevel(packet.ADRENALINE_LEVEL);
            // Clear existing wounds and add the ones from the packet
            pain.getWounds().clear();
            pain.getWounds().addAll(packet.WOUNDS);
        }
    }
}