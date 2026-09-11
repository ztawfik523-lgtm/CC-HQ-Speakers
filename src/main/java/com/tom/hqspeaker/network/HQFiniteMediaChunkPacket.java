package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HQFiniteMediaChunkPacket(
    UUID source, UUID mediaId, long generation, long offset, byte[] data
) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final Type<HQFiniteMediaChunkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaChunkPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQFiniteMediaChunkPacket decode(RegistryFriendlyByteBuf buf) {
                UUID source = buf.readUUID();
                UUID mediaId = buf.readUUID();
                long generation = buf.readVarLong();
                long offset = buf.readVarLong();
                int length = buf.readVarInt();
                if (length < 0 || length > MAX_CHUNK_BYTES) throw new IllegalArgumentException("invalid finite media chunk size " + length);
                byte[] data = new byte[length];
                buf.readBytes(data);
                return new HQFiniteMediaChunkPacket(source, mediaId, generation, offset, data);
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaChunkPacket p) {
                if (p.data() == null || p.data().length == 0 || p.data().length > MAX_CHUNK_BYTES) {
                    throw new IllegalArgumentException("invalid finite media chunk");
                }
                buf.writeUUID(p.source());
                buf.writeUUID(p.mediaId());
                buf.writeVarLong(p.generation());
                buf.writeVarLong(p.offset());
                buf.writeVarInt(p.data().length);
                buf.writeBytes(p.data());
            }
        };

    public boolean sensible() {
        return source != null && mediaId != null && generation > 0L && offset >= 0L
            && data != null && data.length > 0 && data.length <= MAX_CHUNK_BYTES;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaChunkPacket packet, IPayloadContext context) {
        if (packet.sensible()) HQFiniteMediaClient.chunk(packet);
    }
}
