package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.peripheral.HQSpeakerPeripheral;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public final class HQSpeakerStatusPacket implements CustomPacketPayload {
    public enum Transition { READY, STARTED, PAUSED, RESUMED, SEEKED, ENDED, ERROR }

    public static final int MAX_ERROR_CHARS = 256;
    public static final double MAX_DURATION_SECONDS = 24.0 * 60.0 * 60.0;
    public static final Type<HQSpeakerStatusPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "player_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQSpeakerStatusPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQSpeakerStatusPacket decode(RegistryFriendlyByteBuf buf) {
                return new HQSpeakerStatusPacket(buf.readUUID(), buf.readVarLong(),
                    buf.readEnum(Transition.class), buf.readDouble(), buf.readDouble(),
                    buf.readUtf(MAX_ERROR_CHARS));
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQSpeakerStatusPacket packet) {
                buf.writeUUID(packet.source);
                buf.writeVarLong(packet.generation);
                buf.writeEnum(packet.transition);
                buf.writeDouble(packet.position);
                buf.writeDouble(packet.duration);
                buf.writeUtf(packet.error, MAX_ERROR_CHARS);
            }
        };

    public final UUID source;
    public final long generation;
    public final Transition transition;
    public final double position;
    public final double duration;
    public final String error;

    public HQSpeakerStatusPacket(UUID source, long generation, Transition transition,
                                 double position, double duration, String error) {
        this.source = source;
        this.generation = Math.max(0L, generation);
        this.transition = transition;
        this.position = position;
        this.duration = duration;
        this.error = cap(error);
    }

    private static String cap(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_CHARS ? value : value.substring(0, MAX_ERROR_CHARS);
    }

    public boolean hasSensibleNumbers() {
        return Double.isFinite(position) && Double.isFinite(duration)
            && position >= 0.0 && position <= MAX_DURATION_SECONDS
            && duration >= 0.0 && duration <= MAX_DURATION_SECONDS
            && (duration == 0.0 || position <= duration + 1.0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQSpeakerStatusPacket packet, IPayloadContext context) {
        if (packet.transition == null || !(context.player() instanceof ServerPlayer sender)
                || !packet.hasSensibleNumbers()) return;
        HQSpeakerPeripheral peripheral = HQSpeakerPeripheral.findBySource(packet.source);
        if (peripheral != null) peripheral.acceptPlaybackStatus(sender, packet);
    }
}
