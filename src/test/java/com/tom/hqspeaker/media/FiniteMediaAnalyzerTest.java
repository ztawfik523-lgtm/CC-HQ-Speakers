package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
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
    void wavDurationMatchesFirstDataChunkUsedByJavaSound() throws Exception {
        MediaMetadata metadata = analyze("two-data.wav", wavWithTwoDataChunks(8_000, 8_000, 16_000));
        assertEquals(FiniteMediaFormat.WAV, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
    }

    @Test
    void rejectsWavBlockAlignmentTheClientCannotConvert() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("bad-align.wav", wav(1, 2, 8_000, 2, 16, 16_000)));
        assertTrue(error.getMessage().contains("block alignment"));
    }

    @Test
    void rejectsUnsupportedFloatWavSampleWidth() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("float24.wav", wav(3, 1, 8_000, 3, 24, 24_000)));
        assertTrue(error.getMessage().contains("floating-point"));
    }

    @Test
    void analyzesUncompressedAiff() throws Exception {
        MediaMetadata metadata = analyze("tone.bin", aiffPcm(8_000, 8_000, 16, 0, 16_000));
        assertEquals(FiniteMediaFormat.AIFF, metadata.format());
        assertEquals(1.0, metadata.durationSeconds(), 1e-9);
        assertEquals(8_000, metadata.sampleRate());
        assertEquals(1, metadata.channels());
        assertEquals(16, metadata.bitsPerSample());
    }

    @Test
    void rejectsAiffWidthsTheClientReaderRejects() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("wide.aiff", aiffPcm(8_000, 8_000, 40, 0, 40_000)));
        assertTrue(error.getMessage().contains("valid COMM/SSND"));
    }

    @Test
    void rejectsAiffSsndOffsetJavaSoundDoesNotHonor() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("offset.aiff", aiffPcm(8_000, 8_000, 16, 4, 16_000)));
        assertTrue(error.getMessage().contains("SSND offsets"));
    }

    @Test
    void rejectsTruncatedAiffSoundData() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("short.aiff", aiffPcm(8_000, 8_000, 16, 0, 100)));
        assertTrue(error.getMessage().contains("truncated AIFF"));
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
    void seekHintsRemainBoundedForVeryLongOggTimeline() throws Exception {
        MediaMetadata metadata = analyze("long.ogg", oggVorbisWithManyPages(48_000, 5_000));
        assertTrue(metadata.seekPoints().size() <= FiniteMediaAnalyzer.MAX_SEEK_POINTS);
        assertEquals(25_000.0, metadata.durationSeconds(), 1e-9);
        MediaSeekPoint last = metadata.seekPoints().get(metadata.seekPoints().size() - 1);
        assertTrue(last.seconds() >= metadata.durationSeconds() - 20.0,
            "bounded index should still cover the end of the track");
    }

    @Test
    void rejectsOggWithNonVorbisCodec() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("voice.ogg", oggOpus()));
        assertTrue(error.getMessage().contains("Vorbis"));
    }

    @Test
    void rejectsTruncatedVorbisIdentificationPacket() throws Exception {
        IOException error = assertThrows(IOException.class,
            () -> analyze("truncated.ogg", oggTruncatedVorbisIdentification()));
        assertTrue(error.getMessage().contains("Vorbis"));
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
    void rejectsUnsupportedMp4RatherThanTrustingName() throws Exception {
        byte[] mp4 = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm' };
        IOException error = assertThrows(IOException.class,
            () -> analyze("fake.mp3", mp4));
        assertTrue(error.getMessage().contains("unsupported"));
    }

    @Test
    void acceptedJavaSoundContainersOpenThroughTheClientConversionShape() throws Exception {
        assertJavaSoundConverts("decode.wav", wavPcm16Mono(8_000, 8_000));
        assertJavaSoundConverts("decode.aiff", aiffPcm(8_000, 8_000, 16, 0, 16_000));
        assertJavaSoundConverts("decode.au", auPcm16Mono(8_000, 8_000));
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

    @Test
    void returnsChannelToZeroAfterSuccessfulAnalysis() throws Exception {
        Path file = temp.resolve("good.wav");
        Files.write(file, wavPcm16Mono(8_000, 8_000));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(7L);
            FiniteMediaAnalyzer.analyze(channel);
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

    private void assertJavaSoundConverts(String name, byte[] bytes) throws Exception {
        Path file = temp.resolve(name);
        Files.write(file, bytes);
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(file.toFile())) {
            javax.sound.sampled.AudioFormat source = encoded.getFormat();
            int channels = source.getChannels();
            int sampleRate = Math.round(source.getSampleRate());
            javax.sound.sampled.AudioFormat target = new javax.sound.sampled.AudioFormat(
                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, channels, channels * 2, sampleRate, false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(target, encoded)) {
                assertEquals(target.getFrameSize(), decoded.readNBytes(target.getFrameSize()).length);
            }
        }
    }

    private static byte[] wavPcm16Mono(int sampleRate, int frames) {
        return wav(1, 1, sampleRate, 2, 16, frames * 2);
    }

    private static byte[] wav(int formatTag, int channels, int sampleRate, int blockAlign,
                              int bitsPerSample, int dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF");
        b.putInt(36 + dataBytes);
        putAscii(b, "WAVE");
        putAscii(b, "fmt ");
        b.putInt(16);
        b.putShort((short) formatTag);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(sampleRate * blockAlign);
        b.putShort((short) blockAlign);
        b.putShort((short) bitsPerSample);
        putAscii(b, "data");
        b.putInt(dataBytes);
        b.put(new byte[dataBytes]);
        return b.array();
    }

    private static byte[] wavWithTwoDataChunks(int sampleRate, int firstFrames, int secondFrames) {
        int firstBytes = firstFrames * 2;
        int secondBytes = secondFrames * 2;
        int size = 12 + 24 + 8 + firstBytes + 8 + secondBytes;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(b, "RIFF");
        b.putInt(size - 8);
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
        b.putInt(firstBytes);
        b.put(new byte[firstBytes]);
        putAscii(b, "data");
        b.putInt(secondBytes);
        b.put(new byte[secondBytes]);
        return b.array();
    }

    private static byte[] aiffPcm(int sampleRate, int frames, int bitsPerSample,
                                  int ssndOffset, int actualDataBytes) {
        if (sampleRate != 8_000) throw new IllegalArgumentException("test helper currently encodes 8000 Hz only");
        int commChunk = 8 + 18;
        int ssndPayload = 8 + ssndOffset + actualDataBytes;
        int ssndChunk = 8 + ssndPayload;
        ByteBuffer b = ByteBuffer.allocate(12 + commChunk + ssndChunk).order(ByteOrder.BIG_ENDIAN);
        putAscii(b, "FORM");
        b.putInt(4 + commChunk + ssndChunk);
        putAscii(b, "AIFF");
        putAscii(b, "COMM");
        b.putInt(18);
        b.putShort((short) 1);
        b.putInt(frames);
        b.putShort((short) bitsPerSample);
        // 8000.0 in IEEE 754 80-bit extended precision: exponent 0x400B, significand 0xFA00000000000000.
        b.putShort((short) 0x400B);
        b.putInt(0xFA000000);
        b.putInt(0);
        putAscii(b, "SSND");
        b.putInt(ssndPayload);
        b.putInt(ssndOffset);
        b.putInt(0);
        b.put(new byte[ssndOffset]);
        b.put(new byte[actualDataBytes]);
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oggPage(2, 0L, 0x12345678, 0, vorbisIdentification(sampleRate, channels)));
        out.write(oggPage(4, finalGranule, 0x12345678, 1, new byte[] { 0 }));
        return out.toByteArray();
    }

    private static byte[] oggVorbisWithManyPages(int sampleRate, int pages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int serial = 0x24681357;
        out.write(oggPage(2, 0L, serial, 0, vorbisIdentification(sampleRate, 2)));
        for (int i = 1; i <= pages; i++) {
            int headerType = i == pages ? 4 : 0;
            long granule = (long) i * sampleRate * 5L;
            out.write(oggPage(headerType, granule, serial, i, new byte[] { 0 }));
        }
        return out.toByteArray();
    }

    private static byte[] vorbisIdentification(int sampleRate, int channels) {
        ByteBuffer identification = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        identification.put((byte) 1);
        putAscii(identification, "vorbis");
        identification.putInt(0);
        identification.put((byte) channels);
        identification.putInt(sampleRate);
        identification.putInt(0); // bitrate maximum
        identification.putInt(0); // bitrate nominal
        identification.putInt(0); // bitrate minimum
        identification.put((byte) 0xB8); // blocksize exponents 8 and 11
        identification.put((byte) 1); // framing
        return identification.array();
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

    private static byte[] oggTruncatedVorbisIdentification() throws IOException {
        byte[] body = new byte[16];
        body[0] = 1;
        byte[] magic = "vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, body, 1, magic.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oggPage(2, 0L, 8, 0, body));
        out.write(oggPage(4, 48_000L, 8, 1, new byte[] { 0 }));
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
