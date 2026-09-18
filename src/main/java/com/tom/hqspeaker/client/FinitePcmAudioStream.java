package com.tom.hqspeaker.client;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Nonblocking Minecraft AudioStream view over one bounded M1G mono-S16 PCM queue. */
public final class FinitePcmAudioStream implements AudioStream {
    private static final int MAX_READ_BYTES = 8 * 1024;
    private static final int MAX_SILENCE_BYTES = 4 * 1024;

    private final FinitePcmQueue queue;
    private final AudioFormat format;
    private final int silenceBytes;
    private volatile boolean closed;
    private volatile boolean reachedEof;

    public FinitePcmAudioStream(FinitePcmQueue queue, int sampleRate) {
        if (queue == null) throw new NullPointerException("queue");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        this.queue = queue;
        this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, false);
        // About 20 ms of mono S16 silence, hard capped so a huge source rate cannot inflate sound-thread allocation.
        long target = Math.max(2L, Math.min((long) MAX_SILENCE_BYTES,
            Math.max(2L, ((long) sampleRate * 2L) / 50L)));
        int bytes = (int) target;
        this.silenceBytes = (bytes & 1) == 0 ? bytes : bytes - 1;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int maxBytes) throws IOException {
        if (closed) return null;
        if (maxBytes <= 0) return ByteBuffer.allocateDirect(0);
        if (maxBytes < 2) return ByteBuffer.allocateDirect(0);

        int wanted = Math.min(maxBytes - (maxBytes & 1), MAX_READ_BYTES);
        if (wanted < 2) wanted = 2;
        FinitePcmReadAdapter.Result result = FinitePcmReadAdapter.read(queue, wanted, silenceBytes);
        return switch (result.state()) {
            case DATA, SILENCE -> direct(result.data());
            case EOF -> {
                reachedEof = true;
                yield null;
            }
            case CANCELLED -> null;
        };
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // SoundEngine may close a stream independently of the server session (for example on reload). Drop this local
        // epoch's producer-side queue too so a decoder cannot remain permanently blocked on PCM nobody will consume.
        queue.cancel();
    }

    public boolean closed() {
        return closed;
    }

    public boolean reachedEof() {
        return reachedEof;
    }

    static int maxReadBytes() {
        return MAX_READ_BYTES;
    }

    private static ByteBuffer direct(byte[] bytes) {
        ByteBuffer out = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(bytes).flip();
        return out;
    }

}
