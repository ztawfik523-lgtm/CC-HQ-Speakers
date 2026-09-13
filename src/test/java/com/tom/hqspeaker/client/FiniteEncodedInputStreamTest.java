package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.FiniteRangeWindow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
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

class FiniteEncodedInputStreamTest {
    @Test
    void starvationWaitsAndLaterResumesWithoutEof() throws Exception {
        FiniteRangeWindow window = new FiniteRangeWindow(16L, 16);
        window.reset(0L);
        FiniteEncodedInputStream input = new FiniteEncodedInputStream(window, 0L);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> waiting = executor.submit(() -> input.readNBytes(4));
            assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));

            FiniteRangeWindow.Range request = window.nextRequest(4, 1L).orElseThrow();
            assertEquals(0L, request.offset());
            assertTrue(window.accept(0L, new byte[] { 1, 2, 3, 4 }));
            input.signalDataAvailable();

            assertArrayEquals(new byte[] { 1, 2, 3, 4 }, waiting.get(2, TimeUnit.SECONDS));
        } finally {
            input.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void trueAssetEofIsTheOnlyNormalMinusOne() throws Exception {
        FiniteRangeWindow window = new FiniteRangeWindow(4L, 4);
        window.reset(0L);
        FiniteRangeWindow.Range request = window.nextRequest(4, 1L).orElseThrow();
        assertEquals(4, request.length());
        assertTrue(window.accept(0L, new byte[] { 9, 8, 7, 6 }));

        FiniteEncodedInputStream input = new FiniteEncodedInputStream(window, 0L);
        assertArrayEquals(new byte[] { 9, 8, 7, 6 }, input.readNBytes(4));
        assertEquals(-1, input.read());
    }

    @Test
    void cancelWakesWaitingDecoderEpoch() throws Exception {
        FiniteRangeWindow window = new FiniteRangeWindow(32L, 16);
        window.reset(0L);
        FiniteEncodedInputStream input = new FiniteEncodedInputStream(window, 0L);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> waiting = executor.submit(() -> input.read());
            assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
            input.cancel();
            ExecutionException failure = assertThrows(ExecutionException.class,
                () -> waiting.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
        } finally {
            input.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void progressiveConsumptionSlidesTheBoundedWindow() throws Exception {
        FiniteRangeWindow window = new FiniteRangeWindow(64L, 16);
        window.reset(0L);
        FiniteRangeWindow.Range request = window.nextRequest(16, 1L).orElseThrow();
        byte[] first = new byte[request.length()];
        for (int i = 0; i < first.length; i++) first[i] = (byte) i;
        assertTrue(window.accept(request.offset(), first));

        FiniteEncodedInputStream input = new FiniteEncodedInputStream(window, 0L);
        assertArrayEquals(Arrays.copyOfRange(first, 0, 4), input.readNBytes(4));
        assertEquals(4L, input.cursor());
        assertEquals(4L, window.windowStart(), "quarter-window consumption should expose new tail demand");
        assertEquals(16, window.allocatedBytes());
    }
}
