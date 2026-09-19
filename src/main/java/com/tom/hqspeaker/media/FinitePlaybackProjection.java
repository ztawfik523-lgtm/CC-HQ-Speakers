package com.tom.hqspeaker.media;

/**
 * Client-side projection of one server-authoritative shared playback timeline.
 *
 * <p>Multiple physical speaker endpoints may receive equivalent STATE packets at slightly different local times.
 * A packet with the same state revision must not move the local anchor, otherwise each endpoint would create its
 * own packet-arrival clock again. A newer revision deliberately re-anchors the shared projection.</p>
 */
public final class FinitePlaybackProjection {
    private long stateRevision;
    private double position;
    private double duration;
    private long snapshotNanos;
    private boolean paused;
    private boolean looping;

    public synchronized boolean observe(long revision, double position, double duration,
                                        boolean paused, boolean looping, long nowNanos) {
        if (revision <= 0L || !Double.isFinite(position) || !Double.isFinite(duration) || duration <= 0.0) {
            throw new IllegalArgumentException("invalid finite playback projection");
        }
        if (revision < stateRevision) return false;
        if (stateRevision == 0L || revision > stateRevision) {
            stateRevision = revision;
            this.position = Math.max(0.0, Math.min(position, duration));
            this.duration = duration;
            this.paused = paused;
            this.looping = looping;
            snapshotNanos = nowNanos;
        }
        return true;
    }

    public synchronized long revision() {
        return stateRevision;
    }

    public synchronized double projected(long nowNanos) {
        double result = position;
        if (!paused && snapshotNanos > 0L) {
            result += Math.max(0L, nowNanos - snapshotNanos) / 1_000_000_000.0;
        }
        if (duration > 0.0) {
            if (looping) {
                result %= duration;
                if (result < 0.0) result += duration;
            } else {
                result = Math.min(result, duration);
            }
        }
        return Math.max(0.0, result);
    }
}
