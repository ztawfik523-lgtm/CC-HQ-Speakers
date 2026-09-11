package com.tom.hqspeaker.peripheral;

/**
 * Pure server-tick lifetime model for an HQ RAW feed.
 *
 * This deliberately knows nothing about Minecraft, networking, or renderers. The composite owns those concerns and
 * tells this state object whether the inherited outbound queue still contains RAW packets.
 */
final class RawFeedLifetime {
    static final int SAMPLE_RATE = 48_000;
    static final int GRACE_TICKS = 20;

    private long drainTicks;
    private int idleTicks;

    void acceptedSamples(int samples) {
        if (samples <= 0) throw new IllegalArgumentException("samples must be positive");
        long ticks = Math.max(1L, (samples * 20L + SAMPLE_RATE - 1L) / SAMPLE_RATE);
        drainTicks += ticks;
        idleTicks = 0;
    }

    /**
     * Advance one server tick.
     *
     * @param queueHasData Whether the inherited server RAW packet queue still contains unsent data.
     * @return true once the feed has drained and the idle grace elapsed, meaning the caller should close the source.
     */
    boolean tick(boolean queueHasData) {
        if (drainTicks > 0L) drainTicks--;
        if (queueHasData || drainTicks > 0L) {
            idleTicks = 0;
            return false;
        }
        return ++idleTicks >= GRACE_TICKS;
    }

    boolean active(boolean queueHasData) {
        return queueHasData || drainTicks > 0L || idleTicks < GRACE_TICKS;
    }

    long drainTicks() {
        return drainTicks;
    }

    int idleTicks() {
        return idleTicks;
    }

    void clear() {
        drainTicks = 0L;
        idleTicks = 0;
    }
}
