package com.tom.hqspeaker.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

/** Server-side acceptance and metadata inspection for finite MP3 and common WAV media. */
public final class FiniteMediaAnalyzer {
    private static final int WINDOW_BYTES = 64 * 1024;
    private static final long MP3_SYNC_SEARCH_BYTES = 1024L * 1024L;
    private static final double INITIAL_SEEK_INTERVAL_SECONDS = 5.0;
    static final int MAX_SEEK_POINTS = 4096;

    private FiniteMediaAnalyzer() {}

    public static MediaMetadata analyze(SeekableByteChannel channel) throws IOException {
        if (channel == null) throw new NullPointerException("channel");
        try {
            channel.position(0L);
            Reader r = new Reader(channel);
            if (r.size < 4L) throw new IOException("media file is too small");
            if (r.size >= 12L && r.matches(0L, "RIFF") && r.matches(8L, "WAVE")) {
                return CommonWavAnalyzer.analyze(channel);
            }

            MediaMetadata mp3 = tryAnalyzeMp3(r);
            if (mp3 != null) return mp3;
            throw new IOException("unsupported finite media format");
        } catch (ArithmeticException e) {
            throw new IOException("media metadata exceeds supported numeric bounds", e);
        } finally {
            channel.position(0L);
        }
    }

    private static MediaMetadata tryAnalyzeMp3(Reader r) throws IOException {
        long start = id3v2End(r);
        long firstFrame = findMp3Frame(r, start);
        if (firstFrame < 0L) return null;

        Mp3Header first = parseMp3Header(r, firstFrame);
        if (first == null) return null;
        int sampleRate = first.sampleRate;
        int channels = first.channels;
        long samples = 0L;
        long frames = 0L;
        long offset = firstFrame;
        SeekIndexBuilder seek = new SeekIndexBuilder(0.0, firstFrame);

        while (offset + 4L <= r.size) {
            Mp3Header header = parseMp3Header(r, offset);
            if (header == null || header.frameBytes > r.size - offset) break;
            if (header.sampleRate != sampleRate || header.channels != channels) {
                throw new IOException("MP3 changes sample rate or channel layout mid-stream");
            }
            if (frames > 0L) seek.consider(samples / (double) sampleRate, offset);
            samples = Math.addExact(samples, header.samplesPerFrame);
            frames++;
            offset += header.frameBytes;
        }

        if (frames == 0L || samples <= 0L) return null;
        double duration = samples / (double) sampleRate;
        return new MediaMetadata(FiniteMediaFormat.MP3, duration, sampleRate, channels, 0, seek.snapshot());
    }

    private static long id3v2End(Reader r) throws IOException {
        if (r.size < 10L || !r.matches(0L, "ID3")) return 0L;
        int b6 = r.u8(6L), b7 = r.u8(7L), b8 = r.u8(8L), b9 = r.u8(9L);
        if ((b6 | b7 | b8 | b9) >= 128) throw new IOException("invalid ID3v2 synchsafe size");
        long tagSize = ((long) b6 << 21) | ((long) b7 << 14) | ((long) b8 << 7) | b9;
        long end = 10L + tagSize;
        if ((r.u8(5L) & 0x10) != 0) end += 10L;
        if (end > r.size) throw new IOException("truncated ID3v2 tag");
        return end;
    }

    private static long findMp3Frame(Reader r, long start) throws IOException {
        long limit = Math.min(r.size - 4L, start + MP3_SYNC_SEARCH_BYTES);
        for (long pos = Math.max(0L, start); pos <= limit; pos++) {
            Mp3Header header = parseMp3Header(r, pos);
            if (header == null || header.frameBytes > r.size - pos) continue;
            long next = pos + header.frameBytes;
            if (next + 4L <= r.size) {
                Mp3Header nextHeader = parseMp3Header(r, next);
                if (nextHeader == null || nextHeader.sampleRate != header.sampleRate
                        || nextHeader.channels != header.channels) continue;
            }
            return pos;
        }
        return -1L;
    }

    private static Mp3Header parseMp3Header(Reader r, long offset) throws IOException {
        if (offset < 0L || offset + 4L > r.size) return null;
        int h = r.i32be(offset);
        if ((h & 0xFFE0_0000) != 0xFFE0_0000) return null;

        int versionBits = (h >>> 19) & 0x3;
        int layerBits = (h >>> 17) & 0x3;
        int bitrateIndex = (h >>> 12) & 0xF;
        int rateIndex = (h >>> 10) & 0x3;
        int padding = (h >>> 9) & 0x1;
        if (versionBits == 1 || layerBits != 1 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
            return null;
        }

        boolean mpeg1 = versionBits == 3;
        int[] rates = { 44_100, 48_000, 32_000 };
        int sampleRate = rates[rateIndex];
        if (versionBits == 2) sampleRate /= 2;
        else if (versionBits == 0) sampleRate /= 4;

        int[] bitrateMpeg1 = { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0 };
        int[] bitrateMpeg2 = { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0 };
        int bitrateKbps = (mpeg1 ? bitrateMpeg1 : bitrateMpeg2)[bitrateIndex];
        if (bitrateKbps <= 0 || sampleRate <= 0) return null;

        int frameBytes = (mpeg1 ? 144_000 : 72_000) * bitrateKbps / sampleRate + padding;
        if (frameBytes <= 4) return null;
        int samplesPerFrame = mpeg1 ? 1152 : 576;
        int channelMode = (h >>> 6) & 0x3;
        int channels = channelMode == 3 ? 1 : 2;
        return new Mp3Header(sampleRate, channels, frameBytes, samplesPerFrame);
    }

    private record Mp3Header(int sampleRate, int channels, int frameBytes, int samplesPerFrame) {}

    private static final class SeekIndexBuilder {
        private final List<MediaSeekPoint> points = new ArrayList<>();
        private double intervalSeconds = INITIAL_SEEK_INTERVAL_SECONDS;
        private double nextSeconds;

        SeekIndexBuilder(double firstSeconds, long firstOffset) {
            points.add(new MediaSeekPoint(firstSeconds, firstOffset));
            nextSeconds = firstSeconds + intervalSeconds;
        }

        void consider(double seconds, long byteOffset) {
            if (!Double.isFinite(seconds) || seconds < nextSeconds) return;
            points.add(new MediaSeekPoint(seconds, byteOffset));
            if (points.size() > MAX_SEEK_POINTS) thin();
            MediaSeekPoint last = points.get(points.size() - 1);
            nextSeconds = last.seconds() + intervalSeconds;
        }

        List<MediaSeekPoint> snapshot() {
            return List.copyOf(points);
        }

        private void thin() {
            ArrayList<MediaSeekPoint> compacted = new ArrayList<>((points.size() + 1) / 2);
            for (int i = 0; i < points.size(); i += 2) compacted.add(points.get(i));
            points.clear();
            points.addAll(compacted);
            intervalSeconds *= 2.0;
        }
    }

    private static final class Reader {
        private static final int MAX_ZERO_READS = 16;

        private final SeekableByteChannel channel;
        private final ByteBuffer window = ByteBuffer.allocate(WINDOW_BYTES);
        final long size;
        private long base = -1L;
        private int length;

        Reader(SeekableByteChannel channel) throws IOException {
            this.channel = channel;
            this.size = channel.size();
        }

        int u8(long offset) throws IOException {
            ensure(offset);
            return window.get((int) (offset - base)) & 0xFF;
        }

        int i32be(long offset) throws IOException {
            return (u8(offset) << 24)
                | (u8(offset + 1L) << 16)
                | (u8(offset + 2L) << 8)
                | u8(offset + 3L);
        }

        boolean matches(long offset, String ascii) throws IOException {
            if (offset < 0L || ascii.length() > size || offset > size - ascii.length()) return false;
            for (int i = 0; i < ascii.length(); i++) {
                if (u8(offset + i) != (ascii.charAt(i) & 0xFF)) return false;
            }
            return true;
        }

        private void ensure(long offset) throws IOException {
            if (offset < 0L || offset >= size) throw new EOFException("read past end of media file");
            if (base >= 0L && offset >= base && offset < base + length) return;

            long newBase = (offset / WINDOW_BYTES) * WINDOW_BYTES;
            channel.position(newBase);
            window.clear();
            int total = 0;
            int zeroReads = 0;
            while (window.hasRemaining()) {
                int read = channel.read(window);
                if (read < 0) break;
                if (read == 0) {
                    if (++zeroReads >= MAX_ZERO_READS) throw new IOException("media source made no read progress");
                    Thread.onSpinWait();
                    continue;
                }
                zeroReads = 0;
                total += read;
            }
            window.flip();
            base = newBase;
            length = total;
            if (offset >= base + length) throw new EOFException("read past end of media file");
        }
    }
}
