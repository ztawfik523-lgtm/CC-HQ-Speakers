package com.tom.hqspeaker.media;

import java.util.Objects;
import java.util.UUID;

/**
 * Thread-safe canonical finite playback authority.
 *
 * <p>This deliberately owns only facts which must be identical for every physical speaker endpoint attached to
 * one playback. Endpoint gain, listener membership, transport, renderer and recovery remain endpoint-local.</p>
 */
public final class FinitePlaybackAuthority {
    public enum State { PLAYING, PAUSED, ENDED, ERROR }

    public record SeekResult(boolean accepted, double position, boolean ended) {
        static SeekResult rejected(double position, boolean ended) {
            return new SeekResult(false, position, ended);
        }
    }

    /** Atomic read of one canonical playback instant. */
    public record Snapshot(
        UUID playbackId,
        State state,
        double position,
        double duration,
        boolean looping,
        long stateRevision,
        long decodeRevision,
        String error
    ) {}

    private final UUID playbackId;
    private final FinitePlaybackClock clock;
    private State state;
    private String error = "";
    private long stateRevision = 1L;
    private long decodeRevision = 1L;

    public FinitePlaybackAuthority(double durationSeconds, long nowNanos) {
        this(UUID.randomUUID(), durationSeconds, nowNanos);
    }

    public FinitePlaybackAuthority(UUID playbackId, double durationSeconds, long nowNanos) {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be finite and positive");
        }
        this.playbackId = Objects.requireNonNull(playbackId, "playbackId");
        clock = new FinitePlaybackClock(false);
        clock.setDuration(durationSeconds, nowNanos);
        clock.start(nowNanos);
        state = State.PLAYING;
    }

    public UUID playbackId() { return playbackId; }

    public synchronized State state() { return state; }
    public synchronized double duration() { return clock.duration(); }
    public synchronized double position(long nowNanos) { return clock.position(nowNanos); }
    public synchronized boolean looping() { return clock.looping(); }
    public synchronized String error() { return error; }
    public synchronized long stateRevision() { return stateRevision; }
    public synchronized long decodeRevision() { return decodeRevision; }

    public synchronized Snapshot snapshot(long nowNanos) {
        return new Snapshot(playbackId, state, clock.position(nowNanos), clock.duration(), clock.looping(),
            stateRevision, decodeRevision, error);
    }

    public synchronized boolean active() {
        return state == State.PLAYING || state == State.PAUSED;
    }

    public synchronized boolean terminal() {
        return state == State.ENDED || state == State.ERROR;
    }

    /** Finalize non-looping natural EOF exactly once for every endpoint sharing this authority. */
    public synchronized boolean finalizeNaturalEnd(long nowNanos) {
        if (state != State.PLAYING || !clock.reachedEnd(nowNanos)) return false;
        clock.finish(nowNanos);
        state = State.ENDED;
        bumpStateRevision();
        return true;
    }

    public synchronized boolean pause(long nowNanos) {
        if (state != State.PLAYING) return false;
        clock.pause(nowNanos);
        state = State.PAUSED;
        bumpStateRevision();
        return true;
    }

    public synchronized boolean resume(long nowNanos) {
        if (state != State.PAUSED) return false;
        clock.resume(nowNanos);
        state = State.PLAYING;
        bumpStateRevision();
        return true;
    }

    public synchronized SeekResult seek(double seconds, long nowNanos) {
        if (!Double.isFinite(seconds)) throw new IllegalArgumentException("seconds must be finite");
        if (terminal()) return SeekResult.rejected(clock.position(nowNanos), state == State.ENDED);

        // A semantic seek needs a fresh codec epoch unless it lands at terminal non-looping EOF.
        if (decodeRevision == Long.MAX_VALUE) {
            failInternal("finite decoder revision exhausted", nowNanos);
            return SeekResult.rejected(clock.position(nowNanos), false);
        }

        double target = clock.seek(seconds, nowNanos);
        if (!clock.looping() && target >= clock.duration()) {
            clock.finish(nowNanos);
            state = State.ENDED;
            bumpStateRevision();
            return new SeekResult(true, clock.duration(), true);
        }

        decodeRevision++;
        bumpStateRevision();
        return new SeekResult(true, target, false);
    }

    public synchronized boolean setLooping(boolean looping, long nowNanos) {
        if (terminal()) return false;
        clock.setLooping(looping, nowNanos);
        bumpStateRevision();
        return true;
    }

    /**
     * Enter shared terminal ERROR and freeze the canonical position.
     *
     * <p>Endpoint-local renderer/transport failures must not call this once multiple endpoints share the authority.
     * It is reserved for failures which invalidate the canonical playback itself.</p>
     */
    public synchronized boolean fail(String detail, long nowNanos) {
        if (terminal()) return false;
        failInternal(detail, nowNanos);
        return true;
    }

    private void failInternal(String detail, long nowNanos) {
        if (state == State.PLAYING) clock.pause(nowNanos);
        state = State.ERROR;
        error = detail == null ? "" : detail;
        bumpStateRevision();
    }

    private void bumpStateRevision() {
        if (stateRevision == Long.MAX_VALUE) {
            // State revision is ordering metadata rather than media time. Saturation is safer than wraparound.
            return;
        }
        stateRevision++;
    }
}
