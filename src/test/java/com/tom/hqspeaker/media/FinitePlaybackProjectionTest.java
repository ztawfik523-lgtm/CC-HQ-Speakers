package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinitePlaybackProjectionTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void sameRevisionFromAnotherEndpointDoesNotReanchorToItsArrivalTime() {
        FinitePlaybackProjection timeline = new FinitePlaybackProjection();

        assertTrue(timeline.observe(7L, 10.0, 120.0, false, false, SECOND));
        assertEquals(11.0, timeline.projected(2 * SECOND), 1.0e-9);

        // Same server state, arriving a full second later from another physical endpoint.
        assertTrue(timeline.observe(7L, 10.0, 120.0, false, false, 2 * SECOND));
        assertEquals(12.0, timeline.projected(3 * SECOND), 1.0e-9);
    }

    @Test
    void newerRevisionReanchorsTheSharedClock() {
        FinitePlaybackProjection timeline = new FinitePlaybackProjection();
        assertTrue(timeline.observe(3L, 30.0, 120.0, false, false, SECOND));

        assertTrue(timeline.observe(4L, 5.0, 120.0, false, false, 4 * SECOND));
        assertEquals(6.0, timeline.projected(5 * SECOND), 1.0e-9);
        assertEquals(4L, timeline.revision());
    }

    @Test
    void staleRevisionIsRejected() {
        FinitePlaybackProjection timeline = new FinitePlaybackProjection();
        assertTrue(timeline.observe(9L, 20.0, 120.0, false, false, SECOND));
        assertFalse(timeline.observe(8L, 80.0, 120.0, false, false, 2 * SECOND));
        assertEquals(22.0, timeline.projected(3 * SECOND), 1.0e-9);
    }

    @Test
    void pauseAndLoopProjectionStayCanonical() {
        FinitePlaybackProjection timeline = new FinitePlaybackProjection();
        assertTrue(timeline.observe(1L, 9.5, 10.0, true, false, SECOND));
        assertEquals(9.5, timeline.projected(20 * SECOND), 1.0e-9);

        assertTrue(timeline.observe(2L, 9.5, 10.0, false, true, 20 * SECOND));
        assertEquals(0.5, timeline.projected(21 * SECOND), 1.0e-9);
    }
}
