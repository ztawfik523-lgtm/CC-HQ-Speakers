package com.tom.hqspeaker.media;

/** Codec-aware encoded anchor selection for the modern finite decoder. */
public final class FiniteDecodeAnchorSelector {
    public static final double MP3_PRE_ROLL_SECONDS = 1.0;

    public record Anchor(long offset, double seconds) {
        public Anchor {
            if (offset < 0L) throw new IllegalArgumentException("offset must be non-negative");
            if (!Double.isFinite(seconds) || seconds < 0.0) {
                throw new IllegalArgumentException("seconds must be finite and non-negative");
            }
        }
    }

    private FiniteDecodeAnchorSelector() {}

    public static Anchor select(MediaMetadata metadata, long totalBytes, double positionSeconds) {
        if (metadata == null) throw new NullPointerException("metadata");
        if (totalBytes <= 0L) throw new IllegalArgumentException("totalBytes must be positive");
        if (!Double.isFinite(positionSeconds)) throw new IllegalArgumentException("positionSeconds must be finite");

        double position = Math.max(0.0, Math.min(metadata.durationSeconds(), positionSeconds));
        return switch (metadata.format()) {
            case MP3 -> selectMp3(metadata, totalBytes, position);
            case WAV -> selectWav(metadata, totalBytes, position);
            default -> throw new IllegalArgumentException(
                "modern finite anchor selection supports MP3/common WAV only, not " + metadata.format().id());
        };
    }

    private static Anchor selectMp3(MediaMetadata metadata, long totalBytes, double position) {
        double safeTime = Math.max(0.0, position - MP3_PRE_ROLL_SECONDS);
        MediaSeekPoint earliest = null;
        MediaSeekPoint best = null;
        for (MediaSeekPoint point : metadata.seekPoints()) {
            if (point == null || !Double.isFinite(point.seconds()) || point.seconds() < 0.0
                    || point.byteOffset() < 0L || point.byteOffset() >= totalBytes) continue;
            if (earliest == null || point.seconds() < earliest.seconds()) earliest = point;
            if (point.seconds() <= safeTime + 1.0e-9
                    && (best == null || point.seconds() > best.seconds())) {
                best = point;
            }
        }
        MediaSeekPoint selected = best != null ? best : earliest;
        return selected == null ? new Anchor(0L, 0.0) : new Anchor(selected.byteOffset(), selected.seconds());
    }

    private static Anchor selectWav(MediaMetadata metadata, long totalBytes, double position) {
        WavLayout layout = metadata.wavLayout();
        if (layout == null) throw new IllegalArgumentException("common WAV metadata is missing normalized layout");
        if (layout.dataOffset() > totalBytes || layout.dataLength() > totalBytes - layout.dataOffset()) {
            throw new IllegalArgumentException("WAV layout lies outside encoded asset");
        }

        long totalFrames = layout.frameCount();
        long frame = (long) Math.floor(position * layout.sampleRate() + 1.0e-9);
        frame = Math.max(0L, Math.min(totalFrames, frame));
        long offset = layout.byteOffsetForFrame(frame);
        return new Anchor(offset, frame / (double) layout.sampleRate());
    }
}
