package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.RealisticDamage;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

import java.util.function.Supplier;

public class PacketHandler {

    public static final Channel<CustomPacketPayload> CHANNEL =
            ChannelBuilder.named(Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "main"))
                    .optional() // accepts vanilla/missing connections on both sides, like the old accept-anything predicates
                    .payloadChannel()
                    .play()
                    .bidirectional()
                    .add(PainLevelPacket.TYPE, PainLevelPacket.STREAM_CODEC, PainLevelPacket::handle)
                    .add(StopKeyPacket.TYPE, StopKeyPacket.STREAM_CODEC, StopKeyPacket::handle)
                    .build();

    public static void register() {
        //invoking a static method on a class triggers its initialization per the JLS
    }

    public static void sendToServer(CustomPacketPayload msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }

    public static void sendToPlayer(CustomPacketPayload msg, Supplier<ServerPlayer> player) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player.get()));
    }

    public static void sendToAllClients(CustomPacketPayload msg) {
        CHANNEL.send(msg, PacketDistributor.ALL.noArg());
    }
}
