package com.tom.hqspeaker.peripheral;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawFeedLifetimeTest {
    @Test
    void convertsOutstandingSamplesToServerTicks() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(4_800); // 0.1 s at 48 kHz = 2 ticks at 20 TPS.
        assertEquals(4_800L, lifetime.outstandingSamples());
        assertEquals(2L, lifetime.drainTicks());

        lifetime.acceptedSamples(1);
        assertEquals(4_801L, lifetime.outstandingSamples());
        assertEquals(3L, lifetime.drainTicks());
    }

    @Test
    void tinyChunksDoNotRoundUpIndividually() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(1);
        lifetime.acceptedSamples(1);
        assertEquals(2L, lifetime.outstandingSamples());
        assertEquals(1L, lifetime.drainTicks());
    }

    @Test
    void capacityTracksPlayableAudioNotPacketCount() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        long capacity = 131_072L;

        assertTrue(lifetime.canAccept(131_072, capacity));
        lifetime.acceptedSamples(131_072);
        assertFalse(lifetime.canAccept(1, capacity));

        assertFalse(lifetime.tick(false));
        assertEquals(128_672L, lifetime.outstandingSamples());
        assertTrue(lifetime.canAccept(2_400, capacity));
        assertFalse(lifetime.canAccept(2_401, capacity));
    }

    @Test
    void accumulatedRawDurationDrainsBeforeIdleGrace() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(4_800);
        lifetime.acceptedSamples(4_800);
        assertEquals(9_600L, lifetime.outstandingSamples());
        assertEquals(4L, lifetime.drainTicks());

        for (int i = 0; i < 3; i++) assertFalse(lifetime.tick(false));
        assertEquals(2_400L, lifetime.outstandingSamples());
        assertEquals(1L, lifetime.drainTicks());
        assertFalse(lifetime.tick(false));
        assertEquals(0L, lifetime.outstandingSamples());
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
        assertEquals(2_400L, lifetime.outstandingSamples());
        assertEquals(1L, lifetime.drainTicks());
        assertTrue(lifetime.active(false));
    }

    @Test
    void clearRemovesAllLifetimeState() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        lifetime.acceptedSamples(131_072);
        lifetime.tick(false);
        lifetime.clear();
        assertEquals(0L, lifetime.outstandingSamples());
        assertEquals(0L, lifetime.drainTicks());
        assertEquals(0, lifetime.idleTicks());
    }

    @Test
    void rejectsInvalidCapacityArguments() {
        RawFeedLifetime lifetime = new RawFeedLifetime();
        assertThrows(IllegalArgumentException.class, () -> lifetime.acceptedSamples(0));
        assertThrows(IllegalArgumentException.class, () -> lifetime.canAccept(0, 131_072));
        assertThrows(IllegalArgumentException.class, () -> lifetime.canAccept(1, 0));
    }
}
