package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.audio.SharedStreamingGroup;
import com.tom.hqspeaker.client.audio.StreamingAudioSource;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import net.minecraft.client.sounds.AudioStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Legacy non-finite audio stream used only for producer-fed RAW PCM and optional live streaming.
 *
 * <p>Finite MP3/WAV playback is owned by the modern bounded finite engine and never enters this class.</p>
 */
public class HQAudioStream implements AudioStream {
    public static final int SAMPLE_RATE = 48_000;
    private static final int MAX_QUEUE_CHUNKS = 64;
    private static final ByteBuffer SILENCE_FRAME = ByteBuffer.allocateDirect(4096);

    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private volatile javax.sound.sampled.AudioFormat audioFormat;
    private volatile boolean hasRealData;
    private volatile boolean closed;

    private StreamingAudioSource streamingSource;
    private SharedStreamingGroup.Tap sharedStreamingTap;
    private volatile boolean isStreaming;
    private volatile boolean streamReady;

    public boolean hasRealData() {
        return hasRealData
            || (sharedStreamingTap != null && sharedStreamingTap.hasData())
            || (streamingSource != null && streamingSource.hasData());
    }

    public boolean isStreamReady() {
        if (!isStreaming) return hasRealData;
        if (streamReady) return true;

        int queued = sharedStreamingTap != null ? sharedStreamingTap.getQueueSize()
            : (streamingSource != null ? streamingSource.getQueueSize() : 0);
        javax.sound.sampled.AudioFormat format = sharedStreamingTap != null
            ? sharedStreamingTap.getFormat()
            : (streamingSource != null ? streamingSource.getFormat() : null);
        if (format != null && queued >= StreamingAudioSource.PREBUFFER_CHUNKS) {
            streamReady = true;
            HQSpeakerMod.log("HQAudioStream: prebuffer ready, format=" + format);
        }
        return streamReady;
    }

    public boolean isDrained() {
        if (isStreaming) {
            boolean anyBackend = sharedStreamingTap != null || streamingSource != null;
            if (!anyBackend) return true;
            boolean running = (sharedStreamingTap != null && sharedStreamingTap.isRunning())
                || (streamingSource != null && streamingSource.isRunning());
            boolean queued = (sharedStreamingTap != null && sharedStreamingTap.hasData())
                || (streamingSource != null && streamingSource.hasData());
            return !running && !queued;
        }
        if (!closed) return false;
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    public void push(HQSpeakerAudioPacket packet) {
        if (closed || packet == null || packet.format == null) return;
        switch (packet.format) {
            case PCM_S16LE -> pushPCM(packet.data);
            case MP3_STREAM, HLS_STREAM, TS_STREAM -> startStreaming(packet);
            case OGG_VORBIS, MP3, AUDIO_FILE ->
                HQSpeakerMod.warn("HQAudioStream: rejected retired whole-file format " + packet.format);
        }
    }

    public boolean isEmpty() {
        if (isStreaming) {
            return (sharedStreamingTap == null || !sharedStreamingTap.hasData())
                && (streamingSource == null || !streamingSource.hasData());
        }
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    @Override
    public javax.sound.sampled.AudioFormat getFormat() {
        if (isStreaming) {
            javax.sound.sampled.AudioFormat format = sharedStreamingTap != null
                ? sharedStreamingTap.getFormat()
                : (streamingSource != null ? streamingSource.getFormat() : null);
            if (format != null) return format;
        }
        javax.sound.sampled.AudioFormat format = audioFormat;
        return format != null ? format : monoFormat(SAMPLE_RATE);
    }

    @Override
    public ByteBuffer read(int maxBytes) throws IOException {
        if (isStreaming && (sharedStreamingTap != null || streamingSource != null)) {
            ByteBuffer data = sharedStreamingTap != null
                ? sharedStreamingTap.readPCM(maxBytes) : streamingSource.readPCM(maxBytes);
            if (data == null) return null;
            if (data.remaining() > 0) {
                hasRealData = true;
                return data;
            }
            return silence(maxBytes);
        }

        if (!hasRealData) {
            if (closed) return null;
            return silence(maxBytes);
        }

        synchronized (queue) {
            ByteBuffer chunk = queue.peek();
            if (chunk == null) {
                if (closed) return null;
                return silence(maxBytes);
            }
            if (chunk.remaining() <= maxBytes) return queue.poll();

            int wanted = maxBytes - Math.floorMod(maxBytes, 2);
            if (wanted <= 0) return silence(maxBytes);
            byte[] bytes = new byte[wanted];
            chunk.get(bytes);
            ByteBuffer head = ByteBuffer.allocateDirect(wanted).order(ByteOrder.LITTLE_ENDIAN);
            head.put(bytes).flip();
            return head;
        }
    }

    private static ByteBuffer silence(int maxBytes) {
        int count = Math.min(Math.max(0, maxBytes), SILENCE_FRAME.capacity());
        ByteBuffer slice = SILENCE_FRAME.duplicate();
        slice.position(0).limit(count);
        return slice;
    }

    @Override
    public void close() {
        closeAndStop();
    }

    public void closeAndStop() {
        closed = true;
        if (sharedStreamingTap != null) {
            sharedStreamingTap.close();
            sharedStreamingTap = null;
        }
        if (streamingSource != null) {
            streamingSource.stop();
            streamingSource = null;
        }
        synchronized (queue) {
            queue.clear();
        }
    }

    private void startStreaming(HQSpeakerAudioPacket packet) {
        if (packet.streamUrl == null || packet.streamUrl.isEmpty()) {
            HQSpeakerMod.warn("HQAudioStream: no URL for streaming packet");
            return;
        }

        isStreaming = true;
        streamReady = false;
        hasRealData = true;

        StreamingAudioSource.StreamType type = switch (packet.format) {
            case MP3_STREAM -> StreamingAudioSource.StreamType.MP3_STREAM;
            case HLS_STREAM -> StreamingAudioSource.StreamType.HLS_STREAM;
            case TS_STREAM -> StreamingAudioSource.StreamType.TS_STREAM;
            default -> throw new IllegalArgumentException("not a streaming format: " + packet.format);
        };

        java.util.UUID source = packet.source;
        if (packet.syncGroupId != null && packet.syncGroupSize > 1) {
            sharedStreamingTap = SharedStreamingGroup.open(packet.syncGroupId, packet.streamUrl,
                type, packet.volume, source, Math.max(1, packet.syncGroupSize));
            streamingSource = null;
            HQSpeakerMod.log("HQAudioStream: joined shared " + packet.format + " group "
                + packet.syncGroupId + " from " + packet.streamUrl);
        } else {
            streamingSource = new StreamingAudioSource(packet.streamUrl, type, packet.volume);
            streamingSource.setMetadataListener((rawTitle, station, genre, description) -> {
                try {
                    com.tom.hqspeaker.network.HQSpeakerNetwork.sendToServer(
                        new com.tom.hqspeaker.network.IcyMetaPacket(
                            source, rawTitle, station, genre, description));
                } catch (Exception exception) {
                    HQSpeakerMod.warn("HQAudioStream: failed to send ICY meta - " + exception.getMessage());
                }
            });
            streamingSource.start();
            HQSpeakerMod.log("HQAudioStream: started " + packet.format + " from " + packet.streamUrl);
        }
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public StreamingAudioSource getStreamingSource() {
        return streamingSource;
    }

    public SharedStreamingGroup.Tap getSharedStreamingTap() {
        return sharedStreamingTap;
    }

    private void pushPCM(byte[] raw) {
        if (raw == null || raw.length == 0) return;
        int length = raw.length - Math.floorMod(raw.length, 2);
        if (length <= 0) return;

        ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(raw, 0, length).flip();
        synchronized (queue) {
            if (queue.size() >= MAX_QUEUE_CHUNKS) {
                HQSpeakerMod.warn("HQAudioStream: dropping PCM chunk; queue full");
                return;
            }
            if (audioFormat == null) audioFormat = monoFormat(SAMPLE_RATE);
            queue.add(buffer);
            hasRealData = true;
        }
    }

    private static javax.sound.sampled.AudioFormat monoFormat(int sampleRate) {
        return new javax.sound.sampled.AudioFormat(
            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, false);
    }
}
