package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.peripheral.HQFiniteMediaServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HQFiniteMediaStatusPacket(
    UUID source, long generation, Transition transition,
    double position, double duration, String error
) implements CustomPacketPayload {
    public enum Transition { READY, STARTED, PAUSED, RESUMED, SEEKED, ENDED, ERROR }
    private static final int MAX_ERROR = 256;

    public static final Type<HQFiniteMediaStatusPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_status_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaStatusPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public HQFiniteMediaStatusPacket decode(RegistryFriendlyByteBuf buf) {
            return new HQFiniteMediaStatusPacket(buf.readUUID(), buf.readVarLong(), buf.readEnum(Transition.class),
                buf.readDouble(), buf.readDouble(), buf.readUtf(MAX_ERROR));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaStatusPacket p) {
            buf.writeUUID(p.source()); buf.writeVarLong(p.generation()); buf.writeEnum(p.transition());
            buf.writeDouble(p.position()); buf.writeDouble(p.duration());
            String e = p.error() == null ? "" : p.error();
            buf.writeUtf(e.length() <= MAX_ERROR ? e : e.substring(0, MAX_ERROR), MAX_ERROR);
        }
    };

    public boolean sensible() {
        return source != null && generation > 0L && transition != null
            && Double.isFinite(position) && position >= 0.0
            && Double.isFinite(duration) && duration >= 0.0;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaStatusPacket packet, IPayloadContext context) {
        if (!packet.sensible()) return;
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                HQFiniteMediaServer.acceptStatus(player, packet);
            }
        });
    }
}
