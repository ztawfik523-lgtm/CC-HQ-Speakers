package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressiveMp3DecoderTest {
    @Test
    void discardsWholeFramesBeforeTargetAndPartialTargetFrame() {
        int rate = 48_000;
        assertEquals(1_152, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 0L, rate, 1_152));
        assertEquals(1_152, ProgressiveMp3Decoder.discardSamples(9.0, 10.0, 47_000L, rate, 1_152));
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

    private static int[] decode(byte[] pcm) {
        ByteBuffer b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[pcm.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = b.getShort();
        return out;
    }
}
