package com.tom.hqspeaker.media;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded in-memory encoded byte window for one active finite source.
 *
 * <p>The window deliberately knows the full asset length without owning the full asset. Missing network data is
 * distinct from physical EOF. A fresh window stays unanchored until authoritative server state supplies the first
 * encoded offset, a seek/re-anchor discards obsolete bytes immediately, and forward advancement preserves useful
 * unread overlap while exposing new bounded demand at the tail.</p>
 */
public final class FiniteRangeWindow {
    public enum Availability { DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, CANCELLED_OR_STALE }

    public record Range(long offset, int length) {
        public Range {
            if (offset < 0L) throw new IllegalArgumentException("offset must be non-negative");
            if (length <= 0) throw new IllegalArgumentException("length must be positive");
        }
    }

    public record Probe(Availability availability, int contiguousBytes) {}
    private record Pending(int length, long requestedAtNanos) {}

    private final long totalBytes;
    private final int capacity;
    private long windowStart;
    private int windowLength;
    private byte[] bytes = new byte[0];
    private BitSet present = new BitSet();
    private BitSet requested = new BitSet();
    private final Map<Long, Pending> pending = new HashMap<>();
    private boolean anchored;
    private boolean cancelled;

    public FiniteRangeWindow(long totalBytes, int capacity) {
        if (totalBytes <= 0L) throw new IllegalArgumentException("totalBytes must be positive");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.totalBytes = totalBytes;
        this.capacity = capacity;
    }

    /**
     * Install a new authoritative encoded anchor. All previous bytes and outstanding demand become obsolete.
     */
    public synchronized void reset(long anchorOffset) {
        if (anchorOffset < 0L || anchorOffset > totalBytes) {
            throw new IllegalArgumentException("anchorOffset outside asset");
        }
        cancelled = false;
        anchored = true;
        windowStart = anchorOffset;
        windowLength = (int) Math.min((long) capacity, totalBytes - anchorOffset);
        bytes = new byte[windowLength];
        present = new BitSet(windowLength);
        requested = new BitSet(windowLength);
        pending.clear();
    }

    /**
     * Move the progressive read head forward while retaining bytes and whole in-flight requests which are still useful.
     * Seeking backwards or to an unrelated encoded area must use {@link #reset(long)} instead.
     */
    public synchronized void advanceTo(long newStart) {
        if (cancelled || !anchored) throw new IllegalStateException("range window is not active");
        if (newStart < windowStart || newStart > totalBytes) {
            throw new IllegalArgumentException("newStart must move forward within the asset");
        }
        if (newStart == windowStart) return;

        long oldEnd = windowStart + windowLength;
        if (newStart >= oldEnd) {
            reset(newStart);
            return;
        }

        int shift = (int) (newStart - windowStart);
        int overlapLength = windowLength - shift;
        int newLength = (int) Math.min((long) capacity, totalBytes - newStart);
        int preservedLength = Math.min(overlapLength, newLength);

        // Forward sliding never needs a larger array than the current anchored window. Shift overlap in place to avoid
        // allocating a new 512 KiB array every time a future decoder consumes a small prefix.
        if (newLength > bytes.length) {
            throw new IllegalStateException("forward range window unexpectedly grew");
        }
        if (preservedLength > 0) {
            System.arraycopy(bytes, shift, bytes, 0, preservedLength);
        }
        if (preservedLength < newLength) {
            Arrays.fill(bytes, preservedLength, newLength, (byte) 0);
        }

        BitSet nextPresent = present.get(shift, shift + preservedLength);
        BitSet nextRequested = new BitSet(newLength);
        Map<Long, Pending> nextPending = new HashMap<>();
        long newEnd = newStart + newLength;

        for (Map.Entry<Long, Pending> entry : pending.entrySet()) {
            long offset = entry.getKey();
            Pending value = entry.getValue();
            long end = offset + value.length();
            // Keep only complete requests whose response can still be accepted exactly. If the new read head moves
            // into an in-flight range, drop that whole request and re-request the useful suffix later.
            if (offset < newStart || end > oldEnd || end > newEnd) continue;
            nextPending.put(offset, value);
            int index = (int) (offset - newStart);
            nextRequested.set(index, index + value.length());
        }

        windowStart = newStart;
        windowLength = newLength;
        present = nextPresent;
        requested = nextRequested;
        pending.clear();
        pending.putAll(nextPending);
    }

    public synchronized void cancel() {
        cancelled = true;
        anchored = false;
        present.clear();
        requested.clear();
        pending.clear();
        bytes = new byte[0];
        windowLength = 0;
    }

    /** Mark and return the next missing bounded range, or empty when unanchored/fully present/in-flight. */
    public synchronized Optional<Range> nextRequest(int maxBytes, long nowNanos) {
        if (cancelled || !anchored || windowLength == 0) return Optional.empty();
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");

        int index = 0;
        while (index < windowLength) {
            if (!present.get(index) && !requested.get(index)) break;
            index++;
        }
        if (index >= windowLength) return Optional.empty();

        int end = index;
        int maxEnd = Math.min(windowLength, index + maxBytes);
        while (end < maxEnd && !present.get(end) && !requested.get(end)) end++;
        if (end <= index) return Optional.empty();

        long offset = windowStart + index;
        int length = end - index;
        requested.set(index, end);
        pending.put(offset, new Pending(length, nowNanos));
        return Optional.of(new Range(offset, length));
    }

    /** Accept one exact response which was requested for the current window. Stale/unsolicited data is discarded. */
    public synchronized boolean accept(long offset, byte[] data) {
        if (cancelled || !anchored || data == null || data.length == 0) return false;
        Pending expected = pending.get(offset);
        if (expected == null) return false;
        if (expected.length() != data.length
                || offset < windowStart || offset + data.length > windowStart + windowLength) {
            pending.remove(offset);
            clearRequested(offset, expected.length());
            return false;
        }

        pending.remove(offset);
        int index = (int) (offset - windowStart);
        System.arraycopy(data, 0, bytes, index, data.length);
        present.set(index, index + data.length);
        requested.clear(index, index + data.length);
        return true;
    }

    /** Allow a failed/rejected request to be attempted again later. */
    public synchronized void requestFailed(long offset) {
        Pending value = pending.remove(offset);
        if (value == null) return;
        clearRequested(offset, value.length());
    }

    /** Expire admission-dropped/lost requests so bounded demand can retry without an extra rejection packet. */
    public synchronized int expireRequests(long nowNanos, long timeoutNanos) {
        if (timeoutNanos <= 0L) throw new IllegalArgumentException("timeoutNanos must be positive");
        int expired = 0;
        Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Pending> entry = iterator.next();
            if (nowNanos - entry.getValue().requestedAtNanos() < timeoutNanos) continue;
            clearRequested(entry.getKey(), entry.getValue().length());
            iterator.remove();
            expired++;
        }
        return expired;
    }

    public synchronized Probe probe(long offset, int maxBytes) {
        if (cancelled || !anchored) return new Probe(Availability.CANCELLED_OR_STALE, 0);
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        if (offset >= totalBytes) return new Probe(Availability.TRUE_ASSET_EOF, 0);
        if (offset < windowStart || offset >= windowStart + windowLength) {
            return new Probe(Availability.CANCELLED_OR_STALE, 0);
        }

        int index = (int) (offset - windowStart);
        if (!present.get(index)) return new Probe(Availability.NEED_DATA, 0);

        int limit = Math.min(windowLength, index + maxBytes);
        int end = present.nextClearBit(index);
        if (end > limit) end = limit;
        return new Probe(Availability.DATA_AVAILABLE, end - index);
    }

    /** Copy only currently contiguous bytes. Call {@link #probe(long, int)} first. */
    public synchronized byte[] copy(long offset, int length) {
        Probe probe = probe(offset, length);
        if (probe.availability() != Availability.DATA_AVAILABLE || probe.contiguousBytes() < length) {
            throw new IllegalStateException("requested bytes are not fully available");
        }
        int index = (int) (offset - windowStart);
        return Arrays.copyOfRange(bytes, index, index + length);
    }

    private void clearRequested(long offset, int length) {
        if (offset < windowStart || offset + length > windowStart + windowLength) return;
        int index = (int) (offset - windowStart);
        requested.clear(index, index + length);
    }

    public synchronized long windowStart() { return windowStart; }
    public synchronized long windowEnd() { return windowStart + windowLength; }
    public synchronized int pendingRequests() { return pending.size(); }
    public synchronized int allocatedBytes() { return bytes.length; }
    public synchronized boolean anchored() { return anchored && !cancelled; }
    public long totalBytes() { return totalBytes; }
    public int capacity() { return capacity; }
}
