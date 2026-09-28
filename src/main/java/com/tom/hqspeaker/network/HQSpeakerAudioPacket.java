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
 * Non-finite HQ audio packet.
 *
 * <p>Protocol v11 carries producer-fed RAW PCM and MP3/ICY radio starts. Logical volume is retained for
 * diagnostics/API status while gain and range are already resolved by the authoritative server profile.</p>
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
    public static final int MAX_URL_CHARS = StreamUrlPolicy.MAX_URL_CHARS;
    public static final float MAX_WIRE_RANGE = 4096.0f;

    public final UUID source;
    public final AudioFormat format;
    /** Logical Lua volume, not OpenAL source gain. */
    public final float volume;
    /** Server-resolved source gain. */
    public final float gain;
    /** Server-resolved fade-to-zero distance in blocks. */
    public final float range;
    public final boolean explicitRange;
    public final float x, y, z;
    public final int blockX, blockY, blockZ;
    public final byte[] data;
    public final String streamUrl;
    public final long startTick;
    public final UUID syncGroupId;

    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                float volume, float gain, float range, boolean explicitRange,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                byte[] data, long startTick) {
        this(source, format, volume, gain, range, explicitRange,
            x, y, z, blockX, blockY, blockZ, data, null, startTick, null);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                float volume, float gain, float range, boolean explicitRange,
                                float x, float y, float z,
                                int blockX, int blockY, int blockZ,
                                String streamUrl, long startTick, UUID syncGroupId) {
        this(source, format, volume, gain, range, explicitRange,
            x, y, z, blockX, blockY, blockZ, new byte[0], streamUrl, startTick, syncGroupId);
    }

    private HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume, float gain, float range, boolean explicitRange,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data, String streamUrl, long startTick,
                                 UUID syncGroupId) {
        this.source = source;
        this.format = format != null ? format : AudioFormat.PCM_S16LE;
        this.volume = volume;
        this.gain = gain;
        this.range = range;
        this.explicitRange = explicitRange;
        this.x = x;
        this.y = y;
        this.z = z;
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

    public boolean sensible() {
        if (source == null || format == null
                || !Float.isFinite(volume) || volume < 0.0f || volume > 3.0f
                || !Float.isFinite(gain) || gain < 0.0f || gain > 1.0f
                || !Float.isFinite(range) || range < 0.0f || range > MAX_WIRE_RANGE
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return false;
        }
        if (gain > 0.0f && range <= 0.0f) return false;
        if (isStreamingFormat()) {
            return streamUrl != null && !streamUrl.isBlank() && streamUrl.length() <= MAX_URL_CHARS;
        }
        return data != null && data.length > 0 && data.length <= MAX_BYTES;
    }

    public static void encode(HQSpeakerAudioPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.source);
        buf.writeEnum(packet.format);
        buf.writeFloat(packet.volume);
        buf.writeFloat(packet.gain);
        buf.writeFloat(packet.range);
        buf.writeBoolean(packet.explicitRange);
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
        float gain = buf.readFloat();
        float range = buf.readFloat();
        boolean explicitRange = buf.readBoolean();
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
            return new HQSpeakerAudioPacket(source, AudioFormat.PCM_S16LE,
                volume, gain, range, explicitRange, x, y, z,
                blockX, blockY, blockZ, new byte[0], null, startTick, null);
        }

        if (streaming) {
            String streamUrl = buf.readUtf(MAX_URL_CHARS);
            return new HQSpeakerAudioPacket(source, format,
                volume, gain, range, explicitRange, x, y, z,
                blockX, blockY, blockZ, new byte[0], streamUrl, startTick, syncGroupId);
        }

        int length = buf.readVarInt();
        if (length < 0 || length > MAX_BYTES) {
            HQSpeakerMod.warn("HQSpeakerAudioPacket: rejected oversized payload (" + length + " bytes)");
            return new HQSpeakerAudioPacket(source, AudioFormat.PCM_S16LE,
                volume, gain, range, explicitRange, x, y, z,
                blockX, blockY, blockZ, new byte[0], null, startTick, null);
        }

        byte[] data = new byte[length];
        if (length > 0) buf.readBytes(data);
        return new HQSpeakerAudioPacket(source, format,
            volume, gain, range, explicitRange, x, y, z,
            blockX, blockY, blockZ, data, null, startTick, syncGroupId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HQSpeakerAudioPacket packet, IPayloadContext context) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.receive(packet);
    }
}
