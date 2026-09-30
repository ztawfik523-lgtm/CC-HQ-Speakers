package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiniteMediaAnalyzerTest {
    @TempDir Path temp;

    @Test
    void analyzesMp3FramesAndBuildsCoarseSeekHints() throws Exception {
        int frames = 40;
        MediaMetadata metadata = analyze(mp3Frames(frames, false));
        assertEquals(FiniteMediaFormat.MP3, metadata.format());
        assertEquals(frames * 1152.0 / 44_100.0, metadata.durationSeconds(), 1e-9);
        assertEquals(44_100, metadata.sampleRate());
        assertEquals(2, metadata.channels());
        assertFalse(metadata.seekPoints().isEmpty());
        assertEquals(0.0, metadata.seekPoints().getFirst().seconds(), 1e-9);
    }

    @Test
    void skipsId3v2BeforeMp3Frames() throws Exception {
        MediaMetadata metadata = analyze(mp3Frames(12, true));
        assertEquals(FiniteMediaFormat.MP3, metadata.format());
        assertTrue(metadata.seekPoints().getFirst().byteOffset() >= 30L);
    }

    @Test
    void rejectsNonMp3Input() {
        IOException error = assertThrows(IOException.class,
            () -> analyze(new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm' }));
        assertTrue(error.getMessage().contains("unsupported"));
    }

    @Test
    void returnsChannelToZeroAfterSuccessAndFailure() throws Exception {
        Path good = temp.resolve("good.mp3");
        Files.write(good, mp3Frames(12, false));
        try (FileChannel channel = FileChannel.open(good, StandardOpenOption.READ)) {
            channel.position(7L);
            FiniteMediaAnalyzer.analyze(channel);
            assertEquals(0L, channel.position());
        }

        Path bad = temp.resolve("bad.bin");
        Files.write(bad, new byte[] { 1, 2, 3, 4, 5 });
        try (FileChannel channel = FileChannel.open(bad, StandardOpenOption.READ)) {
            channel.position(3L);
            assertThrows(IOException.class, () -> FiniteMediaAnalyzer.analyze(channel));
            assertEquals(0L, channel.position());
        }
    }

    private MediaMetadata analyze(byte[] bytes) throws Exception {
        Path file = temp.resolve("audio-" + System.nanoTime());
        Files.write(file, bytes);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return FiniteMediaAnalyzer.analyze(channel);
        }
    }

    private static byte[] mp3Frames(int frames, boolean id3) throws IOException {
        final int header = 0xFFFB9000; // MPEG-1 Layer III, 128 kbps, 44.1 kHz, stereo
        final int frameBytes = 417;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (id3) {
            out.write(new byte[] { 'I', 'D', '3', 4, 0, 0, 0, 0, 0, 20 });
            out.write(new byte[20]);
        }
        for (int i = 0; i < frames; i++) {
            out.write((header >>> 24) & 0xFF);
            out.write((header >>> 16) & 0xFF);
            out.write((header >>> 8) & 0xFF);
            out.write(header & 0xFF);
            out.write(new byte[frameBytes - 4]);
        }
        return out.toByteArray();
    }
}
