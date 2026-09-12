package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HQFiniteMediaBeginPacket(
    UUID source, UUID mediaId, long generation, MediaFormat format,
    float volume, float x, float y, float z,
    int blockX, int blockY, int blockZ,
    long totalBytes, boolean looping, boolean paused
) implements CustomPacketPayload {
    public enum MediaFormat { MP3, OGG, AUDIO_FILE }

    public static final Type<HQFiniteMediaBeginPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_begin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaBeginPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQFiniteMediaBeginPacket decode(RegistryFriendlyByteBuf buf) {
                return new HQFiniteMediaBeginPacket(
                    buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readEnum(MediaFormat.class),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean());
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaBeginPacket p) {
                buf.writeUUID(p.source());
                buf.writeUUID(p.mediaId());
                buf.writeVarLong(Math.max(1L, p.generation()));
                buf.writeEnum(p.format());
                buf.writeFloat(p.volume());
                buf.writeFloat(p.x()); buf.writeFloat(p.y()); buf.writeFloat(p.z());
                buf.writeInt(p.blockX()); buf.writeInt(p.blockY()); buf.writeInt(p.blockZ());
                buf.writeVarLong(p.totalBytes());
                buf.writeBoolean(p.looping());
                buf.writeBoolean(p.paused());
            }
        };

    public boolean sensible() {
        // File-size policy belongs to the server config/store. This prototype transport only validates wire sanity.
        return source != null && mediaId != null && generation > 0L && format != null
            && Float.isFinite(volume) && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
            && totalBytes > 0L;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaBeginPacket packet, IPayloadContext context) {
        if (!packet.sensible()) {
            HQSpeakerMod.warn("M1E finite BEGIN rejected as nonsensical");
            return;
        }
        HQSpeakerMod.log("M1E finite wire BEGIN received source=" + packet.source()
            + " generation=" + packet.generation() + " format=" + packet.format()
            + " bytes=" + packet.totalBytes() + " volume=" + packet.volume()
            + " worldPos=" + packet.x() + "," + packet.y() + "," + packet.z()
            + " blockPos=" + packet.blockX() + "," + packet.blockY() + "," + packet.blockZ());
        HQFiniteMediaClient.begin(packet);
    }
}
