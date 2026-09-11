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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiniteMediaAnalyzerTest {
    @TempDir Path temp;

    @Test
    void analyzesPcmWavWithoutDecodingSamples() throws Exception {
        MediaMetadata metadata = analyze("tone.not-wav", wavPcm16Mono(8_000, 8_000));
        assertEquals(FiniteMediaFormat.WAV, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
        assertEquals(8_000, metadata.sampleRate());
        assertEquals(1, metadata.channels());
        assertEquals(16, metadata.bitsPerSample());
    }

    @Test
    void analyzesUncompressedAiff() throws Exception {
        MediaMetadata metadata = analyze("tone.bin", aiffPcm16Mono(8_000, 8_000));
        assertEquals(FiniteMediaFormat.AIFF, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
        assertEquals(8_000, metadata.sampleRate());
        assertEquals(1, metadata.channels());
        assertEquals(16, metadata.bitsPerSample());
    }

    @Test
    void analyzesAuPcm16() throws Exception {
        MediaMetadata metadata = analyze("tone.data", auPcm16Mono(8_000, 8_000));
        assertEquals(FiniteMediaFormat.AU, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
        assertEquals(8_000, metadata.sampleRate());
        assertEquals(1, metadata.channels());
        assertEquals(16, metadata.bitsPerSample());
    }

    @Test
    void analyzesOggVorbisFromPagesAndGranulePosition() throws Exception {
        MediaMetadata metadata = analyze("not-an-ogg.ext", oggVorbis(48_000, 2, 48_000));
        assertEquals(FiniteMediaFormat.OGG_VORBIS, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
        assertEquals(48_000, metadata.sampleRate());
        assertEquals(2, metadata.channels());
        assertFalse(metadata.seekPoints().isEmpty());
    }

    @Test
    void analyzesMp3FramesAndBuildsCoarseSeekHints() throws Exception {
        int frames = 40;
        MediaMetadata metadata = analyze("audio.bin", mp3Frames(frames, false));
        assertEquals(FiniteMediaFormat.MP3, metadata.format());
        assertEquals(frames * 1152.0 / 44_100.0, metadata.durationSeconds(), 1e-9);
        assertEquals(44_100, metadata.sampleRate());
        assertEquals(2, metadata.channels());
        assertFalse(metadata.seekPoints().isEmpty());
        assertEquals(0.0, metadata.seekPoints().get(0).seconds(), 1e-9);
    }

    @Test
    void skipsId3v2BeforeMp3Frames() throws Exception {
        MediaMetadata metadata = analyze("tagged", mp3Frames(12, true));
        assertEquals(FiniteMediaFormat.MP3, metadata.format());
        assertEquals(12 * 1152.0 / 44_100.0, metadata.durationSeconds(), 1e-9);
        assertTrue(metadata.seekPoints().get(0).byteOffset() >= 30L);
    }

    @Test
    void rejectsOggWithNonVorbisCodec() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("voice.ogg", oggOpus()));
        assertTrue(error.getMessage().contains("Vorbis"));
    }

    @Test
    void rejectsUnsupportedMp4RatherThanTrustingName() throws Exception {
        byte[] mp4 = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm' };
        IOException error = assertThrows(IOException.class,
            () -> analyze("fake.mp3", mp4));
        assertTrue(error.getMessage().contains("unsupported"));
    }

    @Test
    void returnsChannelToZeroAfterAnalysisFailure() throws Exception {
        Path file = temp.resolve("bad.bin");
        Files.write(file, new byte[] { 1, 2, 3, 4, 5 });
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(3L);
            assertThrows(IOException.class, () -> FiniteMediaAnalyzer.analyze(channel));
            assertEquals(0L, channel.position());
        }
    }

    private MediaMetadata analyze(String name, byte[] bytes) throws Exception {
        Path file = temp.resolve(name);
        Files.write(file, bytes);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return FiniteMediaAnalyzer.analyze(channel);
        }
    }

    private static byte[] wavPcm16Mono(int sampleRate, int frames) {
        int dataBytes = frames * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF");
        b.putInt(36 + dataBytes);
        putAscii(b, "WAVE");
        putAscii(b, "fmt ");
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) 1);
        b.putInt(sampleRate);
        b.putInt(sampleRate * 2);
        b.putShort((short) 2);
        b.putShort((short) 16);
        putAscii(b, "data");
        b.putInt(dataBytes);
        b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] aiffPcm16Mono(int sampleRate, int frames) {
        if (sampleRate != 8_000) throw new IllegalArgumentException("test helper currently encodes 8000 Hz only");
        int dataBytes = frames * 2;
        int commChunk = 8 + 18;
        int ssndChunk = 8 + 8 + dataBytes;
        ByteBuffer b = ByteBuffer.allocate(12 + commChunk + ssndChunk).order(ByteOrder.BIG_ENDIAN);
        putAscii(b, "FORM");
        b.putInt(4 + commChunk + ssndChunk);
        putAscii(b, "AIFF");
        putAscii(b, "COMM");
        b.putInt(18);
        b.putShort((short) 1);
        b.putInt(frames);
        b.putShort((short) 16);
        // 8000.0 in IEEE 754 80-bit extended precision: exponent 0x400B, significand 0xFA00000000000000.
        b.putShort((short) 0x400B);
        b.putInt(0xFA000000);
        b.putInt(0);
        putAscii(b, "SSND");
        b.putInt(8 + dataBytes);
        b.putInt(0);
        b.putInt(0);
        b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] auPcm16Mono(int sampleRate, int frames) {
        int dataBytes = frames * 2;
        ByteBuffer b = ByteBuffer.allocate(24 + dataBytes).order(ByteOrder.BIG_ENDIAN);
        putAscii(b, ".snd");
        b.putInt(24);
        b.putInt(dataBytes);
        b.putInt(3); // 16-bit linear PCM
        b.putInt(sampleRate);
        b.putInt(1);
        b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] oggVorbis(int sampleRate, int channels, long finalGranule) throws IOException {
        ByteBuffer identification = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        identification.put((byte) 1);
        putAscii(identification, "vorbis");
        identification.putInt(0);
        identification.put((byte) channels);
        identification.putInt(sampleRate);
        identification.putInt(0); // bitrate maximum
        identification.putInt(0); // bitrate nominal
        identification.putInt(0); // bitrate minimum
        identification.put((byte) 0xB8); // blocksize exponents, not used by analyzer
        identification.put((byte) 1); // framing

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oggPage(2, 0L, 0x12345678, 0, identification.array()));
        out.write(oggPage(4, finalGranule, 0x12345678, 1, new byte[] { 0 }));
        return out.toByteArray();
    }

    private static byte[] oggOpus() throws IOException {
        byte[] opusHead = new byte[19];
        byte[] magic = "OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, opusHead, 0, magic.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oggPage(2, 0L, 7, 0, opusHead));
        out.write(oggPage(4, 48_000L, 7, 1, new byte[] { 0 }));
        return out.toByteArray();
    }

    private static byte[] oggPage(int headerType, long granule, int serial, int sequence, byte[] body) {
        if (body.length > 255) throw new IllegalArgumentException("test page body too large");
        ByteBuffer b = ByteBuffer.allocate(28 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "OggS");
        b.put((byte) 0);
        b.put((byte) headerType);
        b.putLong(granule);
        b.putInt(serial);
        b.putInt(sequence);
        b.putInt(0); // checksum deliberately not validated by metadata parser
        b.put((byte) 1);
        b.put((byte) body.length);
        b.put(body);
        return b.array();
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

    private static void putAscii(ByteBuffer b, String value) {
        b.put(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
