package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.WavLayout;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonWavPcmConverterTest {
    @Test
    void convertsUnsigned8Mono() {
        byte[] encoded = new byte[] { 0, (byte) 128, (byte) 255 };
        assertArrayEquals(new int[] { -32768, 0, 32512 }, decode(convert(WavLayout.Representation.U8, 1, encoded)));
    }

    @Test
    void convertsSigned16Mono() {
        byte[] encoded = shorts(-32768, 0, 32767);
        assertArrayEquals(new int[] { -32768, 0, 32767 }, decode(convert(WavLayout.Representation.S16, 1, encoded)));
    }

    @Test
    void convertsSigned24MonoAtFullScale() {
        byte[] encoded = new byte[] {
            0x00, 0x00, (byte) 0x80,
            0x00, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xFF, 0x7F
        };
        assertArrayEquals(new int[] { -32768, 0, 32767 }, decode(convert(WavLayout.Representation.S24, 1, encoded)));
    }

    @Test
    void convertsSigned32MonoAtFullScale() {
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(Integer.MIN_VALUE).putInt(0).putInt(Integer.MAX_VALUE);
        assertArrayEquals(new int[] { -32768, 0, 32767 }, decode(convert(WavLayout.Representation.S32, 1, b.array())));
    }

    @Test
    void convertsFloat32WithClampAndNanSafety() {
        ByteBuffer b = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(-2.0f).putFloat(-0.5f).putFloat(0.0f).putFloat(0.5f)
            .putFloat(2.0f).putFloat(Float.NaN).putFloat(Float.POSITIVE_INFINITY).putFloat(Float.NEGATIVE_INFINITY);
        assertArrayEquals(new int[] { -32768, -16384, 0, 16384, 32767, 0, 32767, -32768 },
            decode(convert(WavLayout.Representation.F32, 1, b.array())));
    }

    @Test
    void downmixesStereoBeforeWritingMono() {
        byte[] encoded = shorts(12_000, 4_000, -10_000, 2_000);
        assertArrayEquals(new int[] { 8_000, -4_000 }, decode(convert(WavLayout.Representation.S16, 2, encoded)));
    }

    @Test
    void rejectsPartialFrames() {
        WavLayout layout = layout(WavLayout.Representation.S16, 2, 8L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> CommonWavPcmConverter.convertFrames(layout, new byte[7], 0, 7));
        assertEquals("WAV input must contain complete frames", error.getMessage());
    }

    private static byte[] convert(WavLayout.Representation representation, int channels, byte[] encoded) {
        return CommonWavPcmConverter.convertFrames(layout(representation, channels, encoded.length), encoded, 0, encoded.length);
    }

    private static WavLayout layout(WavLayout.Representation representation, int channels, long dataLength) {
        return new WavLayout(representation, 48_000, channels,
            representation.bytesPerSample() * channels, 44L, dataLength);
    }

    private static byte[] shorts(int... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) b.putShort((short) value);
        return b.array();
    }

    private static int[] decode(byte[] pcm) {
        ByteBuffer b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[pcm.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = b.getShort();
        return out;
    }
}
