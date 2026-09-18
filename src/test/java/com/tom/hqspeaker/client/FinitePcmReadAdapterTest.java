package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FinitePcmReadAdapterTest {
    @Test
    void starvationBecomesBoundedSilenceRatherThanEof() {
        FinitePcmQueue queue = new FinitePcmQueue(16);

        FinitePcmReadAdapter.Result result = FinitePcmReadAdapter.read(queue, 8192, 1920);

        assertEquals(FinitePcmReadAdapter.State.SILENCE, result.state());
        assertEquals(1920, result.data().length);
        assertTrue(Arrays.equals(new byte[1920], result.data()));
    }

    @Test
    void queuedPcmRemainsDataAndSampleAligned() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        queue.write(new byte[] { 1, 2, 3, 4 }, 0, 4);

        FinitePcmReadAdapter.Result result = FinitePcmReadAdapter.read(queue, 8, 4);

        assertEquals(FinitePcmReadAdapter.State.DATA, result.state());
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, result.data());
    }

    @Test
    void physicalEofAndCancellationStayDistinct() {
        FinitePcmQueue eof = new FinitePcmQueue(16);
        eof.markEof();
        assertEquals(FinitePcmReadAdapter.State.EOF,
            FinitePcmReadAdapter.read(eof, 8, 4).state());

        FinitePcmQueue cancelled = new FinitePcmQueue(16);
        cancelled.cancel();
        assertEquals(FinitePcmReadAdapter.State.CANCELLED,
            FinitePcmReadAdapter.read(cancelled, 8, 4).state());
    }

    @Test
    void silenceNeverExceedsMinecraftReadRequest() {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        FinitePcmReadAdapter.Result result = FinitePcmReadAdapter.read(queue, 512, 1920);

        assertEquals(FinitePcmReadAdapter.State.SILENCE, result.state());
        assertEquals(512, result.data().length);
    }
}
