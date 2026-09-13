package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiniteDecodeDescriptorTest {
    @Test
    void mapsMp3WithoutWavLayout() {
        MediaMetadata metadata = new MediaMetadata(
            FiniteMediaFormat.MP3, 10.0, 44_100, 2, 0, List.of(new MediaSeekPoint(0.0, 123L)));
        FiniteDecodeDescriptor descriptor = FiniteDecodeDescriptor.fromMetadata(metadata);
        assertEquals(FiniteDecodeDescriptor.Kind.MP3, descriptor.kind());
        assertEquals(44_100, descriptor.sampleRate());
        assertEquals(2, descriptor.channels());
        assertNull(descriptor.wavLayout());
    }

    @Test
    void mapsCommonWavWithNormalizedLayout() {
        WavLayout layout = new WavLayout(WavLayout.Representation.S24, 48_000, 2, 6, 68L, 6_000L);
        MediaMetadata metadata = new MediaMetadata(
            FiniteMediaFormat.WAV, layout.durationSeconds(), 48_000, 2, 24,
            List.of(new MediaSeekPoint(0.0, 68L)), layout);
        FiniteDecodeDescriptor descriptor = FiniteDecodeDescriptor.fromMetadata(metadata);
        assertEquals(FiniteDecodeDescriptor.Kind.WAV, descriptor.kind());
        assertEquals(layout, descriptor.wavLayout());
    }

    @Test
    void rejectsHistoricalContainersFromModernDecoderSurface() {
        MediaMetadata ogg = new MediaMetadata(
            FiniteMediaFormat.OGG_VORBIS, 1.0, 48_000, 2, 0, List.of());
        assertThrows(IllegalArgumentException.class, () -> FiniteDecodeDescriptor.fromMetadata(ogg));
    }

    @Test
    void rejectsWavWithoutNormalizedLayout() {
        MediaMetadata wav = new MediaMetadata(
            FiniteMediaFormat.WAV, 1.0, 48_000, 2, 16, List.of(new MediaSeekPoint(0.0, 44L)));
        assertThrows(IllegalArgumentException.class, () -> FiniteDecodeDescriptor.fromMetadata(wav));
    }
}
