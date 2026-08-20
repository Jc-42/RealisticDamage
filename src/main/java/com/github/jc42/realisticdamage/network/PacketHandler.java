package com.github.jc42.realisticdamage.network;

import com.github.jc42.realisticdamage.RealisticDamage;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Supplier;

@EventBusSubscriber(modid = RealisticDamage.MODID)
public class PacketHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(PainLevelPacket.TYPE, PainLevelPacket.STREAM_CODEC);
        registrar.playToClient(StopKeyPacket.TYPE, StopKeyPacket.STREAM_CODEC);
        registrar.playToServer(ApplyBandagePacket.TYPE, ApplyBandagePacket.STREAM_CODEC, ApplyBandagePacket::handle);
    }

    public static void sendToPlayer(CustomPacketPayload msg, Supplier<ServerPlayer> player) {
        PacketDistributor.sendToPlayer(player.get(), msg);
    }

    public static void sendToAllClients(CustomPacketPayload msg) {
        PacketDistributor.sendToAllPlayers(msg);
    }

    @EventBusSubscriber(modid = RealisticDamage.MODID, value = Dist.CLIENT)
    public static class Client {

        @SubscribeEvent
        public static void registerClientHandlers(RegisterClientPayloadHandlersEvent event) {
            event.register(PainLevelPacket.TYPE, PainLevelPacket::handle);
            event.register(StopKeyPacket.TYPE, StopKeyPacket::handle);
        }

        public static void sendToServer(CustomPacketPayload msg) {
            ClientPacketDistributor.sendToServer(msg);
        }
    }
}