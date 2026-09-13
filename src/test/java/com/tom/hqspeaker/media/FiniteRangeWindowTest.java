package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FiniteRangeWindowTest {
    @Test
    void freshWindowCannotDemandUntilAuthoritativeAnchorArrives() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);

        assertFalse(window.anchored());
        assertTrue(window.nextRequest(128, 1L).isEmpty());
        assertEquals(FiniteRangeWindow.Availability.CANCELLED_OR_STALE,
            window.probe(0L, 1).availability());

        window.reset(0L);
        assertTrue(window.anchored());
        assertEquals(0L, window.nextRequest(128, 2L).orElseThrow().offset());
    }

    @Test
    void requestsAndAcceptsBoundedNonZeroOffsetData() {
        FiniteRangeWindow window = new FiniteRangeWindow(2_000_000L, 256 * 1024);
        window.reset(1_000_000L);

        FiniteRangeWindow.Range request = window.nextRequest(64 * 1024, 10L).orElseThrow();
        assertEquals(1_000_000L, request.offset());
        assertEquals(64 * 1024, request.length());
        assertEquals(FiniteRangeWindow.Availability.NEED_DATA, window.probe(request.offset(), 16).availability());

        byte[] bytes = new byte[request.length()];
        Arrays.fill(bytes, (byte) 0x5A);
        assertTrue(window.accept(request.offset(), bytes));
        assertEquals(FiniteRangeWindow.Availability.DATA_AVAILABLE, window.probe(request.offset(), 32).availability());
        assertArrayEquals(Arrays.copyOf(bytes, 32), window.copy(request.offset(), 32));
        assertTrue(window.allocatedBytes() <= window.capacity());
    }

    @Test
    void reanchorDropsOldDataAndStaleResponse() {
        FiniteRangeWindow window = new FiniteRangeWindow(4_000_000L, 128 * 1024);
        window.reset(0L);
        FiniteRangeWindow.Range oldRequest = window.nextRequest(32 * 1024, 1L).orElseThrow();

        window.reset(3_000_000L);
        assertFalse(window.accept(oldRequest.offset(), new byte[oldRequest.length()]));
        assertEquals(FiniteRangeWindow.Availability.CANCELLED_OR_STALE,
            window.probe(oldRequest.offset(), 1).availability());

        FiniteRangeWindow.Range current = window.nextRequest(32 * 1024, 2L).orElseThrow();
        assertEquals(3_000_000L, current.offset());
    }

    @Test
    void advancePreservesUnreadOverlapAndRequestsOnlyNewTail() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        window.reset(100L);

        FiniteRangeWindow.Range firstRequest = window.nextRequest(256, 1L).orElseThrow();
        assertEquals(new FiniteRangeWindow.Range(100L, 256), firstRequest);
        byte[] first = new byte[256];
        for (int i = 0; i < first.length; i++) first[i] = (byte) (i * 17);
        assertTrue(window.accept(firstRequest.offset(), first));

        window.advanceTo(164L);
        assertEquals(164L, window.windowStart());
        assertEquals(420L, window.windowEnd());
        assertEquals(FiniteRangeWindow.Availability.DATA_AVAILABLE, window.probe(164L, 192).availability());
        assertArrayEquals(Arrays.copyOfRange(first, 64, 96), window.copy(164L, 32));

        FiniteRangeWindow.Range tail = window.nextRequest(256, 2L).orElseThrow();
        assertEquals(new FiniteRangeWindow.Range(356L, 64), tail);
        assertTrue(window.allocatedBytes() <= window.capacity());
    }

    @Test
    void advanceKeepsOnlyWholeStillUsefulInflightRequests() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        window.reset(0L);

        FiniteRangeWindow.Range crossing = window.nextRequest(128, 1L).orElseThrow();
        FiniteRangeWindow.Range preserved = window.nextRequest(128, 1L).orElseThrow();
        assertEquals(new FiniteRangeWindow.Range(0L, 128), crossing);
        assertEquals(new FiniteRangeWindow.Range(128L, 128), preserved);

        window.advanceTo(64L);
        assertEquals(1, window.pendingRequests());
        assertFalse(window.accept(crossing.offset(), new byte[crossing.length()]));
        assertTrue(window.accept(preserved.offset(), new byte[preserved.length()]));

        FiniteRangeWindow.Range missingPrefix = window.nextRequest(64, 2L).orElseThrow();
        assertEquals(new FiniteRangeWindow.Range(64L, 64), missingPrefix);
    }

    @Test
    void repeatedAdvanceAndRefillStaysBoundedAcrossManyWindows() {
        byte[] source = new byte[4096];
        for (int i = 0; i < source.length; i++) source[i] = (byte) (i * 31);

        FiniteRangeWindow window = new FiniteRangeWindow(source.length, 256);
        window.reset(0L);
        long now = 1L;
        fillCurrentWindow(window, source, now);

        long cursor = 0L;
        for (int step = 0; step < 40; step++) {
            assertArrayEquals(Arrays.copyOfRange(source, (int) cursor, (int) cursor + 32),
                window.copy(cursor, 32));
            cursor += 64L;
            window.advanceTo(cursor);
            assertTrue(window.allocatedBytes() <= 256);
            fillCurrentWindow(window, source, now + step + 1L);
        }

        assertEquals(2560L, window.windowStart());
        assertTrue(window.allocatedBytes() <= window.capacity());
    }

    @Test
    void missingDataIsNotPhysicalEof() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        window.reset(0L);
        assertEquals(FiniteRangeWindow.Availability.NEED_DATA, window.probe(0L, 1).availability());
        assertEquals(FiniteRangeWindow.Availability.TRUE_ASSET_EOF, window.probe(1000L, 1).availability());
    }

    @Test
    void timedOutRequestCanBeRetried() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        window.reset(0L);
        FiniteRangeWindow.Range first = window.nextRequest(128, 100L).orElseThrow();
        assertEquals(1, window.pendingRequests());
        assertEquals(0, window.expireRequests(150L, 100L));
        assertEquals(1, window.expireRequests(250L, 100L));

        FiniteRangeWindow.Range retry = window.nextRequest(128, 300L).orElseThrow();
        assertEquals(first.offset(), retry.offset());
        assertEquals(first.length(), retry.length());
    }

    @Test
    void malformedResponseDoesNotWedgeDemand() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        window.reset(0L);
        FiniteRangeWindow.Range request = window.nextRequest(128, 1L).orElseThrow();

        assertFalse(window.accept(request.offset(), new byte[request.length() - 1]));
        assertEquals(0, window.pendingRequests());

        FiniteRangeWindow.Range retry = window.nextRequest(128, 2L).orElseThrow();
        assertEquals(request, retry);
    }

    private static void fillCurrentWindow(FiniteRangeWindow window, byte[] source, long now) {
        while (true) {
            var next = window.nextRequest(128, now);
            if (next.isEmpty()) return;
            FiniteRangeWindow.Range range = next.get();
            byte[] data = Arrays.copyOfRange(source, (int) range.offset(), (int) range.offset() + range.length());
            assertTrue(window.accept(range.offset(), data));
        }
    }
}
