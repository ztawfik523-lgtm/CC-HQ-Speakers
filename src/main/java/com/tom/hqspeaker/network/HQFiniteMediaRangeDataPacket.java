package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import com.tom.hqspeaker.media.FiniteRangeLimits;
import com.tom.hqspeaker.media.FiniteRangeValidation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Server response containing exactly one bounded encoded region of the active prepared asset. */
public record HQFiniteMediaRangeDataPacket(
    UUID source, UUID assetId, long generation, long offset, byte[] data
) implements CustomPacketPayload {
    public static final Type<HQFiniteMediaRangeDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_range_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaRangeDataPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQFiniteMediaRangeDataPacket decode(RegistryFriendlyByteBuf buf) {
                UUID source = buf.readUUID();
                UUID assetId = buf.readUUID();
                long generation = buf.readVarLong();
                long offset = buf.readVarLong();
                int length = buf.readVarInt();
                if (length <= 0 || length > FiniteRangeLimits.MAX_RANGE_BYTES) {
                    throw new IllegalArgumentException("invalid finite range response length " + length);
                }
                byte[] data = new byte[length];
                buf.readBytes(data);
                return new HQFiniteMediaRangeDataPacket(source, assetId, generation, offset, data);
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaRangeDataPacket packet) {
                if (!packet.sensible()) throw new IllegalArgumentException("invalid finite range response");
                buf.writeUUID(packet.source());
                buf.writeUUID(packet.assetId());
                buf.writeVarLong(packet.generation());
                buf.writeVarLong(packet.offset());
                buf.writeVarInt(packet.data().length);
                buf.writeBytes(packet.data());
            }
        };

    public boolean sensible() {
        return data != null && FiniteRangeValidation.wireRangeSensible(
            source, assetId, generation, offset, data.length);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaRangeDataPacket packet, IPayloadContext context) {
        if (packet.sensible()) HQFiniteMediaClient.rangeData(packet);
    }
}
