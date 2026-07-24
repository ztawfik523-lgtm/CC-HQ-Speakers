package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class HQSpeakerNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(HQSpeakerAudioPacket.TYPE, HQSpeakerAudioPacket.STREAM_CODEC, HQSpeakerAudioPacket::handle);
        registrar.playToClient(HQSpeakerStopPacket.TYPE, HQSpeakerStopPacket.STREAM_CODEC, HQSpeakerStopPacket::handle);
        registrar.playToServer(IcyMetaPacket.TYPE, IcyMetaPacket.STREAM_CODEC, IcyMetaPacket::handle);

        HQSpeakerMod.log("Network registered with 3 payloads.");
    }

    public static void sendToPlayer(HQSpeakerAudioPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(HQSpeakerStopPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(CustomPacketPayload packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(IcyMetaPacket packet) {
        PacketDistributor.sendToServer(packet);
    }
}
