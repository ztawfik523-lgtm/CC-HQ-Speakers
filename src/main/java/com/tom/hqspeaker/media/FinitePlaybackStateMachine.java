package com.tom.hqspeaker.media;

/**
 * Compatibility wrapper around {@link FinitePlaybackAuthority} for the existing single-playback component tests.
 *
 * <p>M1J moves canonical timeline/state into {@code FinitePlaybackAuthority}. Volume remains endpoint-local so a
 * future multispeaker playback can share one clock without forcing every physical speaker to share gain.</p>
 */
public final class FinitePlaybackStateMachine {
    public enum State { PLAYING, PAUSED, ENDED, ERROR }

    public record SeekResult(boolean accepted, double position, boolean ended) {
        static SeekResult rejected(double position, boolean ended) {
            return new SeekResult(false, position, ended);
        }
    }

    private final FinitePlaybackAuthority authority;
    private float volume;

    public FinitePlaybackStateMachine(double durationSeconds, double initialVolume, long nowNanos) {
        if (!Double.isFinite(initialVolume)) throw new IllegalArgumentException("initialVolume must be finite");
        authority = new FinitePlaybackAuthority(durationSeconds, nowNanos);
        volume = clampVolume(initialVolume);
    }

    public State state() { return map(authority.state()); }
    public double duration() { return authority.duration(); }
    public double position(long nowNanos) { return authority.position(nowNanos); }
    public boolean looping() { return authority.looping(); }
    public float volume() { return volume; }
    public String error() { return authority.error(); }

    public boolean active() { return authority.active(); }
    public boolean terminal() { return authority.terminal(); }

    public boolean finalizeNaturalEnd(long nowNanos) {
        return authority.finalizeNaturalEnd(nowNanos);
    }

    public boolean pause(long nowNanos) { return authority.pause(nowNanos); }
    public boolean resume(long nowNanos) { return authority.resume(nowNanos); }

    public SeekResult seek(double seconds, long nowNanos) {
        FinitePlaybackAuthority.SeekResult result = authority.seek(seconds, nowNanos);
        return new SeekResult(result.accepted(), result.position(), result.ended());
    }

    public boolean setVolume(double volume) {
        if (!Double.isFinite(volume)) throw new IllegalArgumentException("volume must be finite");
        if (terminal()) return false;
        this.volume = clampVolume(volume);
        return true;
    }

    public boolean setLooping(boolean looping, long nowNanos) {
        return authority.setLooping(looping, nowNanos);
    }

    public boolean fail(String detail, long nowNanos) {
        return authority.fail(detail, nowNanos);
    }

    private static State map(FinitePlaybackAuthority.State state) {
        return switch (state) {
            case PLAYING -> State.PLAYING;
            case PAUSED -> State.PAUSED;
            case ENDED -> State.ENDED;
            case ERROR -> State.ERROR;
        };
    }

    private static float clampVolume(double volume) {
        return (float) Math.max(0.0, Math.min(3.0, volume));
    }
}
