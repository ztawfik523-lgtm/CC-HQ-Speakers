package com.tom.hqspeaker.media;

/**
 * Decoder-facing format contract for the modern prepared finite path.
 *
 * <p>This intentionally contains only the two M1G core formats. Historical analyzer support for other containers must
 * not silently widen the modern decoder surface.</p>
 */
public record FiniteDecodeDescriptor(
    Kind kind,
    int sampleRate,
    int channels,
    WavLayout wavLayout
) {
    public enum Kind { MP3, WAV }

    public FiniteDecodeDescriptor {
        if (kind == null) throw new NullPointerException("kind");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("modern finite audio must be mono/stereo");
        if (kind == Kind.MP3 && wavLayout != null) {
            throw new IllegalArgumentException("MP3 descriptor cannot carry WAV layout");
        }
        if (kind == Kind.WAV) {
            if (wavLayout == null) throw new IllegalArgumentException("WAV descriptor requires normalized layout");
            if (wavLayout.sampleRate() != sampleRate || wavLayout.channels() != channels) {
                throw new IllegalArgumentException("WAV descriptor rate/channels mismatch");
            }
        }
    }

    public static FiniteDecodeDescriptor fromMetadata(MediaMetadata metadata) {
        if (metadata == null) throw new NullPointerException("metadata");
        return switch (metadata.format()) {
            case MP3 -> new FiniteDecodeDescriptor(Kind.MP3, metadata.sampleRate(), metadata.channels(), null);
            case WAV -> new FiniteDecodeDescriptor(Kind.WAV, metadata.sampleRate(), metadata.channels(), metadata.wavLayout());
            default -> throw new IllegalArgumentException(
                "modern finite playback supports MP3 and common WAV only, not " + metadata.format().id());
        };
    }
}
