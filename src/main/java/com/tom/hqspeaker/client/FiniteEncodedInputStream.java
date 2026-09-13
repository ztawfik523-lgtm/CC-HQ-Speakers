package com.tom.hqspeaker.client;

import com.tom.hqspeaker.media.FiniteRangeWindow;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decoder-worker-only blocking view over one M1F encoded range window.
 *
 * <p>Temporary range starvation waits for {@link #signalDataAvailable()} and is never translated to EOF. A semantic
 * seek/replacement should cancel this object and create a fresh one for the new local decode epoch.</p>
 */
public final class FiniteEncodedInputStream extends InputStream {
    private final FiniteRangeWindow window;
    private final Object signal = new Object();
    private final int slideThresholdBytes;
    private long cursor;
    private volatile boolean cancelled;

    public FiniteEncodedInputStream(FiniteRangeWindow window, long startOffset) {
        if (window == null) throw new NullPointerException("window");
        if (!window.anchored()) throw new IllegalArgumentException("range window must be anchored");
        if (startOffset < window.windowStart() || startOffset > window.totalBytes()) {
            throw new IllegalArgumentException("startOffset outside active encoded window/asset");
        }
        this.window = window;
        this.cursor = startOffset;
        this.slideThresholdBytes = Math.max(1, window.capacity() / 4);
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return read < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (target == null) throw new NullPointerException("target");
        if (offset < 0 || length < 0 || offset > target.length - length) throw new IndexOutOfBoundsException();
        if (length == 0) return 0;

        while (true) {
            if (cancelled) throw new IOException("finite encoded input cancelled or stale");

            FiniteRangeWindow.Probe probe = window.probe(cursor, length);
            switch (probe.availability()) {
                case DATA_AVAILABLE -> {
                    int count = Math.min(length, probe.contiguousBytes());
                    byte[] available = window.copy(cursor, count);
                    System.arraycopy(available, 0, target, offset, count);
                    cursor += count;
                    slideIfUseful();
                    return count;
                }
                case TRUE_ASSET_EOF -> {
                    return -1;
                }
                case CANCELLED_OR_STALE -> throw new IOException("finite encoded input cancelled or stale");
                case NEED_DATA -> awaitData();
            }
        }
    }

    private void slideIfUseful() throws IOException {
        if (cursor >= window.totalBytes()) return;
        long consumed = cursor - window.windowStart();
        if (consumed < slideThresholdBytes) return;
        try {
            window.advanceTo(cursor);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("finite encoded window became stale", e);
        }
    }

    private void awaitData() throws IOException {
        synchronized (signal) {
            if (cancelled) throw new IOException("finite encoded input cancelled or stale");
            try {
                signal.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("finite encoded input interrupted", e);
            }
        }
    }

    /** Wake a decoder worker after new range bytes are accepted or range state otherwise changes. */
    public void signalDataAvailable() {
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    /** Cancel this decoder epoch and wake any worker currently waiting for encoded bytes. */
    public void cancel() {
        cancelled = true;
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    @Override
    public void close() {
        cancel();
    }

    public long cursor() {
        return cursor;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
