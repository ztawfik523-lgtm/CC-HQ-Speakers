package com.tom.hqspeaker.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.IOException;
import java.io.InputStream;

/** Progressive finite MP3 decoder using the packaged JLayer dependency and M1G bounded PCM queue. */
public final class ProgressiveMp3Decoder {
    private ProgressiveMp3Decoder() {}

    public static void decode(InputStream input, FinitePcmQueue output,
                              int expectedSampleRate, int expectedChannels,
                              double anchorTime, double targetTime) throws IOException {
        if (input == null) throw new NullPointerException("input");
        if (output == null) throw new NullPointerException("output");
        if (expectedSampleRate <= 0) throw new IllegalArgumentException("expectedSampleRate must be positive");
        if (expectedChannels < 1 || expectedChannels > 2) throw new IllegalArgumentException("expectedChannels must be mono/stereo");
        if (!Double.isFinite(anchorTime) || anchorTime < 0.0) throw new IllegalArgumentException("anchorTime must be finite and non-negative");
        if (!Double.isFinite(targetTime) || targetTime < anchorTime) throw new IllegalArgumentException("targetTime must be finite and at/after anchorTime");

        Bitstream bitstream = new Bitstream(input);
        Decoder decoder = new Decoder();
        long decodedSamplesPerChannel = 0L;

        try {
            while (true) {
                Header header;
                try {
                    header = bitstream.readFrame();
                } catch (BitstreamException e) {
                    throw new IOException("MP3 bitstream read failed", e);
                }
                if (header == null) {
                    output.markEof();
                    return;
                }

                try {
                    int sampleRate = header.frequency();
                    int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                    if (sampleRate != expectedSampleRate) {
                        throw new IOException("MP3 sample rate changed from analyzed descriptor");
                    }
                    if (channels != expectedChannels) {
                        throw new IOException("MP3 channel count changed from analyzed descriptor");
                    }

                    SampleBuffer decoded;
                    try {
                        decoded = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    } catch (DecoderException e) {
                        throw new IOException("MP3 frame decode failed", e);
                    }

                    int frameLength = decoded.getBufferLength();
                    if (frameLength < 0 || frameLength % channels != 0) {
                        throw new IOException("MP3 decoder returned misaligned PCM");
                    }
                    int samplesPerChannel = frameLength / channels;
                    int discard = discardSamples(anchorTime, targetTime, decodedSamplesPerChannel,
                        sampleRate, samplesPerChannel);
                    byte[] mono = downmix(decoded.getBuffer(), frameLength, channels, discard);
                    if (mono.length > 0) output.write(mono, 0, mono.length);
                    decodedSamplesPerChannel += samplesPerChannel;
                } finally {
                    bitstream.closeFrame();
                }
            }
        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
        }
    }

    static int discardSamples(double anchorTime, double targetTime, long decodedSamplesPerChannel,
                              int sampleRate, int samplesPerChannel) {
        double frameStart = anchorTime + decodedSamplesPerChannel / (double) sampleRate;
        double delta = targetTime - frameStart;
        if (delta <= 0.0) return 0;
        long discard = (long) Math.ceil(delta * sampleRate - 1.0e-9);
        if (discard <= 0L) return 0;
        return (int) Math.min((long) samplesPerChannel, discard);
    }

    static byte[] downmix(short[] samples, int frameLength, int channels, int discardPerChannel) {
        if (samples == null) throw new NullPointerException("samples");
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("channels must be mono/stereo");
        if (frameLength < 0 || frameLength > samples.length || frameLength % channels != 0) {
            throw new IllegalArgumentException("invalid frameLength");
        }
        int samplesPerChannel = frameLength / channels;
        if (discardPerChannel < 0 || discardPerChannel > samplesPerChannel) {
            throw new IllegalArgumentException("discard outside frame");
        }

        int audibleSamples = samplesPerChannel - discardPerChannel;
        byte[] mono = new byte[Math.multiplyExact(audibleSamples, 2)];
        int source = discardPerChannel * channels;
        int target = 0;
        for (int i = 0; i < audibleSamples; i++) {
            long sum = 0L;
            for (int channel = 0; channel < channels; channel++) sum += samples[source + channel];
            short value = (short) (sum / channels);
            mono[target++] = (byte) value;
            mono[target++] = (byte) (value >> 8);
            source += channels;
        }
        return mono;
    }
}
