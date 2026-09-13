package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinitePlaybackStateMachineTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void startsImmediatelyAndPauseResumeRemainServerOwned() {
        FinitePlaybackStateMachine playback = new FinitePlaybackStateMachine(10.0, 0.75, 0L);

        assertEquals(FinitePlaybackStateMachine.State.PLAYING, playback.state());
        assertEquals(0.75f, playback.volume(), 1.0e-6f);
        assertEquals(0.4, playback.position(400_000_000L), 1.0e-9);

        assertTrue(playback.pause(400_000_000L));
        assertEquals(FinitePlaybackStateMachine.State.PAUSED, playback.state());
        assertEquals(0.4, playback.position(2 * SECOND), 1.0e-9);

        assertTrue(playback.resume(2 * SECOND));
        assertEquals(FinitePlaybackStateMachine.State.PLAYING, playback.state());
        assertEquals(0.9, playback.position(2_500_000_000L), 1.0e-9);
    }

    @Test
    void exactEndSeekEndsNonLoopingAndWrapsLooping() {
        FinitePlaybackStateMachine nonLooping = new FinitePlaybackStateMachine(5.0, 1.0, 0L);
        var ended = nonLooping.seek(5.0, SECOND);

        assertTrue(ended.accepted());
        assertTrue(ended.ended());
        assertEquals(FinitePlaybackStateMachine.State.ENDED, nonLooping.state());
        assertEquals(5.0, nonLooping.position(10 * SECOND), 1.0e-9);

        FinitePlaybackStateMachine looping = new FinitePlaybackStateMachine(5.0, 1.0, 0L);
        assertTrue(looping.setLooping(true, SECOND));
        var wrapped = looping.seek(5.0, 2 * SECOND);

        assertTrue(wrapped.accepted());
        assertFalse(wrapped.ended());
        assertEquals(FinitePlaybackStateMachine.State.PLAYING, looping.state());
        assertEquals(0.0, wrapped.position(), 1.0e-9);
        assertEquals(0.25, looping.position(2_250_000_000L), 1.0e-9);
    }

    @Test
    void naturalEndIsCanonicalAndTerminal() {
        FinitePlaybackStateMachine playback = new FinitePlaybackStateMachine(1.0, 1.0, 0L);

        assertFalse(playback.finalizeNaturalEnd(999_000_000L));
        assertTrue(playback.finalizeNaturalEnd(SECOND));
        assertEquals(FinitePlaybackStateMachine.State.ENDED, playback.state());
        assertEquals(1.0, playback.position(5 * SECOND), 1.0e-9);
        assertFalse(playback.pause(5 * SECOND));
        assertFalse(playback.resume(5 * SECOND));
        assertFalse(playback.setLooping(true, 5 * SECOND));
        assertFalse(playback.setVolume(0.5));
    }

    @Test
    void serverErrorFreezesPositionAtFailureInstant() {
        FinitePlaybackStateMachine playback = new FinitePlaybackStateMachine(20.0, 1.0, 0L);

        assertTrue(playback.fail("range read failed", 3 * SECOND));
        assertEquals(FinitePlaybackStateMachine.State.ERROR, playback.state());
        assertEquals("range read failed", playback.error());
        assertEquals(3.0, playback.position(3 * SECOND), 1.0e-9);
        assertEquals(3.0, playback.position(15 * SECOND), 1.0e-9);
        assertFalse(playback.fail("second error", 16 * SECOND));
    }

    @Test
    void volumeIsClampedAndFiniteValuesOnly() {
        FinitePlaybackStateMachine playback = new FinitePlaybackStateMachine(5.0, 99.0, 0L);
        assertEquals(3.0f, playback.volume(), 1.0e-6f);

        assertTrue(playback.setVolume(-5.0));
        assertEquals(0.0f, playback.volume(), 1.0e-6f);
        assertThrows(IllegalArgumentException.class, () -> playback.setVolume(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> playback.seek(Double.POSITIVE_INFINITY, 0L));
    }
}
