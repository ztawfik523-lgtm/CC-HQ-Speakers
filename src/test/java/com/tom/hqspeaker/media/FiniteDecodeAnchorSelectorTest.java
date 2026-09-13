package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiniteDecodeAnchorSelectorTest {
    @Test
    void mp3UsesEarlierCoarsePointForOneSecondSafetyMargin() {
        MediaMetadata metadata = new MediaMetadata(
            FiniteMediaFormat.MP3,
            20.0,
            44_100,
            2,
            0,
            List.of(
                new MediaSeekPoint(0.0, 100L),
                new MediaSeekPoint(5.0, 50_000L),
                new MediaSeekPoint(10.0, 100_000L),
                new MediaSeekPoint(15.0, 150_000L)
            )
        );

        FiniteDecodeAnchorSelector.Anchor atTen =
            FiniteDecodeAnchorSelector.select(metadata, 200_000L, 10.0);
        assertEquals(50_000L, atTen.offset());
        assertEquals(5.0, atTen.seconds(), 1e-9);

        FiniteDecodeAnchorSelector.Anchor nearStart =
            FiniteDecodeAnchorSelector.select(metadata, 200_000L, 0.5);
        assertEquals(100L, nearStart.offset());
        assertEquals(0.0, nearStart.seconds(), 1e-9);
    }

    @Test
    void mp3SelectionDoesNotAssumeSeekPointsAreSorted() {
        MediaMetadata metadata = new MediaMetadata(
            FiniteMediaFormat.MP3,
            20.0,
            44_100,
            2,
            0,
            List.of(
                new MediaSeekPoint(10.0, 100_000L),
                new MediaSeekPoint(0.0, 100L),
                new MediaSeekPoint(5.0, 50_000L)
            )
        );
        FiniteDecodeAnchorSelector.Anchor anchor =
            FiniteDecodeAnchorSelector.select(metadata, 200_000L, 11.2);
        assertEquals(100_000L, anchor.offset());
        assertEquals(10.0, anchor.seconds(), 1e-9);
    }

    @Test
    void wavMapsServerTimeToExactFrameBoundary() {
        WavLayout layout = new WavLayout(
            WavLayout.Representation.S16, 8_000, 2, 4, 44L, 80_000L);
        MediaMetadata metadata = new MediaMetadata(
            FiniteMediaFormat.WAV,
            layout.durationSeconds(),
            8_000,
            2,
            16,
            List.of(new MediaSeekPoint(0.0, 44L)),
            layout
        );

        FiniteDecodeAnchorSelector.Anchor anchor =
            FiniteDecodeAnchorSelector.select(metadata, 100_000L, 1.23456);
        long frame = (long) Math.floor(1.23456 * 8_000 + 1.0e-9);
        assertEquals(44L + frame * 4L, anchor.offset());
        assertEquals(frame / 8_000.0, anchor.seconds(), 1e-12);
    }
}
