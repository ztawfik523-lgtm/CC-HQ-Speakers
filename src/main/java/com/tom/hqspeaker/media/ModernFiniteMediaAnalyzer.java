package com.tom.hqspeaker.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;

/**
 * M1G prepared/local finite-media acceptance boundary.
 *
 * <p>The historical analyzer remains available for inherited/legacy paths, but modern prepared playback deliberately
 * narrows to MPEG Layer III and the common WAV subset. This wrapper is the server-side gate the prepared path should
 * use.</p>
 */
public final class ModernFiniteMediaAnalyzer {
    private ModernFiniteMediaAnalyzer() {}

    public static MediaMetadata analyze(SeekableByteChannel channel) throws IOException {
        if (channel == null) throw new NullPointerException("channel");
        try {
            if (isRiffWave(channel)) return CommonWavAnalyzer.analyze(channel);

            MediaMetadata metadata = FiniteMediaAnalyzer.analyze(channel);
            if (metadata.format() != FiniteMediaFormat.MP3) {
                throw new IOException("modern finite playback supports MP3 and common WAV only");
            }
            return metadata;
        } finally {
            channel.position(0L);
        }
    }

    private static boolean isRiffWave(SeekableByteChannel channel) throws IOException {
        if (channel.size() < 12L) return false;
        ByteBuffer header = ByteBuffer.allocate(12);
        channel.position(0L);
        int zeroReads = 0;
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read < 0) return false;
            if (read == 0) {
                if (++zeroReads > 16) throw new IOException("media channel made no read progress");
            } else {
                zeroReads = 0;
            }
        }
        byte[] bytes = header.array();
        return ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE");
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] wanted = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || offset + wanted.length > bytes.length) return false;
        for (int i = 0; i < wanted.length; i++) if (bytes[offset + i] != wanted[i]) return false;
        return true;
    }
}
