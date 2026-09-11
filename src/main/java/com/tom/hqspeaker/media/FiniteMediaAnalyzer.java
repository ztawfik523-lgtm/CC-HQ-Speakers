package com.tom.hqspeaker.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight server-side inspection of supported finite encoded media.
 *
 * <p>This reads the encoded container/frame structure only. It never decodes the complete track to PCM and keeps a
 * single 64 KiB read window regardless of file size. The supplied channel is reset to position zero before return.</p>
 */
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

            if (r.matches(0L, "OggS")) return analyzeOggVorbis(r);
            if (r.matches(0L, "RIFF") && r.size >= 12L && r.matches(8L, "WAVE")) return analyzeWav(r);
            if (r.matches(0L, "FORM") && r.size >= 12L
                    && (r.matches(8L, "AIFF") || r.matches(8L, "AIFC"))) return analyzeAiff(r);
            if (r.matches(0L, ".snd")) return analyzeAu(r);

            MediaMetadata mp3 = tryAnalyzeMp3(r);
            if (mp3 != null) return mp3;
            throw new IOException("unsupported finite media format");
        } catch (ArithmeticException e) {
            throw new IOException("media metadata exceeds supported numeric bounds", e);
        } finally {
            channel.position(0L);
        }
    }

    private static MediaMetadata analyzeOggVorbis(Reader r) throws IOException {
        OggPage first = readOggPage(r, 0L);
        if ((first.headerType & 0x02) == 0 || !isVorbisIdentification(r, first)) {
            throw new IOException("OGG file is not Vorbis audio");
        }

        long packet = first.bodyStart;
        long version = r.u32le(packet + 7L);
        if (version != 0L) throw new IOException("unsupported Vorbis version " + version);
        int channels = r.u8(packet + 11L);
        long sampleRateLong = r.u32le(packet + 12L);
        if (channels <= 0 || channels > 8 || sampleRateLong <= 0L || sampleRateLong > Integer.MAX_VALUE) {
            throw new IOException("invalid OGG Vorbis channel/rate metadata");
        }
        int blockSizes = r.u8(packet + 28L);
        int shortBlockExponent = blockSizes & 0x0F;
        int longBlockExponent = (blockSizes >>> 4) & 0x0F;
        if (shortBlockExponent < 6 || shortBlockExponent > 13
                || longBlockExponent < shortBlockExponent || longBlockExponent > 13
                || (r.u8(packet + 29L) & 0x01) == 0) {
            throw new IOException("invalid OGG Vorbis identification header");
        }

        int sampleRate = (int) sampleRateLong;
        long serial = first.serial;
        long lastGranule = -1L;
        SeekIndexBuilder seek = new SeekIndexBuilder(0.0, 0L);

        long pos = 0L;
        while (pos < r.size) {
            OggPage page = readOggPage(r, pos);
            if (page.serial == serial) {
                if (page.granule >= 0L) {
                    lastGranule = Math.max(lastGranule, page.granule);
                    seek.consider(page.granule / (double) sampleRate, page.offset);
                }
            } else if ((page.headerType & 0x02) != 0 && isVorbisIdentification(r, page)) {
                throw new IOException("chained OGG Vorbis streams are not supported");
            }
            pos = page.nextOffset;
        }

        if (lastGranule <= 0L) throw new IOException("OGG Vorbis duration is unavailable");
        double duration = lastGranule / (double) sampleRate;
        return new MediaMetadata(FiniteMediaFormat.OGG_VORBIS, duration, sampleRate, channels, 0, seek.snapshot());
    }

    private static OggPage readOggPage(Reader r, long offset) throws IOException {
        if (offset < 0L || r.size < 27L || offset > r.size - 27L || !r.matches(offset, "OggS")) {
            throw new IOException("invalid OGG page at byte " + offset);
        }
        if (r.u8(offset + 4L) != 0) throw new IOException("unsupported OGG bitstream version");

        int headerType = r.u8(offset + 5L);
        long granule = r.i64le(offset + 6L);
        long serial = r.u32le(offset + 14L);
        int segments = r.u8(offset + 26L);
        long headerSize = 27L + segments;
        if (headerSize > r.size - offset) throw new EOFException("truncated OGG segment table");

        long bodySize = 0L;
        for (int i = 0; i < segments; i++) bodySize += r.u8(offset + 27L + i);
        long bodyStart = offset + headerSize;
        if (bodySize > r.size - bodyStart) throw new EOFException("truncated OGG page body");
        long next = bodyStart + bodySize;
        if (next <= offset) throw new IOException("invalid OGG page size");
        return new OggPage(offset, headerType, granule, serial, bodyStart, segments, next);
    }

    private static boolean isVorbisIdentification(Reader r, OggPage page) throws IOException {
        if (page.segments <= 0) return false;
        int packetLength = 0;
        for (int i = 0; i < page.segments; i++) {
            int lace = r.u8(page.offset + 27L + i);
            packetLength += lace;
            if (lace < 255) break;
        }
        if (packetLength < 30 || page.nextOffset - page.bodyStart < 30L) return false;
        return r.u8(page.bodyStart) == 1 && r.matches(page.bodyStart + 1L, "vorbis");
    }

    private static MediaMetadata analyzeWav(Reader r) throws IOException {
        int formatTag = -1;
        int channels = -1;
        int sampleRate = -1;
        int blockAlign = -1;
        int bitsPerSample = 0;
        long dataBytes = -1L;
        long dataOffset = -1L;
        boolean fmtSeen = false;

        long pos = 12L;
        while (pos + 8L <= r.size) {
            String id = r.ascii(pos, 4);
            long chunkSize = r.u32le(pos + 4L);
            long data = pos + 8L;
            long next = checkedChunkEnd(data, chunkSize, true, r.size, "WAV");

            if ("fmt ".equals(id)) {
                if (fmtSeen) throw new IOException("WAV contains multiple fmt chunks");
                if (chunkSize < 16L) throw new IOException("invalid WAV fmt chunk");
                formatTag = r.u16le(data);
                channels = r.u16le(data + 2L);
                long sr = r.u32le(data + 4L);
                if (sr > Integer.MAX_VALUE) throw new IOException("WAV sample rate is too large");
                sampleRate = (int) sr;
                blockAlign = r.u16le(data + 12L);
                bitsPerSample = r.u16le(data + 14L);
                fmtSeen = true;
            } else if ("data".equals(id)) {
                if (!fmtSeen) throw new IOException("WAV data chunk precedes fmt chunk");
                dataOffset = data;
                dataBytes = chunkSize;
                break; // JavaSound decodes the first data chunk after fmt.
            }
            pos = next;
        }

        if (!fmtSeen || dataBytes <= 0L) throw new IOException("WAV is missing fmt/data chunks");
        if (channels <= 0 || channels > 8 || sampleRate <= 0 || blockAlign <= 0) {
            throw new IOException("invalid WAV channel/rate/frame metadata");
        }
        if (formatTag != 1 && formatTag != 3 && formatTag != 6 && formatTag != 7) {
            throw new IOException("unsupported WAV encoding tag " + formatTag);
        }
        if (formatTag == 3) {
            if (bitsPerSample != 32 && bitsPerSample != 64) {
                throw new IOException("unsupported floating-point WAV sample size " + bitsPerSample);
            }
        } else if (formatTag == 6 || formatTag == 7) {
            if (bitsPerSample != 8) throw new IOException("invalid companded WAV sample size");
        } else if (bitsPerSample <= 0 || bitsPerSample > 64) {
            throw new IOException("unsupported PCM WAV sample size " + bitsPerSample);
        }

        int bytesPerSample = (bitsPerSample + 7) / 8;
        int expectedBlockAlign = Math.multiplyExact(bytesPerSample, channels);
        if (blockAlign != expectedBlockAlign) {
            throw new IOException("WAV block alignment does not match the client decoder frame size");
        }
        long frames = dataBytes / blockAlign;
        if (frames <= 0L) throw new IOException("WAV contains no complete audio frames");
        double duration = frames / (double) sampleRate;
        return new MediaMetadata(FiniteMediaFormat.WAV, duration, sampleRate, channels, bitsPerSample,
            List.of(new MediaSeekPoint(0.0, dataOffset)));
    }

    private static MediaMetadata analyzeAiff(Reader r) throws IOException {
        if (r.matches(8L, "AIFC")) {
            throw new IOException("compressed AIFC is not currently supported");
        }
        if (!r.matches(8L, "AIFF")) throw new IOException("invalid AIFF form type");

        int channels = -1;
        long sampleFrames = -1L;
        int bitsPerSample = 0;
        int sampleRate = -1;
        long soundDataOffset = -1L;
        long soundDataBytes = -1L;
        boolean commSeen = false;

        long pos = 12L;
        while (pos + 8L <= r.size) {
            String id = r.ascii(pos, 4);
            long chunkSize = r.u32be(pos + 4L);
            long data = pos + 8L;
            long next = checkedChunkEnd(data, chunkSize, true, r.size, "AIFF");

            if ("COMM".equals(id)) {
                if (chunkSize < 18L) throw new IOException("invalid AIFF COMM chunk");
                channels = r.u16be(data);
                sampleFrames = r.u32be(data + 2L);
                bitsPerSample = r.u16be(data + 6L);
                double rate = r.extended80(data + 8L);
                if (!Double.isFinite(rate) || rate <= 0.0 || rate > Integer.MAX_VALUE) {
                    throw new IOException("invalid AIFF sample rate");
                }
                sampleRate = (int) Math.round(rate);
                commSeen = true;
            } else if ("SSND".equals(id)) {
                if (!commSeen) throw new IOException("AIFF SSND chunk precedes COMM chunk");
                if (chunkSize < 8L) throw new IOException("invalid AIFF SSND chunk");
                long offset = r.u32be(data);
                if (offset != 0L) {
                    throw new IOException("AIFF SSND offsets are not supported by the JavaSound client decoder");
                }
                soundDataOffset = data + 8L;
                soundDataBytes = chunkSize - 8L;
                break; // JavaSound stops at the first SSND chunk.
            }
            pos = next;
        }

        if (channels <= 0 || channels > 8 || sampleFrames <= 0L || sampleRate <= 0
                || bitsPerSample <= 0 || bitsPerSample > 32 || soundDataOffset < 0L) {
            throw new IOException("AIFF is missing valid COMM/SSND metadata");
        }
        int bytesPerSample = (bitsPerSample + 7) / 8;
        long frameBytes = Math.multiplyExact((long) bytesPerSample, channels);
        long requiredBytes = Math.multiplyExact(sampleFrames, frameBytes);
        if (requiredBytes > soundDataBytes) throw new IOException("truncated AIFF sound data");
        double duration = sampleFrames / (double) sampleRate;
        return new MediaMetadata(FiniteMediaFormat.AIFF, duration, sampleRate, channels, bitsPerSample,
            List.of(new MediaSeekPoint(0.0, soundDataOffset)));
    }

    private static MediaMetadata analyzeAu(Reader r) throws IOException {
        if (r.size < 24L) throw new IOException("truncated AU header");
        long dataOffset = r.u32be(4L);
        long declaredDataSize = r.u32be(8L);
        long encoding = r.u32be(12L);
        long sampleRateLong = r.u32be(16L);
        long channelsLong = r.u32be(20L);

        if (dataOffset < 24L || dataOffset > Integer.MAX_VALUE || dataOffset > r.size) {
            throw new IOException("invalid AU data offset");
        }
        if (sampleRateLong <= 0L || sampleRateLong > Integer.MAX_VALUE
                || channelsLong <= 0L || channelsLong > 8L) {
            throw new IOException("invalid AU channel/rate metadata");
        }
        int bytesPerSample = switch ((int) encoding) {
            case 1, 2, 27 -> 1; // mu-law, linear 8-bit, A-law
            case 3 -> 2;
            case 4 -> 3;
            case 5, 6 -> 4;
            case 7 -> 8;
            default -> throw new IOException("unsupported AU encoding " + encoding);
        };
        long available = r.size - dataOffset;
        long dataBytes = declaredDataSize == 0xFFFF_FFFFL ? available : declaredDataSize;
        if (dataBytes <= 0L || dataBytes > available) throw new IOException("invalid AU data size");

        int sampleRate = (int) sampleRateLong;
        int channels = (int) channelsLong;
        long frameBytes = Math.multiplyExact((long) bytesPerSample, channels);
        long frames = dataBytes / frameBytes;
        if (frames <= 0L) throw new IOException("AU contains no complete audio frames");
        double duration = frames / (double) sampleRate;
        return new MediaMetadata(FiniteMediaFormat.AU, duration, sampleRate, channels, bytesPerSample * 8,
            List.of(new MediaSeekPoint(0.0, dataOffset)));
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

    private static long checkedChunkEnd(long data, long size, boolean paddedEven, long fileSize, String kind)
            throws IOException {
        long rawEnd;
        try {
            rawEnd = Math.addExact(data, size);
        } catch (ArithmeticException e) {
            throw new IOException(kind + " chunk size overflows file bounds");
        }
        if (rawEnd > fileSize) throw new EOFException("truncated " + kind + " chunk");
        long next = rawEnd;
        if (paddedEven && (size & 1L) != 0L) {
            if (next == Long.MAX_VALUE) throw new IOException(kind + " chunk padding overflows");
            next++;
            if (next > fileSize) next = rawEnd; // permit a final unpadded odd chunk at physical EOF
        }
        return next;
    }

    private record OggPage(long offset, int headerType, long granule, long serial,
                           long bodyStart, int segments, long nextOffset) {}
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

        int u16le(long offset) throws IOException {
            return u8(offset) | (u8(offset + 1L) << 8);
        }

        int u16be(long offset) throws IOException {
            return (u8(offset) << 8) | u8(offset + 1L);
        }

        long u32le(long offset) throws IOException {
            return (long) u8(offset)
                | ((long) u8(offset + 1L) << 8)
                | ((long) u8(offset + 2L) << 16)
                | ((long) u8(offset + 3L) << 24);
        }

        long u32be(long offset) throws IOException {
            return ((long) u8(offset) << 24)
                | ((long) u8(offset + 1L) << 16)
                | ((long) u8(offset + 2L) << 8)
                | (long) u8(offset + 3L);
        }

        int i32be(long offset) throws IOException {
            return (u8(offset) << 24)
                | (u8(offset + 1L) << 16)
                | (u8(offset + 2L) << 8)
                | u8(offset + 3L);
        }

        long i64le(long offset) throws IOException {
            long value = 0L;
            for (int i = 0; i < 8; i++) value |= (long) u8(offset + i) << (8 * i);
            return value;
        }

        boolean matches(long offset, String ascii) throws IOException {
            byte[] bytes = ascii.getBytes(StandardCharsets.US_ASCII);
            if (offset < 0L || bytes.length > size || offset > size - bytes.length) return false;
            for (int i = 0; i < bytes.length; i++) if (u8(offset + i) != (bytes[i] & 0xFF)) return false;
            return true;
        }

        String ascii(long offset, int length) throws IOException {
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) bytes[i] = (byte) u8(offset + i);
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        double extended80(long offset) throws IOException {
            int signExp = u16be(offset);
            int exponent = signExp & 0x7FFF;
            boolean negative = (signExp & 0x8000) != 0;
            long high = u32be(offset + 2L);
            long low = u32be(offset + 6L);
            if (exponent == 0 && high == 0L && low == 0L) return 0.0;
            if (exponent == 0x7FFF) return Double.NaN;
            double mantissa = (high * 4_294_967_296.0 + low) / 9_223_372_036_854_775_808.0;
            double value = Math.scalb(mantissa, exponent - 16383);
            return negative ? -value : value;
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
                    if (++zeroReads >= MAX_ZERO_READS) {
                        throw new IOException("media source made no read progress");
                    }
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
