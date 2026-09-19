package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.HQFiniteMediaClient;
import com.tom.hqspeaker.media.FiniteDecodeDescriptor;
import com.tom.hqspeaker.media.WavLayout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Modern finite source descriptor. M1G intentionally exposes only MP3 or normalized common WAV. */
public record HQFiniteMediaBeginPacket(
    UUID source, UUID mediaId, UUID playbackId, long generation, FiniteDecodeDescriptor descriptor,
    float volume, float x, float y, float z,
    int blockX, int blockY, int blockZ,
    long totalBytes, boolean looping, boolean paused
) implements CustomPacketPayload {
    public static final Type<HQFiniteMediaBeginPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "finite_begin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQFiniteMediaBeginPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQFiniteMediaBeginPacket decode(RegistryFriendlyByteBuf buf) {
                UUID source = buf.readUUID();
                UUID mediaId = buf.readUUID();
                UUID playbackId = buf.readUUID();
                long generation = buf.readVarLong();
                FiniteDecodeDescriptor descriptor = readDescriptor(buf);
                return new HQFiniteMediaBeginPacket(
                    source, mediaId, playbackId, generation, descriptor,
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean());
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQFiniteMediaBeginPacket p) {
                buf.writeUUID(p.source());
                buf.writeUUID(p.mediaId());
                buf.writeUUID(p.playbackId());
                buf.writeVarLong(Math.max(1L, p.generation()));
                writeDescriptor(buf, p.descriptor());
                buf.writeFloat(p.volume());
                buf.writeFloat(p.x()); buf.writeFloat(p.y()); buf.writeFloat(p.z());
                buf.writeInt(p.blockX()); buf.writeInt(p.blockY()); buf.writeInt(p.blockZ());
                buf.writeVarLong(p.totalBytes());
                buf.writeBoolean(p.looping());
                buf.writeBoolean(p.paused());
            }
        };

    public boolean sensible() {
        if (source == null || mediaId == null || playbackId == null || generation <= 0L || descriptor == null
                || !Float.isFinite(volume) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || totalBytes <= 0L) {
            return false;
        }
        WavLayout wav = descriptor.wavLayout();
        return wav == null || (wav.dataOffset() <= totalBytes && wav.dataLength() <= totalBytes - wav.dataOffset());
    }

    private static FiniteDecodeDescriptor readDescriptor(RegistryFriendlyByteBuf buf) {
        FiniteDecodeDescriptor.Kind kind = buf.readEnum(FiniteDecodeDescriptor.Kind.class);
        int sampleRate = buf.readVarInt();
        int channels = buf.readVarInt();
        WavLayout wav = null;
        if (kind == FiniteDecodeDescriptor.Kind.WAV) {
            WavLayout.Representation representation = buf.readEnum(WavLayout.Representation.class);
            int blockAlign = buf.readVarInt();
            long dataOffset = buf.readVarLong();
            long dataLength = buf.readVarLong();
            wav = new WavLayout(representation, sampleRate, channels, blockAlign, dataOffset, dataLength);
        }
        return new FiniteDecodeDescriptor(kind, sampleRate, channels, wav);
    }

    private static void writeDescriptor(RegistryFriendlyByteBuf buf, FiniteDecodeDescriptor descriptor) {
        buf.writeEnum(descriptor.kind());
        buf.writeVarInt(descriptor.sampleRate());
        buf.writeVarInt(descriptor.channels());
        if (descriptor.kind() == FiniteDecodeDescriptor.Kind.WAV) {
            WavLayout wav = descriptor.wavLayout();
            buf.writeEnum(wav.representation());
            buf.writeVarInt(wav.blockAlign());
            buf.writeVarLong(wav.dataOffset());
            buf.writeVarLong(wav.dataLength());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQFiniteMediaBeginPacket packet, IPayloadContext context) {
        if (!packet.sensible()) {
            HQSpeakerMod.warn("M1G finite BEGIN rejected as nonsensical");
            return;
        }
        HQSpeakerMod.log("M1J finite wire BEGIN received source=" + packet.source()
            + " playback=" + packet.playbackId() + " generation=" + packet.generation() + " format=" + packet.descriptor().kind()
            + " rate=" + packet.descriptor().sampleRate() + " channels=" + packet.descriptor().channels()
            + " bytes=" + packet.totalBytes() + " volume=" + packet.volume()
            + " worldPos=" + packet.x() + "," + packet.y() + "," + packet.z()
            + " blockPos=" + packet.blockX() + "," + packet.blockY() + "," + packet.blockZ());
        HQFiniteMediaClient.begin(packet);
    }
}
