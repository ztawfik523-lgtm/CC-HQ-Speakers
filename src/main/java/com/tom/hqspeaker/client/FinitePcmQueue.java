package com.tom.hqspeaker.client;

import java.io.IOException;
import java.util.Arrays;

/**
 * Fixed-capacity mono signed-16 PCM queue between a decoder worker and Minecraft's renderer-facing AudioStream.
 *
 * <p>Decoder writes may wait for capacity. Renderer reads never wait: an empty live queue is STARVED, not EOF.</p>
 */
public final class FinitePcmQueue {
    public enum ReadState { DATA, STARVED, EOF, CANCELLED }

    public record ReadResult(ReadState state, byte[] data) {
        public ReadResult {
            if (state == null) throw new NullPointerException("state");
            data = data == null ? new byte[0] : data;
            if (state != ReadState.DATA && data.length != 0) {
                throw new IllegalArgumentException("non-DATA result cannot carry PCM bytes");
            }
        }
    }

    private final byte[] ring;
    private int readIndex;
    private int writeIndex;
    private int size;
    private boolean eof;
    private boolean cancelled;

    public FinitePcmQueue(int capacityBytes) {
        if (capacityBytes < 2 || (capacityBytes & 1) != 0) {
            throw new IllegalArgumentException("PCM capacity must be a positive whole number of S16 samples");
        }
        ring = new byte[capacityBytes];
    }

    /**
     * Write mono S16LE PCM, waiting only when the bounded queue is full. Intended for decoder workers only.
     */
    public void write(byte[] pcm, int offset, int length) throws IOException {
        if (pcm == null) throw new NullPointerException("pcm");
        if (offset < 0 || length < 0 || offset > pcm.length - length) throw new IndexOutOfBoundsException();
        if ((length & 1) != 0) throw new IllegalArgumentException("PCM write must contain whole S16 samples");
        int consumed = 0;
        while (consumed < length) {
            synchronized (this) {
                while (!cancelled && !eof && size == ring.length) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("PCM producer interrupted", e);
                    }
                }
                if (cancelled) throw new IOException("PCM queue cancelled");
                if (eof) throw new IOException("PCM queue already reached EOF");

                int available = ring.length - size;
                int count = Math.min(length - consumed, available);
                // Keep reads/writes sample-aligned even around ring wrap.
                count -= count & 1;
                if (count == 0) continue;

                int first = Math.min(count, ring.length - writeIndex);
                if ((first & 1) != 0) first--;
                if (first > 0) {
                    System.arraycopy(pcm, offset + consumed, ring, writeIndex, first);
                    writeIndex = (writeIndex + first) % ring.length;
                    size += first;
                    consumed += first;
                    count -= first;
                }
                if (count > 0) {
                    System.arraycopy(pcm, offset + consumed, ring, writeIndex, count);
                    writeIndex = (writeIndex + count) % ring.length;
                    size += count;
                    consumed += count;
                }
                notifyAll();
            }
        }
    }

    /** Nonblocking renderer-facing read. */
    public synchronized ReadResult read(int maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        int wanted = maxBytes - (maxBytes & 1);
        if (wanted <= 0) wanted = 2;

        if (size > 0) {
            int count = Math.min(size, wanted);
            count -= count & 1;
            byte[] out = new byte[count];
            int first = Math.min(count, ring.length - readIndex);
            System.arraycopy(ring, readIndex, out, 0, first);
            readIndex = (readIndex + first) % ring.length;
            size -= first;
            if (first < count) {
                int second = count - first;
                System.arraycopy(ring, readIndex, out, first, second);
                readIndex = (readIndex + second) % ring.length;
                size -= second;
            }
            notifyAll();
            return new ReadResult(ReadState.DATA, out);
        }
        if (cancelled) return new ReadResult(ReadState.CANCELLED, new byte[0]);
        if (eof) return new ReadResult(ReadState.EOF, new byte[0]);
        return new ReadResult(ReadState.STARVED, new byte[0]);
    }

    /** Mark normal local decoder EOF after all already-queued PCM has been consumed. */
    public synchronized void markEof() {
        if (cancelled) return;
        eof = true;
        notifyAll();
    }

    /** Cancel this decoder/renderer epoch, discard stale PCM, and wake any blocked producer. */
    public synchronized void cancel() {
        cancelled = true;
        eof = false;
        size = 0;
        readIndex = 0;
        writeIndex = 0;
        Arrays.fill(ring, (byte) 0);
        notifyAll();
    }

    public synchronized int queuedBytes() { return size; }
    public int capacityBytes() { return ring.length; }
    public synchronized boolean eofMarked() { return eof; }
    public synchronized boolean cancelled() { return cancelled; }
}
