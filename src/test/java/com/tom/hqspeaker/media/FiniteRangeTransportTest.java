package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Component proof for the M1F server-range -> bounded-client-window contract without Minecraft rendering. */
class FiniteRangeTransportTest {
    @TempDir Path temp;

    @Test
    void fakeConsumerStreamsSlidesAndJumpsWhileMemoryStaysBounded() throws Exception {
        byte[] source = new byte[2 * 1024 * 1024];
        for (int i = 0; i < source.length; i++) source[i] = (byte) ((i * 73) ^ (i >>> 7));

        try (MediaAssetStore store = new MediaAssetStore(temp.resolve("store"), source.length * 2L, source.length * 3L);
             FiniteRangeReadService service = new FiniteRangeReadService(store)) {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            UUID player = UUID.randomUUID();
            int capacity = 256 * 1024;
            FiniteRangeWindow window = new FiniteRangeWindow(source.length, capacity);

            long start = 1_000_000L;
            window.reset(start);
            fillWindow(service, player, asset, window);
            assertWindowPrefix(source, window, start, 4096);
            assertTrue(window.allocatedBytes() <= capacity);

            long cursor = start;
            for (int i = 0; i < 8; i++) {
                cursor += 64 * 1024L;
                window.advanceTo(cursor);
                fillWindow(service, player, asset, window);
                assertWindowPrefix(source, window, cursor, 4096);
                assertTrue(window.allocatedBytes() <= capacity);
            }
            assertTrue(cursor - start > capacity, "consumer must progress beyond one complete window");

            long obsoleteOffset = cursor;
            long distant = 128 * 1024L;
            window.reset(distant);
            assertEquals(FiniteRangeWindow.Availability.CANCELLED_OR_STALE,
                window.probe(obsoleteOffset, 1).availability());
            fillWindow(service, player, asset, window);
            assertWindowPrefix(source, window, distant, 4096);
            assertTrue(window.allocatedBytes() <= capacity);
        }
    }

    private static void fillWindow(FiniteRangeReadService service, UUID player, MediaAsset asset,
                                   FiniteRangeWindow window) throws Exception {
        while (true) {
            var next = window.nextRequest(64 * 1024, System.nanoTime());
            if (next.isEmpty()) return;
            FiniteRangeWindow.Range range = next.get();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<FiniteRangeReadService.ReadResult> result = new AtomicReference<>();
            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(player, asset.id(), asset.sizeBytes(), range.offset(), range.length(), read -> {
                    result.set(read);
                    done.countDown();
                }));
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().success(), result.get().error());
            assertTrue(window.accept(result.get().offset(), result.get().data()));
        }
    }

    private static void assertWindowPrefix(byte[] source, FiniteRangeWindow window, long offset, int length) {
        assertEquals(FiniteRangeWindow.Availability.DATA_AVAILABLE,
            window.probe(offset, length).availability());
        assertArrayEquals(Arrays.copyOfRange(source, (int) offset, (int) offset + length),
            window.copy(offset, length));
    }
}
