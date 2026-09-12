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
    UUID source, UUID mediaId, long generation, PlaybackState state,
    double position, double duration, float volume, boolean looping, String error
) implements CustomPacketPayload {
    public enum PlaybackState { PLAYING, PAUSED, ENDED, ERROR }
    private static final int MAX_ERROR = 256;

    public static final Type<HQFiniteMediaStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public HQFiniteMediaStatePacket decode(RegistryFriendlyByteBuf buf) {
            return new HQFiniteMediaStatePacket(
                buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readEnum(PlaybackState.class),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean(), buf.readUtf(MAX_ERROR));
        }

        @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaStatePacket p) {
            buf.writeUUID(p.source());
            buf.writeUUID(p.mediaId());
            buf.writeVarLong(Math.max(1L, p.generation()));
            buf.writeEnum(p.state());
            buf.writeDouble(p.position());
            buf.writeDouble(p.duration());
            buf.writeFloat(p.volume());
            buf.writeBoolean(p.looping());
            String e = p.error() == null ? "" : p.error();
            buf.writeUtf(e.length() <= MAX_ERROR ? e : e.substring(0, MAX_ERROR), MAX_ERROR);
        }
    };

    public boolean sensible() {
        return source != null && mediaId != null && generation > 0L && state != null
            && Double.isFinite(position) && position >= 0.0
            && Double.isFinite(duration) && duration > 0.0
            && position <= duration + 1.0e-6
            && Float.isFinite(volume);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaStatePacket packet, IPayloadContext context) {
        if (!packet.sensible()) {
            HQSpeakerMod.warn("M1E finite STATE rejected as nonsensical source=" + packet.source()
                + " generation=" + packet.generation() + " state=" + packet.state()
                + " position=" + packet.position() + " duration=" + packet.duration());
            return;
        }
        HQSpeakerMod.log("M1E finite wire STATE received source=" + packet.source()
            + " generation=" + packet.generation() + " state=" + packet.state()
            + " position=" + packet.position() + " duration=" + packet.duration()
            + " volume=" + packet.volume() + " looping=" + packet.looping());
        HQFiniteMediaClient.state(packet);
    }
}
