package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernFiniteMediaAnalyzerTest {
    @TempDir Path temp;

    @Test
    void formatModelContainsOnlyCurrentFormats() {
        assertEquals(Arrays.asList(FiniteMediaFormat.MP3, FiniteMediaFormat.WAV),
            Arrays.asList(FiniteMediaFormat.values()));
    }

    @Test
    void acceptsCurrentMp3AndWav() throws Exception {
        assertEquals(FiniteMediaFormat.MP3, analyze(mp3Frames(12)).format());
        assertEquals(FiniteMediaFormat.WAV, analyze(wavPcm16Mono()).format());
    }

    @Test
    void rejectsRetiredContainerSignatures() {
        for (byte[] retired : new byte[][] {
            ascii("OggS"),
            ascii("FORM0000AIFF"),
            ascii(".snd")
        }) {
            IOException error = assertThrows(IOException.class, () -> analyze(retired));
            assertTrue(error.getMessage().contains("unsupported") || error.getMessage().contains("too small"));
        }
    }

    private MediaMetadata analyze(byte[] bytes) throws Exception {
        Path file = temp.resolve("media-" + System.nanoTime());
        Files.write(file, bytes);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return ModernFiniteMediaAnalyzer.analyze(channel);
        }
    }

    private static byte[] mp3Frames(int frames) throws IOException {
        final int header = 0xFFFB9000;
        final int frameBytes = 417;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < frames; i++) {
            out.write((header >>> 24) & 0xFF);
            out.write((header >>> 16) & 0xFF);
            out.write((header >>> 8) & 0xFF);
            out.write(header & 0xFF);
            out.write(new byte[frameBytes - 4]);
        }
        return out.toByteArray();
    }

    private static byte[] wavPcm16Mono() {
        int frames = 8_000;
        int dataBytes = frames * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF"); b.putInt(36 + dataBytes); putAscii(b, "WAVE");
        putAscii(b, "fmt "); b.putInt(16);
        b.putShort((short) 1); b.putShort((short) 1); b.putInt(8_000);
        b.putInt(16_000); b.putShort((short) 2); b.putShort((short) 16);
        putAscii(b, "data"); b.putInt(dataBytes); b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void putAscii(ByteBuffer b, String value) {
        b.put(ascii(value));
    }
}
