package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.FiniteRangeWindow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ProgressiveMp3DecoderTest {
    @Test
    void discardsWholeFramesBeforeTargetAndPartialTargetFrame() {
        int rate = 48_000;
        assertEquals(1_152, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 0L, rate, 1_152));
        assertEquals(1_000, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 47_000L, rate, 1_152));
        assertEquals(500, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 47_500L, rate, 1_152));
        assertEquals(0, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 48_000L, rate, 1_152));
    }

    @Test
    void downmixesStereoAndSkipsPreTargetSamples() {
        short[] interleaved = new short[] {
            10_000, 6_000,
            12_000, 4_000,
            -8_000, -4_000
        };
        byte[] pcm = ProgressiveMp3Decoder.downmix(interleaved, interleaved.length, 2, 1);
        assertArrayEquals(new int[] { 8_000, -6_000 }, decode(pcm));
    }

    @Test
    void monoPreservesDecodedSamples() {
        short[] mono = new short[] { Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE };
        assertArrayEquals(new int[] { -32768, -1, 0, 1, 32767 },
            decode(ProgressiveMp3Decoder.downmix(mono, mono.length, 1, 0)));
    }

    @Test
    void realJlayerDecodeSurvivesInitialStarvationAndMultipleWindowSlides() throws Exception {
        byte[] mp3 = fixture();
        FiniteRangeWindow window = new FiniteRangeWindow(mp3.length, 1024);
        window.reset(0L);
        FiniteEncodedInputStream input = new FiniteEncodedInputStream(window, 0L);
        FinitePcmQueue output = new FinitePcmQueue(256 * 1024);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> decoder = executor.submit(() -> {
                try {
                    ProgressiveMp3Decoder.decode(input, output, 44_100, 1, 0.0, 0.0);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            assertThrows(TimeoutException.class, () -> decoder.get(100, TimeUnit.MILLISECONDS));

            int acceptedRanges = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!decoder.isDone()) {
                var request = window.nextRequest(257, System.nanoTime());
                if (request.isPresent()) {
                    FiniteRangeWindow.Range range = request.get();
                    int from = Math.toIntExact(range.offset());
                    byte[] data = Arrays.copyOfRange(mp3, from, from + range.length());
                    assertTrue(window.accept(range.offset(), data));
                    input.signalDataAvailable();
                    acceptedRanges++;
                } else {
                    if (System.nanoTime() >= deadline) fail("real MP3 decoder did not finish");
                    Thread.sleep(1L);
                }
            }
            decoder.get(1, TimeUnit.SECONDS);

            byte[] pcm = drain(output);
            assertTrue(acceptedRanges > 8, "fixture should require several bounded range refills");
            assertTrue(window.windowStart() > 0L, "decoder should have advanced the encoded window");
            assertTrue(pcm.length > 20_000, "real fixture should produce substantial PCM");
            assertTrue(anyNonZero(pcm), "real fixture should decode to non-silent PCM");
        } finally {
            input.cancel();
            output.cancel();
            executor.shutdownNow();
        }
    }

    @Test
    void realJlayerPreRollDiscardReducesButDoesNotEraseOutput() throws Exception {
        byte[] mp3 = fixture();
        byte[] full = decodeDirect(mp3, 0.0);
        byte[] afterTarget = decodeDirect(mp3, 0.30);

        assertTrue(full.length > afterTarget.length);
        assertTrue(afterTarget.length > 0);
        assertTrue(anyNonZero(afterTarget));
    }

    private static byte[] decodeDirect(byte[] mp3, double targetTime) throws Exception {
        FinitePcmQueue output = new FinitePcmQueue(256 * 1024);
        ProgressiveMp3Decoder.decode(new ByteArrayInputStream(mp3), output, 44_100, 1, 0.0, targetTime);
        return drain(output);
    }

    private static byte[] drain(FinitePcmQueue output) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        while (true) {
            FinitePcmQueue.ReadResult result = output.read(8192);
            switch (result.state()) {
                case DATA -> pcm.writeBytes(result.data());
                case EOF -> {
                    return pcm.toByteArray();
                }
                case STARVED -> fail("decoder finished without marking PCM EOF");
                case CANCELLED -> fail("decoder output was unexpectedly cancelled");
            }
        }
    }

    private static byte[] fixture() throws IOException {
        try (InputStream input = ProgressiveMp3DecoderTest.class.getResourceAsStream(
                "/com/tom/hqspeaker/client/m1g-real-mono-44100.mp3")) {
            assertNotNull(input, "real MP3 fixture resource is missing");
            return input.readAllBytes();
        }
    }

    private static boolean anyNonZero(byte[] pcm) {
        for (byte value : pcm) if (value != 0) return true;
        return false;
    }

    private static int[] decode(byte[] pcm) {
        ByteBuffer b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[pcm.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = b.getShort();
        return out;
    }
}
