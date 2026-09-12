package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinitePlaybackClockTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void startedClockAdvancesWithoutRendererHandshake() {
        FinitePlaybackClock clock = new FinitePlaybackClock(false);
        clock.setDuration(10.0, 0L);
        clock.start(0L);

        assertTrue(clock.playing());
        assertEquals(0.25, clock.position(SECOND / 4), 1e-6);
        assertEquals(1.0, clock.position(SECOND), 1e-6);
    }

    @Test
    void disablingLoopRebasesWrappedPosition() {
        FinitePlaybackClock clock = new FinitePlaybackClock(true);
        clock.setDuration(10.0, 0L);
        clock.start(0L);
        assertEquals(2.0, clock.position(12L * SECOND), 1e-6);

        clock.setLooping(false, 12L * SECOND);
        assertEquals(2.0, clock.position(12L * SECOND), 1e-6);
        assertEquals(5.0, clock.position(15L * SECOND), 1e-6);
    }

    @Test
    void exactEndSeekIsStableForNonLoopingTrack() {
        FinitePlaybackClock clock = new FinitePlaybackClock(false);
        clock.setDuration(5.0, 0L);
        clock.start(0L);
        assertEquals(5.0, clock.seek(5.0, SECOND), 1e-6);
        clock.finish(SECOND);
        assertFalse(clock.playing());
        assertEquals(5.0, clock.position(20L * SECOND), 1e-6);
    }

    @Test
    void exactEndSeekWrapsToZeroWhileLooping() {
        FinitePlaybackClock clock = new FinitePlaybackClock(true);
        clock.setDuration(5.0, 0L);
        assertEquals(0.0, clock.seek(5.0, SECOND), 1e-6);
    }

    @Test
    void pauseFreezesAndResumeContinues() {
        FinitePlaybackClock clock = new FinitePlaybackClock(false);
        clock.setDuration(30.0, 0L);
        clock.start(0L);
        clock.pause(3L * SECOND);
        assertEquals(3.0, clock.position(10L * SECOND), 1e-6);
        clock.resume(10L * SECOND);
        assertEquals(5.0, clock.position(12L * SECOND), 1e-6);
    }

    @Test
    void nonLoopingClockReportsNaturalEndAtDuration() {
        FinitePlaybackClock clock = new FinitePlaybackClock(false);
        clock.setDuration(5.0, 0L);
        clock.start(0L);

        assertFalse(clock.reachedEnd(4L * SECOND));
        assertTrue(clock.reachedEnd(5L * SECOND));
        assertTrue(clock.reachedEnd(20L * SECOND));
    }

    @Test
    void loopingClockNeverReportsNaturalEnd() {
        FinitePlaybackClock clock = new FinitePlaybackClock(true);
        clock.setDuration(5.0, 0L);
        clock.start(0L);

        assertFalse(clock.reachedEnd(5L * SECOND));
        assertFalse(clock.reachedEnd(20L * SECOND));
        assertEquals(0.0, clock.position(20L * SECOND), 1e-6);
    }
}
