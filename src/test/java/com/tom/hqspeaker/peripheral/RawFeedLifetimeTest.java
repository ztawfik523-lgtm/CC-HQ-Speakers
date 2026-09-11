package com.tom.hqspeaker.peripheral;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawFeedLifetimeTest {
    @Test
    void convertsAcceptedSamplesToServerTicks() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(4_800); // 0.1 s at 48 kHz = 2 ticks at 20 TPS.
        assertEquals(2L, lifetime.drainTicks());

        lifetime.acceptedSamples(1);
        assertEquals(3L, lifetime.drainTicks(), "non-empty RAW chunks consume at least one tick");
    }

    @Test
    void accumulatedRawDurationDrainsBeforeIdleGrace() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(4_800);
        lifetime.acceptedSamples(4_800);
        assertEquals(4L, lifetime.drainTicks());

        for (int i = 0; i < 3; i++) assertFalse(lifetime.tick(false));
        assertEquals(1L, lifetime.drainTicks());
        assertFalse(lifetime.tick(false));
        assertEquals(0L, lifetime.drainTicks());
        assertEquals(1, lifetime.idleTicks());

        for (int i = 1; i < RawFeedLifetime.GRACE_TICKS - 1; i++) assertFalse(lifetime.tick(false));
        assertTrue(lifetime.tick(false), "feed should close only after the full idle grace");
    }

    @Test
    void unsentServerQueuePreventsClosureAndResetsGrace() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(1);
        assertFalse(lifetime.tick(false));
        assertEquals(1, lifetime.idleTicks());

        for (int i = 0; i < 10; i++) assertFalse(lifetime.tick(true));
        assertEquals(0, lifetime.idleTicks());
        assertTrue(lifetime.active(true));
    }

    @Test
    void acceptingMoreSamplesResetsIdleGrace() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(1);
        for (int i = 0; i < 10; i++) assertFalse(lifetime.tick(false));
        assertEquals(10, lifetime.idleTicks());

        lifetime.acceptedSamples(2_400);
        assertEquals(0, lifetime.idleTicks());
        assertEquals(1L, lifetime.drainTicks());
        assertTrue(lifetime.active(false));
    }

    @Test
    void clearRemovesAllLifetimeState() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(131_072);
        lifetime.tick(false);
        lifetime.clear();
        assertEquals(0L, lifetime.drainTicks());
        assertEquals(0, lifetime.idleTicks());
    }

    @Test
    void rejectsEmptyAcceptedChunk() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        assertThrows(IllegalArgumentException.class, () -> lifetime.acceptedSamples(0));
    }
}
