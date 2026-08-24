package com.github.jc42.realisticdamage.network;

import com.github.jc42.realisticdamage.PainCapability;
import com.github.jc42.realisticdamage.RealisticDamage;
import com.github.jc42.realisticdamage.Wound;
import com.github.jc42.realisticdamage.item.Bandage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ApplyBandagePacket implements CustomPacketPayload {
    public static final Type<ApplyBandagePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "apply_bandage"));

    public static final StreamCodec<ByteBuf, ApplyBandagePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    p -> p.woundIndex,
                    ApplyBandagePacket::new
            );

    private final int woundIndex;

    public ApplyBandagePacket(int woundIndex) {
        this.woundIndex = woundIndex;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApplyBandagePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        ItemStack heldStack = player.getMainHandItem();
        if (!(heldStack.getItem() instanceof Bandage bandage)) return;

        PainCapability pain = player.getData(RealisticDamage.PAIN);
        if (packet.woundIndex < 0 || packet.woundIndex >= pain.getWounds().size()) return;

        Wound wound = pain.getWounds().get(packet.woundIndex);
        Bandage existingBandage = wound.getAppliedBandage();

        //The wound screen only lets the client send this once the player has confirmed a valid replacement
        //(or there's no existing bandage), but re-check server-side in case of desync - a higher tier bandage
        //is required to replace one that's already applied, and the old bandage is simply discarded.
        if (existingBandage != null && bandage.getTier() <= existingBandage.getTier()) return;

        wound.setAppliedBandage(bandage);
        heldStack.consume(1, player);
        //TODO apply the bandage's actual effects to the wound (healing, bleed reduction, etc.)

        PacketDistributor.sendToPlayer(player, new PainLevelPacket(pain.getAdrenalineLevel(), pain.getWounds()));
    }
}
