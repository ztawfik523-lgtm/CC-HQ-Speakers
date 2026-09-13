package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FiniteRangeWindowTest {
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
        FiniteRangeWindow.Range oldRequest = window.nextRequest(32 * 1024, 1L).orElseThrow();

        window.reset(3_000_000L);
        assertFalse(window.accept(oldRequest.offset(), new byte[oldRequest.length()]));
        assertEquals(FiniteRangeWindow.Availability.CANCELLED_OR_STALE,
            window.probe(oldRequest.offset(), 1).availability());

        FiniteRangeWindow.Range current = window.nextRequest(32 * 1024, 2L).orElseThrow();
        assertEquals(3_000_000L, current.offset());
    }

    @Test
    void missingDataIsNotPhysicalEof() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        assertEquals(FiniteRangeWindow.Availability.NEED_DATA, window.probe(0L, 1).availability());
        assertEquals(FiniteRangeWindow.Availability.TRUE_ASSET_EOF, window.probe(1000L, 1).availability());
    }

    @Test
    void timedOutRequestCanBeRetried() {
        FiniteRangeWindow window = new FiniteRangeWindow(1000L, 256);
        FiniteRangeWindow.Range first = window.nextRequest(128, 100L).orElseThrow();
        assertEquals(1, window.pendingRequests());
        assertEquals(0, window.expireRequests(150L, 100L));
        assertEquals(1, window.expireRequests(250L, 100L));

        FiniteRangeWindow.Range retry = window.nextRequest(128, 300L).orElseThrow();
        assertEquals(first.offset(), retry.offset());
        assertEquals(first.length(), retry.length());
    }
}
