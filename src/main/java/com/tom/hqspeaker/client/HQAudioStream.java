package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.audio.SharedStreamingGroup;
import com.tom.hqspeaker.client.audio.StreamingAudioSource;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HQAudioStream implements AudioStream {
    public interface FiniteListener {
        void onReady(long generation, double durationSeconds);
        void onError(long generation, String message);
    }

    public static final int SAMPLE_RATE = 48_000;
    static final int MAX_DECODED_PCM_BYTES = 64 * 1024 * 1024;
    private static final int MAX_QUEUE_CHUNKS = 64;
    private static final ByteBuffer SILENCE_FRAME = ByteBuffer.allocateDirect(4096);

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HQSpeaker-Decoder");
        thread.setDaemon(true);
        return thread;
    });

    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private final AtomicInteger pendingDecodes = new AtomicInteger();
    private final AtomicLong lifecycle = new AtomicLong();
    private volatile javax.sound.sampled.AudioFormat audioFormat;
    private volatile boolean hasRealData;
    private volatile boolean closed;

    private volatile boolean finiteMode;
    private volatile long finiteGeneration;
    private volatile String finiteLabel = "audio";
    private volatile FiniteAudioTrack finiteTrack;
    private volatile String finiteError = "";
    private volatile boolean finiteEofDelivered;
    private volatile FiniteListener finiteListener;

    private StreamingAudioSource streamingSource;
    private SharedStreamingGroup.Tap sharedStreamingTap;
    private volatile boolean isStreaming;
    private volatile boolean streamReady;

    public HQAudioStream() {}

    private HQAudioStream(FiniteAudioTrack track, long generation, String label) {
        finiteMode = true;
        finiteTrack = track;
        finiteGeneration = generation;
        finiteLabel = label;
        audioFormat = monoFormat(track.sampleRate());
        hasRealData = true;
    }

    public void setFiniteListener(FiniteListener listener) { finiteListener = listener; }
    public boolean isFinite() { return finiteMode; }
    public long getFiniteGeneration() { return finiteGeneration; }
    public String getFiniteLabel() { return finiteLabel; }
    public boolean isFiniteReady() { return finiteTrack != null; }
    public boolean hasFiniteError() { return !finiteError.isEmpty(); }
    public String getFiniteError() { return finiteError; }
    public boolean hasDeliveredFiniteEof() { return finiteEofDelivered; }
    public int getPendingDecodeCount() { return pendingDecodes.get(); }

    public double getFiniteDuration() {
        FiniteAudioTrack track = finiteTrack;
        return track == null ? 0.0 : track.durationSeconds();
    }

    public double seekFinite(double seconds) {
        FiniteAudioTrack track = finiteTrack;
        if (track == null || !Double.isFinite(seconds)) return 0.0;
        finiteEofDelivered = false;
        return track.seek(seconds);
    }

    public void setFiniteLooping(boolean looping) {
        FiniteAudioTrack track = finiteTrack;
        if (track != null) track.setLooping(looping);
    }

    public HQAudioStream forkFiniteRenderer(double positionSeconds, boolean looping) {
        FiniteAudioTrack track = finiteTrack;
        if (track == null) throw new IllegalStateException("finite track is not decoded");
        return new HQAudioStream(track.fork(positionSeconds, looping), finiteGeneration, finiteLabel);
    }

    public boolean hasRealData() {
        return hasRealData
            || (sharedStreamingTap != null && sharedStreamingTap.hasData())
            || (streamingSource != null && streamingSource.hasData());
    }

    public boolean isStreamReady() {
        if (finiteMode) return finiteTrack != null;
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
        if (finiteMode) return hasFiniteError() || finiteEofDelivered;
        if (isStreaming) {
            boolean anyBackend = sharedStreamingTap != null || streamingSource != null;
            if (!anyBackend) return true;
            boolean running = (sharedStreamingTap != null && sharedStreamingTap.isRunning())
                || (streamingSource != null && streamingSource.isRunning());
            boolean queued = (sharedStreamingTap != null && sharedStreamingTap.hasData())
                || (streamingSource != null && streamingSource.hasData());
            return !running && !queued;
        }
        if (pendingDecodes.get() > 0 || !closed) return false;
        synchronized (queue) { return queue.isEmpty(); }
    }

    public void push(HQSpeakerAudioPacket packet) {
        if (closed) return;
        switch (packet.format) {
            case PCM_S16LE -> pushPCM(packet.data, null);
            case OGG_VORBIS -> submitFiniteDecode(packet, "ogg");
            case MP3 -> submitFiniteDecode(packet, "mp3");
            case AUDIO_FILE -> submitFiniteDecode(packet, detectPackedAudioLabel(packet.data));
            case MP3_STREAM, HLS_STREAM, TS_STREAM -> startStreaming(packet);
        }
    }

    public boolean isEmpty() {
        if (finiteMode) return finiteTrack == null || finiteTrack.isAtEnd();
        if (isStreaming) {
            return (sharedStreamingTap == null || !sharedStreamingTap.hasData())
                && (streamingSource == null || !streamingSource.hasData());
        }
        synchronized (queue) { return queue.isEmpty(); }
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
        if (finiteMode) {
            FiniteAudioTrack track = finiteTrack;
            if (track == null) {
                if (closed || hasFiniteError()) return null;
                return silence(maxBytes);
            }
            ByteBuffer output = track.read(maxBytes);
            if (output == null) finiteEofDelivered = true;
            return output;
        }

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
            if (closed && pendingDecodes.get() == 0) return null;
            return silence(maxBytes);
        }
        synchronized (queue) {
            ByteBuffer chunk = queue.peek();
            if (chunk == null) {
                if (closed && pendingDecodes.get() == 0) return null;
                return silence(maxBytes);
            }
            if (chunk.remaining() <= maxBytes) return queue.poll();
            int wanted = maxBytes - Math.floorMod(maxBytes, 2);
            byte[] bytes = new byte[wanted];
            chunk.get(bytes);
            ByteBuffer head = ByteBuffer.allocateDirect(wanted).order(ByteOrder.LITTLE_ENDIAN);
            head.put(bytes).flip();
            return head;
        }
    }

    private static ByteBuffer silence(int maxBytes) {
        int count = Math.min(maxBytes, SILENCE_FRAME.capacity());
        ByteBuffer slice = SILENCE_FRAME.duplicate();
        slice.position(0).limit(Math.max(0, count));
        return slice;
    }

    @Override public void close() { closeAndStop(); }

    public void closeAndStop() {
        closed = true;
        lifecycle.incrementAndGet();
        if (sharedStreamingTap != null) {
            sharedStreamingTap.close();
            sharedStreamingTap = null;
        }
        if (streamingSource != null) {
            streamingSource.stop();
            streamingSource = null;
        }
        synchronized (queue) { queue.clear(); }
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
            default -> StreamingAudioSource.StreamType.MP3_STREAM;
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

    public boolean isStreaming() { return isStreaming; }
    public StreamingAudioSource getStreamingSource() { return streamingSource; }
    public SharedStreamingGroup.Tap getSharedStreamingTap() { return sharedStreamingTap; }

    private void submitFiniteDecode(HQSpeakerAudioPacket packet, String label) {
        byte[] raw = packet.data;
        finiteMode = true;
        finiteGeneration = packet.generation;
        finiteLabel = label;
        if (raw == null || raw.length == 0 || raw.length > HQSpeakerAudioPacket.MAX_BYTES) {
            failFinite("invalid " + label + " payload");
            return;
        }

        long expectedLifecycle = lifecycle.get();
        pendingDecodes.incrementAndGet();
        DECODER.submit(() -> {
            try {
                DecodeResult decoded = decode(raw, label);
                if (closed || lifecycle.get() != expectedLifecycle) return;
                FiniteAudioTrack track = new FiniteAudioTrack(decoded.pcm, decoded.sampleRate,
                    packet.finiteLooping);
                finiteTrack = track;
                audioFormat = monoFormat(decoded.sampleRate);
                hasRealData = true;
                FiniteListener listener = finiteListener;
                if (listener != null) listener.onReady(finiteGeneration, track.durationSeconds());
            } catch (Exception exception) {
                if (!closed && lifecycle.get() == expectedLifecycle) failFinite(safeError(label, exception));
            } finally {
                pendingDecodes.decrementAndGet();
            }
        });
    }

    private void failFinite(String message) {
        finiteError = message == null || message.isBlank() ? "decode failed" : message;
        HQSpeakerMod.warn("HQAudioStream: " + finiteError);
        FiniteListener listener = finiteListener;
        if (listener != null) listener.onError(finiteGeneration, finiteError);
    }

    private static String safeError(String label, Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) detail = exception.getClass().getSimpleName();
        String message = label + " decode failed: " + detail;
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private void pushPCM(byte[] raw, javax.sound.sampled.AudioFormat format) {
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
            if (format != null && audioFormat == null) audioFormat = format;
            else if (audioFormat == null) audioFormat = monoFormat(SAMPLE_RATE);
            queue.add(buffer);
            hasRealData = true;
        }
    }

    static String detectPackedAudioLabel(byte[] raw) {
        if (raw == null || raw.length < 4) return "audio";
        if (raw.length >= 12 && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F'
                && raw[3] == 'F' && raw[8] == 'W' && raw[9] == 'A'
                && raw[10] == 'V' && raw[11] == 'E') return "wav";
        if (raw[0] == 'O' && raw[1] == 'g' && raw[2] == 'g' && raw[3] == 'S') return "ogg";
        if (raw[0] == 'I' && raw[1] == 'D' && raw[2] == '3') return "mp3";
        if ((raw[0] & 0xFF) == 0xFF && ((raw[1] & 0xE0) == 0xE0)) return "mp3";
        if (raw.length >= 12 && raw[0] == 'F' && raw[1] == 'O' && raw[2] == 'R'
                && raw[3] == 'M' && raw[8] == 'A' && raw[9] == 'I' && raw[10] == 'F') return "aiff";
        if (raw[0] == '.' && raw[1] == 's' && raw[2] == 'n' && raw[3] == 'd') return "au";
        if (raw.length >= 12 && raw[4] == 'f' && raw[5] == 't' && raw[6] == 'y'
                && raw[7] == 'p') return "mp4/aac";
        return "audio";
    }

    private record DecodeResult(byte[] pcm, int sampleRate) {}

    private DecodeResult decodeOgg(byte[] raw) throws Exception {
        ByteBuffer nativeBuffer = MemoryUtil.memAlloc(raw.length);
        try {
            nativeBuffer.put(raw).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channelsBuffer = stack.mallocInt(1);
                IntBuffer rateBuffer = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(
                    nativeBuffer, channelsBuffer, rateBuffer);
                if (pcm == null) throw new IOException("STBVorbis rejected the file");
                try {
                    int channels = channelsBuffer.get(0);
                    int rate = rateBuffer.get(0);
                    int samples = pcm.remaining();
                    if (channels <= 0 || channels > 8 || rate <= 0 || samples <= 0
                            || (long) samples * 2L > MAX_DECODED_PCM_BYTES) {
                        throw new IOException("decoded PCM is too large or invalid");
                    }
                    int frames = samples / channels;
                    if ((long) frames * 2L > MAX_DECODED_PCM_BYTES) {
                        throw new IOException("decoded mono PCM is too large");
                    }
                    byte[] mono = new byte[frames * 2];
                    for (int frame = 0; frame < frames; frame++) {
                        long sum = 0;
                        for (int channel = 0; channel < channels; channel++) {
                            sum += pcm.get(frame * channels + channel);
                        }
                        short sample = (short) (sum / channels);
                        mono[frame * 2] = (byte) (sample & 0xFF);
                        mono[frame * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                    }
                    return new DecodeResult(mono, rate);
                } finally {
                    MemoryUtil.memFree(pcm);
                }
            }
        } finally {
            MemoryUtil.memFree(nativeBuffer);
            java.lang.ref.Reference.reachabilityFence(raw);
        }
    }

    private DecodeResult decode(byte[] raw, String label) throws Exception {
        String kind = "audio".equals(label) ? detectPackedAudioLabel(raw) : label;
        if ("ogg".equals(kind)) return decodeOgg(raw);

        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new ByteArrayInputStream(raw))) {
            javax.sound.sampled.AudioFormat encodedFormat = encoded.getFormat();
            int channels = encodedFormat.getChannels();
            int rate = (int) encodedFormat.getSampleRate();
            if (channels <= 0 || channels > 8) throw new IOException("invalid channel count " + channels);
            if (rate <= 0) rate = 44_100;
            javax.sound.sampled.AudioFormat decodedFormat = new javax.sound.sampled.AudioFormat(
                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                rate, 16, channels, channels * 2, rate, false);
            byte[] source;
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFormat, encoded)) {
                source = decoded.readAllBytes();
            }
            if (source.length == 0) throw new IOException("decoded PCM is empty");
            if (source.length > MAX_DECODED_PCM_BYTES) throw new IOException("decoded PCM is too large");

            int frames = source.length / (channels * 2);
            if ((long) frames * 2L > MAX_DECODED_PCM_BYTES) {
                throw new IOException("decoded mono PCM is too large");
            }
            if (channels == 1) {
                int completeBytes = frames * 2;
                return new DecodeResult(completeBytes == source.length
                    ? source : java.util.Arrays.copyOf(source, completeBytes), rate);
            }

            byte[] mono = new byte[frames * 2];
            for (int frame = 0; frame < frames; frame++) {
                long sum = 0;
                for (int channel = 0; channel < channels; channel++) {
                    int index = frame * channels * 2 + channel * 2;
                    sum += (short) ((source[index] & 0xFF) | (source[index + 1] << 8));
                }
                short sample = (short) (sum / channels);
                mono[frame * 2] = (byte) (sample & 0xFF);
                mono[frame * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            return new DecodeResult(mono, rate);
        } catch (UnsupportedAudioFileException exception) {
            throw new IOException("unsupported " + label, exception);
        }
    }

    private static javax.sound.sampled.AudioFormat monoFormat(int sampleRate) {
        return new javax.sound.sampled.AudioFormat(
            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, false);
    }
}
