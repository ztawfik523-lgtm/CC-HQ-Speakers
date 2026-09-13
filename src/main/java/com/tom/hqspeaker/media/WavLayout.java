package com.tom.hqspeaker.media;

/**
 * Normalized direct-conversion layout for the common WAV subset supported by modern finite playback.
 *
 * <p>This is physical container metadata, not renderer policy. M1G converts these samples progressively to
 * mono signed 16-bit PCM while preserving {@link #sampleRate()}.</p>
 */
public record WavLayout(
    Representation representation,
    int sampleRate,
    int channels,
    int blockAlign,
    long dataOffset,
    long dataLength
) {
    public enum Representation {
        U8(8),
        S16(16),
        S24(24),
        S32(32),
        F32(32);

        private final int bitsPerSample;

        Representation(int bitsPerSample) {
            this.bitsPerSample = bitsPerSample;
        }

        public int bitsPerSample() {
            return bitsPerSample;
        }

        public int bytesPerSample() {
            return bitsPerSample / 8;
        }
    }

    public WavLayout {
        if (representation == null) throw new NullPointerException("representation");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("channels must be mono or stereo");
        int expectedAlign = Math.multiplyExact(representation.bytesPerSample(), channels);
        if (blockAlign != expectedAlign) {
            throw new IllegalArgumentException("blockAlign does not match representation/channels");
        }
        if (dataOffset < 0L) throw new IllegalArgumentException("dataOffset must be non-negative");
        if (dataLength <= 0L) throw new IllegalArgumentException("dataLength must be positive");
        if (dataLength % blockAlign != 0L) {
            throw new IllegalArgumentException("dataLength must contain complete PCM frames");
        }
    }

    public long frameCount() {
        return dataLength / blockAlign;
    }

    public double durationSeconds() {
        return frameCount() / (double) sampleRate;
    }

    public long byteOffsetForFrame(long frame) {
        if (frame < 0L || frame > frameCount()) throw new IllegalArgumentException("frame outside WAV data");
        return Math.addExact(dataOffset, Math.multiplyExact(frame, (long) blockAlign));
    }
}
