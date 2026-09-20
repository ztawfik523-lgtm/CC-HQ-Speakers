package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Legacy non-finite audio packet.
 *
 * <p>Protocol v9 carries only producer-fed RAW PCM and optional live-stream starts. Finite MP3/WAV uses the
 * HQFiniteMedia* protocol and does not share this packet.</p>
 */
public class HQSpeakerAudioPacket implements CustomPacketPayload {
    public static final Type<HQSpeakerAudioPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "audio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQSpeakerAudioPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public HQSpeakerAudioPacket decode(RegistryFriendlyByteBuf buf) {
                return HQSpeakerAudioPacket.decode(buf);
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, HQSpeakerAudioPacket packet) {
                HQSpeakerAudioPacket.encode(packet, buf);
            }
        };

    public enum AudioFormat {
        PCM_S16LE,
        MP3_STREAM
    }

    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_URL_CHARS = 512;

    public final UUID source;
    public final AudioFormat format;
    public final float volume;
    public final float x, y, z;
    public final int blockX, blockY, blockZ;
    public final byte[] data;
    public final String streamUrl;
    public final long startTick;
    public final UUID syncGroupId;

    public HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                byte[] data) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ,
            data, null, 0L, null);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                byte[] data, long startTick) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ,
            data, null, startTick, null);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                String streamUrl) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ,
            new byte[0], streamUrl, 0L, null);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                String streamUrl, long startTick) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ,
            new byte[0], streamUrl, startTick, null);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                String streamUrl, long startTick,
                                UUID syncGroupId) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ,
            new byte[0], streamUrl, startTick, syncGroupId);
    }

    private HQSpeakerAudioPacket(UUID source, AudioFormat format, float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data, String streamUrl, long startTick,
                                 UUID syncGroupId) {
        this.source = source;
        this.format = format != null ? format : AudioFormat.PCM_S16LE;
        this.volume = Float.isFinite(volume) ? Math.max(0.0f, Math.min(3.0f, volume)) : 1.0f;
        this.x = Float.isFinite(x) ? x : 0.0f;
        this.y = Float.isFinite(y) ? y : 0.0f;
        this.z = Float.isFinite(z) ? z : 0.0f;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;

        if (data == null) data = new byte[0];
        this.data = data.length <= MAX_BYTES ? data : new byte[0];
        this.streamUrl = streamUrl != null && streamUrl.length() <= MAX_URL_CHARS ? streamUrl : "";
        this.startTick = Math.max(0L, startTick);
        this.syncGroupId = syncGroupId;
    }

    public boolean isStreamingFormat() {
        return format == AudioFormat.MP3_STREAM;
    }

    public static void encode(HQSpeakerAudioPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.source);
        buf.writeEnum(packet.format);
        buf.writeFloat(packet.volume);
        buf.writeFloat(packet.x);
        buf.writeFloat(packet.y);
        buf.writeFloat(packet.z);
        buf.writeInt(packet.blockX);
        buf.writeInt(packet.blockY);
        buf.writeInt(packet.blockZ);
        buf.writeVarLong(packet.startTick);
        buf.writeBoolean(packet.syncGroupId != null);
        if (packet.syncGroupId != null) buf.writeUUID(packet.syncGroupId);

        boolean streaming = packet.isStreamingFormat();
        buf.writeBoolean(streaming);
        if (streaming) {
            String safeUrl = packet.streamUrl != null && packet.streamUrl.length() <= MAX_URL_CHARS
                ? packet.streamUrl : "";
            buf.writeUtf(safeUrl, MAX_URL_CHARS);
        } else {
            int length = packet.data == null ? 0 : Math.min(packet.data.length, MAX_BYTES);
            buf.writeVarInt(length);
            if (length > 0) buf.writeBytes(packet.data, 0, length);
        }
    }

    public static HQSpeakerAudioPacket decode(FriendlyByteBuf buf) {
        UUID source = buf.readUUID();
        AudioFormat format = buf.readEnum(AudioFormat.class);
        float volume = buf.readFloat();
        float x = buf.readFloat();
        float y = buf.readFloat();
        float z = buf.readFloat();
        int blockX = buf.readInt();
        int blockY = buf.readInt();
        int blockZ = buf.readInt();
        long startTick = buf.readVarLong();
        UUID syncGroupId = buf.readBoolean() ? buf.readUUID() : null;
        boolean streaming = buf.readBoolean();

        boolean expectedStreaming = format == AudioFormat.MP3_STREAM;
        if (streaming != expectedStreaming) {
            HQSpeakerMod.warn("HQSpeakerAudioPacket: rejected mismatched streaming flag for " + format);
            return new HQSpeakerAudioPacket(source, AudioFormat.PCM_S16LE, volume, x, y, z,
                blockX, blockY, blockZ, new byte[0], null, startTick, null);
        }

        if (streaming) {
            String streamUrl = buf.readUtf(MAX_URL_CHARS);
            return new HQSpeakerAudioPacket(source, format, volume, x, y, z,
                blockX, blockY, blockZ, streamUrl, startTick, syncGroupId);
        }

        int length = buf.readVarInt();
        if (length < 0 || length > MAX_BYTES) {
            HQSpeakerMod.warn("HQSpeakerAudioPacket: rejected oversized payload (" + length + " bytes)");
            return new HQSpeakerAudioPacket(source, AudioFormat.PCM_S16LE, volume, x, y, z,
                blockX, blockY, blockZ, new byte[0], null, startTick, null);
        }

        byte[] data = new byte[length];
        if (length > 0) buf.readBytes(data);
        return new HQSpeakerAudioPacket(source, format, volume, x, y, z,
            blockX, blockY, blockZ, data, null, startTick, syncGroupId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQSpeakerAudioPacket packet, IPayloadContext context) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.receive(packet);
    }
}
