package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.WavLayout;

/** Pure frame converter for M1G common WAV: source-rate mono/stereo samples -> mono S16LE PCM. */
public final class CommonWavPcmConverter {
    private CommonWavPcmConverter() {}

    public static byte[] convertFrames(WavLayout layout, byte[] encoded, int offset, int length) {
        if (layout == null) throw new NullPointerException("layout");
        if (encoded == null) throw new NullPointerException("encoded");
        if (offset < 0 || length < 0 || offset > encoded.length - length) throw new IndexOutOfBoundsException();
        if (length % layout.blockAlign() != 0) {
            throw new IllegalArgumentException("WAV input must contain complete frames");
        }

        int frames = length / layout.blockAlign();
        byte[] out = new byte[Math.multiplyExact(frames, 2)];
        int source = offset;
        int target = 0;
        int sampleBytes = layout.representation().bytesPerSample();

        for (int frame = 0; frame < frames; frame++) {
            int mono = decodeSample(layout.representation(), encoded, source);
            if (layout.channels() == 2) {
                int right = decodeSample(layout.representation(), encoded, source + sampleBytes);
                mono = (mono + right) / 2;
            }
            out[target++] = (byte) mono;
            out[target++] = (byte) (mono >> 8);
            source += layout.blockAlign();
        }
        return out;
    }

    private static int decodeSample(WavLayout.Representation representation, byte[] data, int offset) {
        return switch (representation) {
            case U8 -> ((data[offset] & 0xFF) - 128) << 8;
            case S16 -> (short) ((data[offset] & 0xFF) | (data[offset + 1] << 8));
            case S24 -> {
                int value = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16);
                if ((value & 0x0080_0000) != 0) value |= 0xFF00_0000;
                yield value >> 8;
            }
            case S32 -> {
                int value = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | (data[offset + 3] << 24);
                yield value >> 16;
            }
            case F32 -> {
                int bits = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | (data[offset + 3] << 24);
                yield floatToS16(Float.intBitsToFloat(bits));
            }
        };
    }

    private static int floatToS16(float value) {
        if (Float.isNaN(value)) return 0;
        if (value >= 1.0f) return 32767;
        if (value <= -1.0f) return -32768;
        return value >= 0.0f ? Math.round(value * 32767.0f) : Math.round(value * 32768.0f);
    }
}
