package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HQFiniteMediaControlPacket(UUID source, long generation, Action action, double value) implements CustomPacketPayload {
    public enum Action { PAUSE, RESUME, SEEK, SET_VOLUME, SET_LOOP, STOP }

    public static final Type<HQFiniteMediaControlPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_control_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaControlPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public HQFiniteMediaControlPacket decode(RegistryFriendlyByteBuf buf) {
            return new HQFiniteMediaControlPacket(buf.readUUID(), buf.readVarLong(), buf.readEnum(Action.class), buf.readDouble());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaControlPacket p) {
            buf.writeUUID(p.source()); buf.writeVarLong(p.generation()); buf.writeEnum(p.action()); buf.writeDouble(p.value());
        }
    };

    public boolean sensible() { return source != null && generation > 0L && action != null && Double.isFinite(value); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(HQFiniteMediaControlPacket p, IPayloadContext context) { if (p.sensible()) HQFiniteMediaClient.control(p); }
}
