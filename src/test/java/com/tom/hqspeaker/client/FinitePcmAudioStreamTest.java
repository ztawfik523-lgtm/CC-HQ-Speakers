package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class FinitePcmAudioStreamTest {
    @Test
    void starvationReturnsBoundedSilenceInsteadOfEof() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        FinitePcmAudioStream stream = new FinitePcmAudioStream(queue, 48_000);

        ByteBuffer buffer = stream.read(8 * 1024);
        assertNotNull(buffer);
        assertTrue(buffer.remaining() > 0);
        assertTrue(buffer.remaining() <= 4 * 1024);
        while (buffer.hasRemaining()) assertEquals(0, buffer.get());
        assertFalse(stream.reachedEof());
    }

    @Test
    void dataIsFrameAlignedAndPhysicalEofIsObservable() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        queue.write(new byte[] { 1, 2, 3, 4 }, 0, 4);
        FinitePcmAudioStream stream = new FinitePcmAudioStream(queue, 44_100);

        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, bytes(stream.read(5)));
        queue.markEof();
        assertNull(stream.read(4096));
        assertTrue(stream.reachedEof());
    }

    @Test
    void cancellationIsTerminalButNotPhysicalEof() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        FinitePcmAudioStream stream = new FinitePcmAudioStream(queue, 48_000);
        queue.cancel();

        assertNull(stream.read(4096));
        assertFalse(stream.reachedEof());
    }

    @Test
    void closeCancelsProducerQueue() {
        FinitePcmQueue queue = new FinitePcmQueue(16);
        FinitePcmAudioStream stream = new FinitePcmAudioStream(queue, 48_000);

        stream.close();
        assertTrue(stream.closed());
        assertTrue(queue.cancelled());
        assertFalse(stream.reachedEof());
    }

    private static byte[] bytes(ByteBuffer buffer) {
        assertNotNull(buffer);
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }
}
