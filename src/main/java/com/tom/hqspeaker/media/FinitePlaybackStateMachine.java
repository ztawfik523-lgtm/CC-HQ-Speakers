package com.tom.hqspeaker.media;

/**
 * Deterministic server-side semantic state machine for one finite playback.
 *
 * <p>This owns only canonical playback truth: state, position, pause/resume, seek, loop, volume, EOF and terminal
 * error freeze. Networking, asset ownership and rendering remain separate concerns.</p>
 */
public final class FinitePlaybackStateMachine {
    public enum State { PLAYING, PAUSED, ENDED, ERROR }

    public record SeekResult(boolean accepted, double position, boolean ended) {
        static SeekResult rejected(double position, boolean ended) {
            return new SeekResult(false, position, ended);
        }
    }

    private final FinitePlaybackClock clock;
    private State state;
    private float volume;
    private String error = "";

    public FinitePlaybackStateMachine(double durationSeconds, double initialVolume, long nowNanos) {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be finite and positive");
        }
        if (!Double.isFinite(initialVolume)) throw new IllegalArgumentException("initialVolume must be finite");

        clock = new FinitePlaybackClock(false);
        clock.setDuration(durationSeconds, nowNanos);
        clock.start(nowNanos);
        volume = clampVolume(initialVolume);
        state = State.PLAYING;
    }

    public State state() { return state; }
    public double duration() { return clock.duration(); }
    public double position(long nowNanos) { return clock.position(nowNanos); }
    public boolean looping() { return clock.looping(); }
    public float volume() { return volume; }
    public String error() { return error; }

    public boolean active() {
        return state == State.PLAYING || state == State.PAUSED;
    }

    public boolean terminal() {
        return state == State.ENDED || state == State.ERROR;
    }

    /** Finalize non-looping natural EOF from the canonical server clock. */
    public boolean finalizeNaturalEnd(long nowNanos) {
        if (state != State.PLAYING || !clock.reachedEnd(nowNanos)) return false;
        clock.finish(nowNanos);
        state = State.ENDED;
        return true;
    }

    public boolean pause(long nowNanos) {
        if (state != State.PLAYING) return false;
        clock.pause(nowNanos);
        state = State.PAUSED;
        return true;
    }

    public boolean resume(long nowNanos) {
        if (state != State.PAUSED) return false;
        clock.resume(nowNanos);
        state = State.PLAYING;
        return true;
    }

    public SeekResult seek(double seconds, long nowNanos) {
        if (!Double.isFinite(seconds)) throw new IllegalArgumentException("seconds must be finite");
        if (terminal()) return SeekResult.rejected(clock.position(nowNanos), state == State.ENDED);

        double target = clock.seek(seconds, nowNanos);
        if (!clock.looping() && target >= clock.duration()) {
            clock.finish(nowNanos);
            state = State.ENDED;
            return new SeekResult(true, clock.duration(), true);
        }
        return new SeekResult(true, target, false);
    }

    public boolean setVolume(double volume) {
        if (!Double.isFinite(volume)) throw new IllegalArgumentException("volume must be finite");
        if (terminal()) return false;
        this.volume = clampVolume(volume);
        return true;
    }

    public boolean setLooping(boolean looping, long nowNanos) {
        if (terminal()) return false;
        clock.setLooping(looping, nowNanos);
        return true;
    }

    /**
     * Enter terminal ERROR and freeze the canonical position at the failure instant.
     */
    public boolean fail(String detail, long nowNanos) {
        if (terminal()) return false;
        if (state == State.PLAYING) clock.pause(nowNanos);
        state = State.ERROR;
        error = detail == null ? "" : detail;
        return true;
    }

    private static float clampVolume(double volume) {
        return (float) Math.max(0.0, Math.min(3.0, volume));
    }
}
