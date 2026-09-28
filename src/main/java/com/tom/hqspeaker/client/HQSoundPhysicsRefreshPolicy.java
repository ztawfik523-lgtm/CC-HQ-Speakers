package com.tom.hqspeaker.client;

/**
 * Pure scheduling policy for HQ-only Sound Physics refreshes.
 *
 * <p>The expensive acoustic calculation is never approximated here. This class only decides when a full
 * Sound Physics refresh is due.</p>
 */
final class HQSoundPhysicsRefreshPolicy {
    static final double MOVEMENT_THRESHOLD = 0.15;
    static final double MOTION_ACTIVITY_THRESHOLD = 0.02;
    static final double SETTLE_RESIDUAL_THRESHOLD = 0.01;
    static final double URGENT_DISPLACEMENT_THRESHOLD = 1.0;

    static final long MIN_MOVEMENT_REFRESH_NANOS = 100_000_000L;
    static final long SETTLE_DELAY_NANOS = 250_000_000L;
    static final long SAFETY_REFRESH_NANOS = 1_000_000_000L;

    enum Reason {
        NONE,
        HARD_STALE,
        URGENT,
        MOVEMENT,
        SETTLE
    }

    private double evaluatedListenerX;
    private double evaluatedListenerY;
    private double evaluatedListenerZ;
    private double evaluatedSourceX;
    private double evaluatedSourceY;
    private double evaluatedSourceZ;

    private double motionListenerX;
    private double motionListenerY;
    private double motionListenerZ;
    private double motionSourceX;
    private double motionSourceY;
    private double motionSourceZ;

    private long lastRefreshNanos;
    private long lastMotionNanos;
    private boolean settleArmed;
    private boolean urgent;

    HQSoundPhysicsRefreshPolicy(
        long nowNanos,
        double listenerX, double listenerY, double listenerZ,
        double sourceX, double sourceY, double sourceZ
    ) {
        evaluatedListenerX = motionListenerX = listenerX;
        evaluatedListenerY = motionListenerY = listenerY;
        evaluatedListenerZ = motionListenerZ = listenerZ;
        evaluatedSourceX = motionSourceX = sourceX;
        evaluatedSourceY = motionSourceY = sourceY;
        evaluatedSourceZ = motionSourceZ = sourceZ;
        lastRefreshNanos = nowNanos;
        lastMotionNanos = nowNanos;
    }

    Reason due(
        long nowNanos,
        double listenerX, double listenerY, double listenerZ,
        double sourceX, double sourceY, double sourceZ
    ) {
        observeMotion(nowNanos, listenerX, listenerY, listenerZ, sourceX, sourceY, sourceZ);

        long age = Math.max(0L, nowNanos - lastRefreshNanos);
        double listenerDisplacement = distanceSquared(
            listenerX, listenerY, listenerZ,
            evaluatedListenerX, evaluatedListenerY, evaluatedListenerZ);
        double sourceDisplacement = distanceSquared(
            sourceX, sourceY, sourceZ,
            evaluatedSourceX, evaluatedSourceY, evaluatedSourceZ);
        double displacement = Math.max(listenerDisplacement, sourceDisplacement);

        if (age >= SAFETY_REFRESH_NANOS) return Reason.HARD_STALE;
        if (urgent || displacement >= square(URGENT_DISPLACEMENT_THRESHOLD)) return Reason.URGENT;
        if (displacement >= square(MOVEMENT_THRESHOLD) && age >= MIN_MOVEMENT_REFRESH_NANOS) {
            return Reason.MOVEMENT;
        }

        if (settleArmed && nowNanos - lastMotionNanos >= SETTLE_DELAY_NANOS) {
            settleArmed = false;
            if (displacement >= square(SETTLE_RESIDUAL_THRESHOLD)) return Reason.SETTLE;
        }

        return Reason.NONE;
    }

    void markUrgent() {
        urgent = true;
    }

    void recordRefresh(
        long nowNanos,
        double listenerX, double listenerY, double listenerZ,
        double sourceX, double sourceY, double sourceZ,
        Reason reason
    ) {
        double previousDisplacement = Math.max(
            distanceSquared(listenerX, listenerY, listenerZ,
                evaluatedListenerX, evaluatedListenerY, evaluatedListenerZ),
            distanceSquared(sourceX, sourceY, sourceZ,
                evaluatedSourceX, evaluatedSourceY, evaluatedSourceZ));

        evaluatedListenerX = listenerX;
        evaluatedListenerY = listenerY;
        evaluatedListenerZ = listenerZ;
        evaluatedSourceX = sourceX;
        evaluatedSourceY = sourceY;
        evaluatedSourceZ = sourceZ;

        motionListenerX = listenerX;
        motionListenerY = listenerY;
        motionListenerZ = listenerZ;
        motionSourceX = sourceX;
        motionSourceY = sourceY;
        motionSourceZ = sourceZ;

        lastRefreshNanos = nowNanos;
        urgent = false;

        boolean positionalRefresh =
            reason == Reason.MOVEMENT
                || (reason == Reason.URGENT && previousDisplacement >= square(MOVEMENT_THRESHOLD));
        settleArmed = positionalRefresh;
        if (positionalRefresh) lastMotionNanos = nowNanos;
    }

    void recordExternalRefresh(
        long nowNanos,
        double listenerX, double listenerY, double listenerZ,
        double sourceX, double sourceY, double sourceZ
    ) {
        evaluatedListenerX = listenerX;
        evaluatedListenerY = listenerY;
        evaluatedListenerZ = listenerZ;
        evaluatedSourceX = sourceX;
        evaluatedSourceY = sourceY;
        evaluatedSourceZ = sourceZ;

        motionListenerX = listenerX;
        motionListenerY = listenerY;
        motionListenerZ = listenerZ;
        motionSourceX = sourceX;
        motionSourceY = sourceY;
        motionSourceZ = sourceZ;

        lastRefreshNanos = nowNanos;
        lastMotionNanos = nowNanos;
        settleArmed = false;
        urgent = false;
    }

    long lastRefreshNanos() {
        return lastRefreshNanos;
    }

    private void observeMotion(
        long nowNanos,
        double listenerX, double listenerY, double listenerZ,
        double sourceX, double sourceY, double sourceZ
    ) {
        double activityThresholdSquared = square(MOTION_ACTIVITY_THRESHOLD);
        boolean listenerMoved = distanceSquared(
            listenerX, listenerY, listenerZ,
            motionListenerX, motionListenerY, motionListenerZ) >= activityThresholdSquared;
        boolean sourceMoved = distanceSquared(
            sourceX, sourceY, sourceZ,
            motionSourceX, motionSourceY, motionSourceZ) >= activityThresholdSquared;

        if (!listenerMoved && !sourceMoved) return;

        motionListenerX = listenerX;
        motionListenerY = listenerY;
        motionListenerZ = listenerZ;
        motionSourceX = sourceX;
        motionSourceY = sourceY;
        motionSourceZ = sourceZ;
        lastMotionNanos = nowNanos;
    }

    private static double distanceSquared(
        double ax, double ay, double az,
        double bx, double by, double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }
}
