package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public final class HQSpeakerControlPacket implements CustomPacketPayload {
    public enum Action { PAUSE, RESUME, SEEK, SET_VOLUME, SET_LOOP }

    public static final Type<HQSpeakerControlPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "player_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQSpeakerControlPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQSpeakerControlPacket decode(RegistryFriendlyByteBuf buf) {
                return new HQSpeakerControlPacket(buf.readUUID(), buf.readVarLong(),
                    buf.readEnum(Action.class), buf.readDouble());
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQSpeakerControlPacket packet) {
                buf.writeUUID(packet.source);
                buf.writeVarLong(packet.generation);
                buf.writeEnum(packet.action);
                buf.writeDouble(packet.value);
            }
        };

    public final UUID source;
    public final long generation;
    public final Action action;
    public final double value;

    public HQSpeakerControlPacket(UUID source, long generation, Action action, double value) {
        this.source = source;
        this.generation = Math.max(0L, generation);
        this.action = action;
        this.value = Double.isFinite(value) ? value : 0.0;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQSpeakerControlPacket packet, IPayloadContext context) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.control(packet);
    }
}
