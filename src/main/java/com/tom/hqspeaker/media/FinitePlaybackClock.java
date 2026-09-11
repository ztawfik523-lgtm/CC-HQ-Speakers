package com.tom.hqspeaker.media;

/**
 * Small, deterministic semantic clock for finite media playback.
 * Time is supplied by callers so this can be tested without Minecraft.
 */
public final class FinitePlaybackClock {
    private double duration;
    private double basePosition;
    private long anchorNanos;
    private boolean playing;
    private boolean looping;

    public FinitePlaybackClock(boolean looping) {
        this.looping = looping;
    }

    public double duration() { return duration; }
    public boolean looping() { return looping; }
    public boolean playing() { return playing; }

    public void setDuration(double duration, long now) {
        double current = position(now);
        this.duration = Double.isFinite(duration) && duration > 0.0 ? duration : 0.0;
        this.basePosition = normalize(current, this.looping);
        this.anchorNanos = now;
    }

    public double position(long now) {
        double value = basePosition;
        if (playing) value += Math.max(0L, now - anchorNanos) / 1_000_000_000.0;
        return normalize(value, looping);
    }

    public void start(long now) {
        anchorNanos = now;
        playing = true;
    }

    public void pause(long now) {
        basePosition = position(now);
        anchorNanos = now;
        playing = false;
    }

    public void resume(long now) {
        anchorNanos = now;
        playing = true;
    }

    public double seek(double seconds, long now) {
        if (!Double.isFinite(seconds)) seconds = 0.0;
        double target = duration > 0.0 ? Math.max(0.0, Math.min(duration, seconds)) : Math.max(0.0, seconds);
        if (looping && duration > 0.0 && target >= duration) target = 0.0;
        basePosition = target;
        anchorNanos = now;
        return target;
    }

    /** Rebase before changing loop state so disabling loop after one or more wraps never jumps to EOF. */
    public void setLooping(boolean looping, long now) {
        double current = position(now);
        this.looping = looping;
        this.basePosition = normalize(current, looping);
        this.anchorNanos = now;
    }

    public void finish(long now) {
        basePosition = duration > 0.0 ? duration : position(now);
        anchorNanos = now;
        playing = false;
    }

    private double normalize(double value, boolean asLooping) {
        value = Math.max(0.0, value);
        if (duration <= 0.0) return value;
        if (asLooping) {
            double wrapped = value % duration;
            return wrapped < 0.0 ? wrapped + duration : wrapped;
        }
        return Math.min(value, duration);
    }
}
