package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Authoritative finite playback snapshot sent by the server. */
public record HQFiniteMediaStatePacket(
    UUID source, UUID mediaId, long generation, long decodeRevision, PlaybackState state,
    double position, double duration, float volume, boolean looping,
    long anchorOffset, double anchorTime, String error
) implements CustomPacketPayload {
    public enum PlaybackState { PLAYING, PAUSED, ENDED, ERROR }
    private static final int MAX_ERROR = 256;

    public static final Type<HQFiniteMediaStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public HQFiniteMediaStatePacket decode(RegistryFriendlyByteBuf buf) {
            return new HQFiniteMediaStatePacket(
                buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readVarLong(), buf.readEnum(PlaybackState.class),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean(),
                buf.readVarLong(), buf.readDouble(), buf.readUtf(MAX_ERROR));
        }

        @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaStatePacket p) {
            buf.writeUUID(p.source());
            buf.writeUUID(p.mediaId());
            buf.writeVarLong(Math.max(1L, p.generation()));
            buf.writeVarLong(Math.max(1L, p.decodeRevision()));
            buf.writeEnum(p.state());
            buf.writeDouble(p.position());
            buf.writeDouble(p.duration());
            buf.writeFloat(p.volume());
            buf.writeBoolean(p.looping());
            buf.writeVarLong(Math.max(0L, p.anchorOffset()));
            buf.writeDouble(p.anchorTime());
            String e = p.error() == null ? "" : p.error();
            buf.writeUtf(e.length() <= MAX_ERROR ? e : e.substring(0, MAX_ERROR), MAX_ERROR);
        }
    };

    public boolean sensible() {
        return source != null && mediaId != null && generation > 0L && decodeRevision > 0L && state != null
            && Double.isFinite(position) && position >= 0.0
            && Double.isFinite(duration) && duration > 0.0
            && position <= duration + 1.0e-6
            && Float.isFinite(volume)
            && anchorOffset >= 0L
            && Double.isFinite(anchorTime) && anchorTime >= 0.0 && anchorTime <= position + 1.0e-6;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaStatePacket packet, IPayloadContext context) {
        if (!packet.sensible()) {
            HQSpeakerMod.warn("M1F finite STATE rejected as nonsensical source=" + packet.source()
                + " generation=" + packet.generation() + " revision=" + packet.decodeRevision()
                + " state=" + packet.state()
                + " position=" + packet.position() + " duration=" + packet.duration());
            return;
        }
        HQFiniteMediaClient.state(packet);
    }
}
