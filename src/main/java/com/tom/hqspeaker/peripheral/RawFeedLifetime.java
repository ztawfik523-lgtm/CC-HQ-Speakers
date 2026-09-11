package com.tom.hqspeaker.peripheral;

/**
 * Pure server-tick lifetime/capacity model for an HQ RAW feed.
 *
 * This deliberately knows nothing about Minecraft, networking, or renderers. The composite owns those concerns and
 * tells this state object whether the inherited outbound queue still contains RAW packets.
 *
 * Outstanding audio is tracked in samples rather than packet count. At 48 kHz and 20 server ticks/s, 2,400 samples
 * become playable per server tick. This lets the producer be paced by actual audio duration instead of how quickly
 * packets can be copied out of the server queue.
 *
 * Methods are synchronized because accepted samples may arrive from a ComputerCraft computer thread while lifecycle
 * ticking happens on the server thread.
 */
final class RawFeedLifetime {
    static final int SAMPLE_RATE = 48_000;
    static final int SAMPLES_PER_TICK = SAMPLE_RATE / 20;
    static final int GRACE_TICKS = 20;

    private long outstandingSamples;
    private int idleTicks;

    synchronized boolean canAccept(int samples, long capacitySamples) {
        if (samples <= 0) throw new IllegalArgumentException("samples must be positive");
        if (capacitySamples <= 0) throw new IllegalArgumentException("capacity must be positive");
        return samples <= capacitySamples && outstandingSamples <= capacitySamples - samples;
    }

    synchronized void acceptedSamples(int samples) {
        if (samples <= 0) throw new IllegalArgumentException("samples must be positive");
        outstandingSamples += samples;
        idleTicks = 0;
    }

    /**
     * Advance one server tick.
     *
     * @param queueHasData Whether the inherited server RAW packet queue still contains unsent data.
     * @return true once the feed has drained and the idle grace elapsed, meaning the caller should close the source.
     */
    synchronized boolean tick(boolean queueHasData) {
        outstandingSamples = Math.max(0L, outstandingSamples - SAMPLES_PER_TICK);
        if (queueHasData || outstandingSamples > 0L) {
            idleTicks = 0;
            return false;
        }
        return ++idleTicks >= GRACE_TICKS;
    }

    synchronized boolean active(boolean queueHasData) {
        return queueHasData || outstandingSamples > 0L || idleTicks < GRACE_TICKS;
    }

    synchronized long outstandingSamples() {
        return outstandingSamples;
    }

    synchronized long drainTicks() {
        return (outstandingSamples + SAMPLES_PER_TICK - 1L) / SAMPLES_PER_TICK;
    }

    synchronized int idleTicks() {
        return idleTicks;
    }

    synchronized void clear() {
        outstandingSamples = 0L;
        idleTicks = 0;
    }
}
