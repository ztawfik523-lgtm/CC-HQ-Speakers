package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HQFiniteMediaEndPacket(UUID source, UUID mediaId, long generation) implements CustomPacketPayload {
    public static final Type<HQFiniteMediaEndPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaEndPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUUID(p.source()); buf.writeUUID(p.mediaId()); buf.writeVarLong(p.generation()); },
        buf -> new HQFiniteMediaEndPacket(buf.readUUID(), buf.readUUID(), buf.readVarLong())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaEndPacket packet, IPayloadContext context) {
        if (packet.source() != null && packet.mediaId() != null && packet.generation() > 0L) HQFiniteMediaClient.end(packet);
    }
}
