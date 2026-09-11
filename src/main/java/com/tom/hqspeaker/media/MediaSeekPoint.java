package com.tom.hqspeaker.media;

/** Coarse encoded-file seek hint for later range/decoder work. */
public record MediaSeekPoint(double seconds, long byteOffset) {
    public MediaSeekPoint {
        if (!Double.isFinite(seconds) || seconds < 0.0) throw new IllegalArgumentException("seconds must be finite and non-negative");
        if (byteOffset < 0L) throw new IllegalArgumentException("byteOffset must be non-negative");
    }
}
