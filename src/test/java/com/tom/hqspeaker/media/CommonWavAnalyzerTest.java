package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonWavAnalyzerTest {
    @TempDir Path temp;

    @Test
    void normalizesClassicPcm16Stereo() throws Exception {
        MediaMetadata metadata = analyze(classicWav(1, 2, 44_100, 16, 100));
        WavLayout layout = metadata.wavLayout();
        assertNotNull(layout);
        assertEquals(FiniteMediaFormat.WAV, metadata.format());
        assertEquals(WavLayout.Representation.S16, layout.representation());
        assertEquals(44_100, layout.sampleRate());
        assertEquals(2, layout.channels());
        assertEquals(4, layout.blockAlign());
        assertEquals(44L, layout.dataOffset());
        assertEquals(400L, layout.dataLength());
        assertEquals(100.0 / 44_100.0, metadata.durationSeconds(), 1e-12);
    }

    @Test
    void normalizesUnsignedPcm8AndFloat32() throws Exception {
        assertEquals(WavLayout.Representation.U8,
            analyze(classicWav(1, 1, 8_000, 8, 32)).wavLayout().representation());
        assertEquals(WavLayout.Representation.F32,
            analyze(classicWav(3, 1, 48_000, 32, 32)).wavLayout().representation());
    }

    @Test
    void acceptsNarrowExtensiblePcm24() throws Exception {
        MediaMetadata metadata = analyze(extensibleWav(1, 2, 48_000, 24, 24, 64));
        WavLayout layout = metadata.wavLayout();
        assertEquals(WavLayout.Representation.S24, layout.representation());
        assertEquals(2, layout.channels());
        assertEquals(6, layout.blockAlign());
        assertEquals(68L, layout.dataOffset());
    }

    @Test
    void acceptsNarrowExtensibleFloat32() throws Exception {
        MediaMetadata metadata = analyze(extensibleWav(3, 1, 48_000, 32, 32, 32));
        assertEquals(WavLayout.Representation.F32, metadata.wavLayout().representation());
    }

    @Test
    void rejectsExtensibleWithDifferentValidWidth() {
        IOException error = assertThrows(IOException.class,
            () -> analyze(extensibleWav(1, 2, 48_000, 32, 24, 32)));
        assertTrue(error.getMessage().contains("valid bits"));
    }

    @Test
    void rejectsSurroundAndCompandedWav() {
        IOException surround = assertThrows(IOException.class,
            () -> analyze(classicWav(1, 3, 48_000, 16, 10)));
        assertTrue(surround.getMessage().contains("mono or stereo"));

        IOException companded = assertThrows(IOException.class,
            () -> analyze(classicWav(7, 1, 8_000, 8, 10)));
        assertTrue(companded.getMessage().contains("encoding tag"));
    }

    @Test
    void rejectsUnsupportedClassicWidths() {
        IOException pcm20 = assertThrows(IOException.class,
            () -> analyze(classicWav(1, 1, 48_000, 20, 10)));
        assertTrue(pcm20.getMessage().contains("sample size"));

        IOException float64 = assertThrows(IOException.class,
            () -> analyze(classicWav(3, 1, 48_000, 64, 10)));
        assertTrue(float64.getMessage().contains("floating-point"));
    }

    @Test
    void rejectsDataChunkWithPartialFrame() {
        byte[] wav = classicWav(1, 2, 48_000, 16, 1);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 3);
        IOException error = assertThrows(IOException.class, () -> analyze(wav));
        assertTrue(error.getMessage().contains("complete audio frames"));
    }

    @Test
    void respectsDeclaredRiffContainerBounds() {
        byte[] wav = classicWav(1, 1, 8_000, 16, 4);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 20);
        IOException error = assertThrows(IOException.class, () -> analyze(wav));
        assertTrue(error.getMessage().contains("RIFF container"));
    }

    @Test
    void rejectsDeclaredRiffContainerLargerThanFile() {
        byte[] wav = classicWav(1, 1, 8_000, 16, 4);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(4, wav.length + 100);
        IOException error = assertThrows(IOException.class, () -> analyze(wav));
        assertTrue(error.getMessage().contains("truncated RIFF"));
    }

    @Test
    void restoresChannelToZero() throws Exception {
        Path file = temp.resolve("restore.wav");
        Files.write(file, classicWav(1, 1, 8_000, 16, 4));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(7L);
            CommonWavAnalyzer.analyze(channel);
            assertEquals(0L, channel.position());
        }
    }

    private MediaMetadata analyze(byte[] bytes) throws Exception {
        Path file = temp.resolve("wav-" + System.nanoTime() + ".wav");
        Files.write(file, bytes);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return CommonWavAnalyzer.analyze(channel);
        }
    }

    private static byte[] classicWav(int tag, int channels, int sampleRate, int bits, int frames) {
        int bytesPerSample = (bits + 7) / 8;
        int blockAlign = bytesPerSample * channels;
        int dataBytes = Math.max(1, frames * blockAlign);
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF"); b.putInt(36 + dataBytes); putAscii(b, "WAVE");
        putAscii(b, "fmt "); b.putInt(16);
        b.putShort((short) tag); b.putShort((short) channels); b.putInt(sampleRate);
        b.putInt(sampleRate * blockAlign); b.putShort((short) blockAlign); b.putShort((short) bits);
        putAscii(b, "data"); b.putInt(dataBytes); b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] extensibleWav(int subformatTag, int channels, int sampleRate,
                                        int containerBits, int validBits, int frames) {
        int bytesPerSample = containerBits / 8;
        int blockAlign = bytesPerSample * channels;
        int dataBytes = frames * blockAlign;
        ByteBuffer b = ByteBuffer.allocate(68 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF"); b.putInt(60 + dataBytes); putAscii(b, "WAVE");
        putAscii(b, "fmt "); b.putInt(40);
        b.putShort((short) 0xFFFE); b.putShort((short) channels); b.putInt(sampleRate);
        b.putInt(sampleRate * blockAlign); b.putShort((short) blockAlign); b.putShort((short) containerBits);
        b.putShort((short) 22); b.putShort((short) validBits); b.putInt(channels == 1 ? 0x4 : 0x3);
        b.putInt(subformatTag);
        b.putShort((short) 0); b.putShort((short) 0x0010);
        b.put((byte) 0x80).put((byte) 0x00).put((byte) 0x00).put((byte) 0xAA);
        b.put((byte) 0x00).put((byte) 0x38).put((byte) 0x9B).put((byte) 0x71);
        putAscii(b, "data"); b.putInt(dataBytes); b.put(new byte[dataBytes]);
        return b.array();
    }

    private static void putAscii(ByteBuffer b, String value) {
        for (int i = 0; i < value.length(); i++) b.put((byte) value.charAt(i));
    }
}
