package com.tom.hqspeaker.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * M1G server-side parser for the intentionally narrow common WAV subset used by modern finite playback.
 *
 * <p>Accepted data is uncompressed PCM/IEEE-float, mono/stereo, with either a classic WAVEFORMATEX tag or
 * WAVE_FORMAT_EXTENSIBLE carrying the standard PCM/float subformat GUID. The parser reads only chunk headers and the
 * small fmt payload; sample data is never decoded or retained here.</p>
 */
public final class CommonWavAnalyzer {
    private static final int WAVE_FORMAT_PCM = 0x0001;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 0x0003;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final byte[] PCM_GUID_TAIL = new byte[] {
        0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    private CommonWavAnalyzer() {}

    public static MediaMetadata analyze(SeekableByteChannel channel) throws IOException {
        if (channel == null) throw new NullPointerException("channel");
        try {
            long size = channel.size();
            if (size < 12L || !asciiEquals(channel, 0L, "RIFF") || !asciiEquals(channel, 8L, "WAVE")) {
                throw new IOException("not a RIFF/WAVE file");
            }

            long riffPayloadBytes = readU32Le(channel, 4L);
            long riffEnd = checkedAdd(8L, riffPayloadBytes, "RIFF size overflows file bounds");
            if (riffEnd < 12L) throw new IOException("invalid RIFF/WAVE size");
            if (riffEnd > size) throw new EOFException("truncated RIFF/WAVE container");

            Format format = null;
            long dataOffset = -1L;
            long dataBytes = -1L;
            long pos = 12L;
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

            while (pos + 8L <= riffEnd) {
                readFully(channel, pos, header);
                String id = new String(header.array(), 0, 4, StandardCharsets.US_ASCII);
                long chunkSize = Integer.toUnsignedLong(header.getInt(4));
                long data = pos + 8L;
                long rawEnd = checkedAdd(data, chunkSize, "WAV chunk size overflows file bounds");
                if (rawEnd > riffEnd) throw new EOFException("WAV chunk extends past RIFF container: " + id);

                if ("fmt ".equals(id)) {
                    if (format != null) throw new IOException("WAV contains multiple fmt chunks");
                    format = parseFormat(channel, data, chunkSize);
                } else if ("data".equals(id)) {
                    if (format == null) throw new IOException("WAV data chunk precedes fmt chunk");
                    dataOffset = data;
                    dataBytes = chunkSize;
                    break;
                }

                long next = rawEnd;
                if ((chunkSize & 1L) != 0L && next < riffEnd) next++;
                if (next <= pos || next > riffEnd) throw new IOException("invalid WAV chunk progression");
                pos = next;
            }

            if (format == null || dataOffset < 0L || dataBytes <= 0L) {
                throw new IOException("WAV is missing fmt/data chunks");
            }
            if (dataBytes % format.blockAlign != 0L) {
                throw new IOException("WAV data chunk does not contain complete audio frames");
            }
            long frames = dataBytes / format.blockAlign;
            if (frames <= 0L) throw new IOException("WAV contains no complete audio frames");

            WavLayout layout = new WavLayout(
                format.representation,
                format.sampleRate,
                format.channels,
                format.blockAlign,
                dataOffset,
                dataBytes
            );
            return new MediaMetadata(
                FiniteMediaFormat.WAV,
                layout.durationSeconds(),
                layout.sampleRate(),
                layout.channels(),
                layout.representation().bitsPerSample(),
                List.of(new MediaSeekPoint(0.0, layout.dataOffset())),
                layout
            );
        } catch (ArithmeticException e) {
            throw new IOException("WAV metadata exceeds supported numeric bounds", e);
        } finally {
            channel.position(0L);
        }
    }

    private static Format parseFormat(SeekableByteChannel channel, long offset, long chunkSize) throws IOException {
        if (chunkSize < 16L) throw new IOException("invalid WAV fmt chunk");
        int read = (int) Math.min(chunkSize, 40L);
        ByteBuffer fmt = ByteBuffer.allocate(read).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, offset, fmt);

        int tag = Short.toUnsignedInt(fmt.getShort(0));
        int channels = Short.toUnsignedInt(fmt.getShort(2));
        long rateLong = Integer.toUnsignedLong(fmt.getInt(4));
        int blockAlign = Short.toUnsignedInt(fmt.getShort(12));
        int containerBits = Short.toUnsignedInt(fmt.getShort(14));

        if (channels < 1 || channels > 2) throw new IOException("common WAV supports mono or stereo only");
        if (rateLong <= 0L || rateLong > Integer.MAX_VALUE) throw new IOException("invalid WAV sample rate");
        int sampleRate = (int) rateLong;

        int effectiveTag = tag;
        if (tag == WAVE_FORMAT_EXTENSIBLE) {
            if (chunkSize < 40L || read < 40) throw new IOException("truncated WAVE_FORMAT_EXTENSIBLE fmt chunk");
            int cbSize = Short.toUnsignedInt(fmt.getShort(16));
            int validBits = Short.toUnsignedInt(fmt.getShort(18));
            if (cbSize < 22) throw new IOException("invalid WAVE_FORMAT_EXTENSIBLE extension size");
            if (validBits != containerBits) {
                throw new IOException("WAVE_FORMAT_EXTENSIBLE valid bits must equal container bits");
            }
            effectiveTag = extensibleSubformatTag(fmt, 24);
        }

        WavLayout.Representation representation = representation(effectiveTag, containerBits);
        int expectedAlign = Math.multiplyExact(representation.bytesPerSample(), channels);
        if (blockAlign != expectedAlign) {
            throw new IOException("WAV block alignment does not match the supported sample layout");
        }
        return new Format(representation, sampleRate, channels, blockAlign);
    }

    private static int extensibleSubformatTag(ByteBuffer fmt, int offset) throws IOException {
        int tag = fmt.getInt(offset);
        for (int i = 0; i < PCM_GUID_TAIL.length; i++) {
            if (fmt.get(offset + 4 + i) != PCM_GUID_TAIL[i]) {
                throw new IOException("unsupported WAVE_FORMAT_EXTENSIBLE subformat GUID");
            }
        }
        if (tag != WAVE_FORMAT_PCM && tag != WAVE_FORMAT_IEEE_FLOAT) {
            throw new IOException("unsupported WAVE_FORMAT_EXTENSIBLE subformat " + tag);
        }
        return tag;
    }

    private static WavLayout.Representation representation(int formatTag, int bits) throws IOException {
        if (formatTag == WAVE_FORMAT_PCM) {
            return switch (bits) {
                case 8 -> WavLayout.Representation.U8;
                case 16 -> WavLayout.Representation.S16;
                case 24 -> WavLayout.Representation.S24;
                case 32 -> WavLayout.Representation.S32;
                default -> throw new IOException("unsupported common PCM WAV sample size " + bits);
            };
        }
        if (formatTag == WAVE_FORMAT_IEEE_FLOAT) {
            if (bits == 32) return WavLayout.Representation.F32;
            throw new IOException("unsupported common floating-point WAV sample size " + bits);
        }
        throw new IOException("unsupported common WAV encoding tag " + formatTag);
    }

    private static long readU32Le(SeekableByteChannel channel, long offset) throws IOException {
        ByteBuffer value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, offset, value);
        return Integer.toUnsignedLong(value.getInt(0));
    }

    private static boolean asciiEquals(SeekableByteChannel channel, long offset, String expected) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(expected.length());
        readFully(channel, offset, b);
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < bytes.length; i++) if (b.get(i) != bytes[i]) return false;
        return true;
    }

    private static void readFully(SeekableByteChannel channel, long offset, ByteBuffer buffer) throws IOException {
        buffer.clear();
        channel.position(offset);
        int zeroReads = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) throw new EOFException("truncated WAV structure");
            if (read == 0) {
                if (++zeroReads > 16) throw new IOException("WAV channel made no read progress");
            } else {
                zeroReads = 0;
            }
        }
        buffer.flip();
    }

    private static long checkedAdd(long left, long right, String message) throws IOException {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw new IOException(message, e);
        }
    }

    private record Format(WavLayout.Representation representation, int sampleRate, int channels, int blockAlign) {}
}
