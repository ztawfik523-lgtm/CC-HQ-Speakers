package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.WavLayout;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveWavDecoderTest {
    @Test
    void decodesTrackLargerThanPcmQueueWithBackpressure() throws Exception {
        byte[] encoded = new byte[100_000];
        for (int i = 0; i < encoded.length; i += 2) {
            short sample = (short) (i / 2);
            encoded[i] = (byte) sample;
            encoded[i + 1] = (byte) (sample >> 8);
        }
        WavLayout layout = new WavLayout(WavLayout.Representation.S16, 48_000, 1, 2, 44L, encoded.length);
        FinitePcmQueue queue = new FinitePcmQueue(4_096);
        TrackingInputStream input = new TrackingInputStream(encoded);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> decoder = executor.submit(() -> {
                try {
                    ProgressiveWavDecoder.decode(input, queue, layout, layout.dataOffset());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            ByteArrayOutputStream heard = new ByteArrayOutputStream(encoded.length);
            while (true) {
                FinitePcmQueue.ReadResult result = queue.read(2_048);
                if (result.state() == FinitePcmQueue.ReadState.DATA) {
                    heard.writeBytes(result.data());
                } else if (result.state() == FinitePcmQueue.ReadState.EOF) {
                    break;
                } else if (result.state() == FinitePcmQueue.ReadState.CANCELLED) {
                    throw new AssertionError("queue cancelled unexpectedly");
                } else {
                    Thread.sleep(1L);
                }
            }
            decoder.get(2, TimeUnit.SECONDS);

            assertArrayEquals(encoded, heard.toByteArray());
            assertTrue(input.maxRequestedBytes <= 16 * 1024,
                "decoder should request bounded encoded chunks rather than the complete WAV data");
            assertTrue(queue.capacityBytes() < encoded.length,
                "fixture must prove a track larger than the PCM queue drains progressively");
        } finally {
            queue.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void stopsAtNormalizedDataBoundaryInsteadOfPhysicalAssetEof() throws Exception {
        WavLayout layout = new WavLayout(WavLayout.Representation.S16, 8_000, 1, 2, 44L, 4L);
        byte[] bytes = new byte[] { 1, 2, 3, 4, 99, 98, 97, 96 };
        FinitePcmQueue queue = new FinitePcmQueue(16);

        ProgressiveWavDecoder.decode(new ByteArrayInputStream(bytes), queue, layout, 44L);
        FinitePcmQueue.ReadResult data = queue.read(16);
        assertEquals(FinitePcmQueue.ReadState.DATA, data.state());
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, data.data());
        assertEquals(FinitePcmQueue.ReadState.EOF, queue.read(16).state());
    }

    @Test
    void truncatedDataFailsInsteadOfBecomingNormalEof() {
        WavLayout layout = new WavLayout(WavLayout.Representation.S16, 8_000, 1, 2, 44L, 8L);
        FinitePcmQueue queue = new FinitePcmQueue(16);
        assertThrows(EOFException.class,
            () -> ProgressiveWavDecoder.decode(new ByteArrayInputStream(new byte[4]), queue, layout, 44L));
    }

    @Test
    void rejectsUnalignedStart() {
        WavLayout layout = new WavLayout(WavLayout.Representation.S16, 8_000, 2, 4, 44L, 16L);
        IOException error = assertThrows(IOException.class,
            () -> ProgressiveWavDecoder.decode(new ByteArrayInputStream(new byte[16]),
                new FinitePcmQueue(16), layout, 46L));
        assertTrue(error.getMessage().contains("frame aligned"));
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        int maxRequestedBytes;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            maxRequestedBytes = Math.max(maxRequestedBytes, len);
            return super.read(b, off, len);
        }
    }
}
