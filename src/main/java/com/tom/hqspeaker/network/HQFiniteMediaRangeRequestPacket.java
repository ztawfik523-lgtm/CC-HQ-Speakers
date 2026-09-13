package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.media.FiniteRangeValidation;
import com.tom.hqspeaker.peripheral.HQFiniteMediaServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client request for one bounded encoded region of the active prepared asset. */
public record HQFiniteMediaRangeRequestPacket(
    UUID source, UUID assetId, long generation, long offset, int length
) implements CustomPacketPayload {
    public static final Type<HQFiniteMediaRangeRequestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_range_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaRangeRequestPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQFiniteMediaRangeRequestPacket decode(RegistryFriendlyByteBuf buf) {
                return new HQFiniteMediaRangeRequestPacket(
                    buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaRangeRequestPacket packet) {
                buf.writeUUID(packet.source());
                buf.writeUUID(packet.assetId());
                buf.writeVarLong(packet.generation());
                buf.writeVarLong(packet.offset());
                buf.writeVarInt(packet.length());
            }
        };

    public boolean sensible() {
        return FiniteRangeValidation.wireRangeSensible(source, assetId, generation, offset, length);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaRangeRequestPacket packet, IPayloadContext context) {
        if (!packet.sensible()) return;
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                HQFiniteMediaServer.acceptRangeRequest(player, packet);
            }
        });
    }
}
