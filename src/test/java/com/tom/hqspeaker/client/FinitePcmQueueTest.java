package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinitePcmQueueTest {
    @Test
    void emptyLiveQueueIsStarvedNotEof() {
        FinitePcmQueue queue = new FinitePcmQueue(8);
        assertEquals(FinitePcmQueue.ReadState.STARVED, queue.read(8).state());
    }

    @Test
    void readsQueuedPcmWithoutBlockingAndThenReportsEof() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(8);
        queue.write(new byte[] { 1, 2, 3, 4, 5, 6 }, 0, 6);
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, queue.read(4).data());
        queue.markEof();
        assertArrayEquals(new byte[] { 5, 6 }, queue.read(8).data());
        assertEquals(FinitePcmQueue.ReadState.EOF, queue.read(8).state());
    }

    @Test
    void producerBackpressureReleasesWhenRendererConsumes() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(4);
        queue.write(new byte[] { 1, 2, 3, 4 }, 0, 4);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> blocked = executor.submit(() -> {
                try {
                    queue.write(new byte[] { 5, 6 }, 0, 2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThrows(TimeoutException.class, () -> blocked.get(100, TimeUnit.MILLISECONDS));
            assertArrayEquals(new byte[] { 1, 2 }, queue.read(2).data());
            blocked.get(2, TimeUnit.SECONDS);
            assertEquals(4, queue.queuedBytes());
            assertArrayEquals(new byte[] { 3, 4, 5, 6 }, queue.read(8).data());
        } finally {
            queue.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void cancelDiscardsStalePcmAndWakesBlockedProducer() throws Exception {
        FinitePcmQueue queue = new FinitePcmQueue(4);
        queue.write(new byte[] { 1, 2, 3, 4 }, 0, 4);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> blocked = executor.submit(() -> {
                try {
                    queue.write(new byte[] { 5, 6 }, 0, 2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThrows(TimeoutException.class, () -> blocked.get(100, TimeUnit.MILLISECONDS));
            queue.cancel();
            ExecutionException failure = assertThrows(ExecutionException.class,
                () -> blocked.get(2, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            assertTrue(cause instanceof RuntimeException && cause.getCause() instanceof IOException);
            assertEquals(0, queue.queuedBytes());
            assertEquals(FinitePcmQueue.ReadState.CANCELLED, queue.read(8).state());
        } finally {
            queue.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsOddSampleWrites() {
        FinitePcmQueue queue = new FinitePcmQueue(8);
        assertThrows(IllegalArgumentException.class,
            () -> queue.write(new byte[] { 1, 2, 3 }, 0, 3));
    }
}
