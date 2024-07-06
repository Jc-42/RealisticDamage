package com.github.Jc42.realisticdamage.network;

import com.github.Jc42.realisticdamage.RealisticDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static int messageID = 0;

    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder.named(
            new ResourceLocation(RealisticDamage.MODID, "main"))
            .serverAcceptedVersions(s -> true)
            .clientAcceptedVersions(s -> true)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void register() {
        INSTANCE.messageBuilder(CPainLevelPacket.class, messageID++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CPainLevelPacket::encode)
                .decoder(CPainLevelPacket::new)
                .consumerMainThread(CPainLevelPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToPlayer(Object msg, Supplier<ServerPlayer> player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(player), msg);
    }

    public static void sendToAllClients(Object msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }

}
