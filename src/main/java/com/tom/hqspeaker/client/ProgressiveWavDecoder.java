package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.WavLayout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Progressive common-WAV worker logic. Reads bounded frame chunks and never decodes/retains the whole track. */
public final class ProgressiveWavDecoder {
    private static final int TARGET_ENCODED_CHUNK_BYTES = 16 * 1024;

    private ProgressiveWavDecoder() {}

    public static void decode(InputStream input, FinitePcmQueue output, WavLayout layout, long encodedStartOffset)
            throws IOException {
        if (input == null) throw new NullPointerException("input");
        if (output == null) throw new NullPointerException("output");
        if (layout == null) throw new NullPointerException("layout");

        long dataEnd;
        try {
            dataEnd = Math.addExact(layout.dataOffset(), layout.dataLength());
        } catch (ArithmeticException e) {
            throw new IOException("WAV data range overflow", e);
        }
        if (encodedStartOffset < layout.dataOffset() || encodedStartOffset > dataEnd) {
            throw new IOException("WAV decoder start lies outside data chunk");
        }
        long relative = encodedStartOffset - layout.dataOffset();
        if (relative % layout.blockAlign() != 0L) {
            throw new IOException("WAV decoder start is not frame aligned");
        }

        long remaining = dataEnd - encodedStartOffset;
        int chunkBytes = Math.max(layout.blockAlign(),
            TARGET_ENCODED_CHUNK_BYTES - TARGET_ENCODED_CHUNK_BYTES % layout.blockAlign());

        while (remaining > 0L) {
            int wanted = (int) Math.min(remaining, (long) chunkBytes);
            wanted -= wanted % layout.blockAlign();
            if (wanted <= 0) wanted = layout.blockAlign();

            byte[] encoded = input.readNBytes(wanted);
            if (encoded.length != wanted) {
                throw new EOFException("WAV asset ended before normalized data chunk completed");
            }
            byte[] pcm = CommonWavPcmConverter.convertFrames(layout, encoded, 0, encoded.length);
            output.write(pcm, 0, pcm.length);
            remaining -= encoded.length;
        }
        output.markEof();
    }
}
