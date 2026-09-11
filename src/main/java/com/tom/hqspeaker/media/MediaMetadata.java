package com.tom.hqspeaker.media;

import java.util.List;
import java.util.Map;

/** Server-derived finite-media facts. These describe the encoded asset, not a client renderer. */
public record MediaMetadata(
    FiniteMediaFormat format,
    double durationSeconds,
    int sampleRate,
    int channels,
    int bitsPerSample,
    List<MediaSeekPoint> seekPoints
) {
    public MediaMetadata {
        if (format == null) throw new NullPointerException("format");
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be finite and positive");
        }
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels <= 0 || channels > 8) throw new IllegalArgumentException("channels must be between 1 and 8");
        if (bitsPerSample < 0) throw new IllegalArgumentException("bitsPerSample must be non-negative");
        seekPoints = seekPoints == null ? List.of() : List.copyOf(seekPoints);
    }

    public Map<String, Object> toLuaMap(long sizeBytes, String sourceName) {
        return Map.of(
            "format", format.id(),
            "duration", durationSeconds,
            "sampleRate", sampleRate,
            "channels", channels,
            "bitsPerSample", bitsPerSample,
            "sizeBytes", sizeBytes,
            "sourceName", sourceName == null ? "" : sourceName
        );
    }
}
