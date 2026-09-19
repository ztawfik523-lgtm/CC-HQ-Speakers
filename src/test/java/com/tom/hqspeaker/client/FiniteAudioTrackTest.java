package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class FiniteAudioTrackTest {
    @Test
    void durationAndReadsUseCompleteFrames() {
        FiniteAudioTrack track = new FiniteAudioTrack(
            new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, false);

        assertEquals(1.0, track.durationSeconds());
        assertEquals(4, track.frameCount());
        assertArrayEquals(new byte[]{0, 1, 2, 3}, bytes(track.read(5)));
        assertArrayEquals(new byte[]{4, 5, 6, 7}, bytes(track.read(8)));
        assertNull(track.read(8));
        assertTrue(track.isAtEnd());
    }

    @Test
    void seekClampsAndRetainedPcmSupportsBackwardSeek() {
        FiniteAudioTrack track = new FiniteAudioTrack(
            new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, false);

        assertEquals(0.5, track.seek(0.5));
        assertArrayEquals(new byte[]{4, 5, 6, 7}, bytes(track.read(8)));
        assertEquals(0.0, track.seek(-20.0));
        assertArrayEquals(new byte[]{0, 1}, bytes(track.read(2)));
        assertEquals(1.0, track.seek(20.0));
        assertNull(track.read(2));
    }

    @Test
    void seekExactlyToDurationIsAtEnd() {
        FiniteAudioTrack track = new FiniteAudioTrack(
            new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, false);

        assertEquals(track.durationSeconds(), track.seek(track.durationSeconds()));
        assertTrue(track.isAtEnd());
        assertNull(track.read(2));
    }

    @Test
    void loopingRewindsWithoutReplacingPcm() {
        FiniteAudioTrack track = new FiniteAudioTrack(new byte[]{10, 11, 12, 13}, 2, true);

        assertArrayEquals(new byte[]{10, 11, 12, 13, 10, 11}, bytes(track.read(6)));
        assertFalse(track.isAtEnd());
        track.setLooping(false);
        assertArrayEquals(new byte[]{12, 13}, bytes(track.read(8)));
        assertNull(track.read(2));
    }

    @Test
    void disablingLoopPreservesCurrentCursor() {
        FiniteAudioTrack track = new FiniteAudioTrack(
            new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, true);

        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 0, 1}, bytes(track.read(10)));
        assertEquals(0.25, track.cursorSeconds());

        track.setLooping(false);

        assertEquals(0.25, track.cursorSeconds());
        assertArrayEquals(new byte[]{2, 3, 4, 5, 6, 7}, bytes(track.read(16)));
        assertTrue(track.isAtEnd());
    }

    @Test
    void rendererForksHaveIndependentSeekCursors() {
        FiniteAudioTrack retained = new FiniteAudioTrack(
            new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, false);
        FiniteAudioTrack first = retained.fork(0.0, false);
        FiniteAudioTrack seeked = retained.fork(0.5, false);

        assertArrayEquals(new byte[]{0, 1}, bytes(first.read(2)));
        assertArrayEquals(new byte[]{4, 5}, bytes(seeked.read(2)));
        assertArrayEquals(new byte[]{2, 3}, bytes(first.read(2)));
    }

    @Test
    void rejectsEmptyAndPartialFrames() {
        assertThrows(IllegalArgumentException.class,
            () -> new FiniteAudioTrack(new byte[0], 48_000, false));
        assertThrows(IllegalArgumentException.class,
            () -> new FiniteAudioTrack(new byte[]{1}, 48_000, false));
        assertThrows(IllegalArgumentException.class,
            () -> new FiniteAudioTrack(new byte[]{1, 2}, 0, false));
    }

    private static byte[] bytes(ByteBuffer buffer) {
        assertNotNull(buffer);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
