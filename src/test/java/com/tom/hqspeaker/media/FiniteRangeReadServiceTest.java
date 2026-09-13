package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FiniteRangeReadServiceTest {
    @TempDir Path temp;

    @Test
    void readsExactArbitraryRangeAndReleasesAccounting() throws Exception {
        byte[] source = new byte[4096];
        for (int i = 0; i < source.length; i++) source[i] = (byte) (i * 31);

        try (MediaAssetStore store = new MediaAssetStore(temp.resolve("store"), 1_000_000L, 2_000_000L);
             FiniteRangeReadService service = new FiniteRangeReadService(store)) {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            UUID player = UUID.randomUUID();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<FiniteRangeReadService.ReadResult> result = new AtomicReference<>();

            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(player, asset.id(), source.length, 777L, 1024, read -> {
                    result.set(read);
                    done.countDown();
                }));

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().success(), result.get().error());
            assertEquals(777L, result.get().offset());
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 777, 1801), result.get().data());
            assertEquals(0, service.outstandingRequests(player));
            assertEquals(0L, service.outstandingBytes(player));
        }
    }

    @Test
    void queuedReadRetainsAssetAcrossOwnerRelease() throws Exception {
        byte[] source = new byte[1024];
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blocker = new CountDownLatch(1);
        executor.submit(() -> {
            try { blocker.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        MediaAssetStore store = new MediaAssetStore(temp.resolve("retain-store"), 1_000_000L, 2_000_000L);
        FiniteRangeReadService service = new FiniteRangeReadService(store, executor, 4, 1024 * 1024L);
        try {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            UUID player = UUID.randomUUID();
            CountDownLatch done = new CountDownLatch(1);

            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(player, asset.id(), source.length, 0L, 512, read -> done.countDown()));
            assertEquals(2, store.referenceCount(asset.id()));

            assertTrue(store.release(asset.id()));
            assertEquals(1, store.referenceCount(asset.id()));

            blocker.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(0, store.referenceCount(asset.id()));
        } finally {
            blocker.countDown();
            service.close();
            store.close();
        }
    }

    @Test
    void rejectsInvalidAndOverBudgetRequestsBeforeIo() throws Exception {
        byte[] source = new byte[2048];
        try (MediaAssetStore store = new MediaAssetStore(temp.resolve("budget-store"), 1_000_000L, 2_000_000L)) {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch blocker = new CountDownLatch(1);
            executor.submit(() -> {
                try { blocker.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            FiniteRangeReadService service = new FiniteRangeReadService(store, executor, 1, 1024L);
            try {
                UUID player = UUID.randomUUID();
                assertEquals(FiniteRangeReadService.Submission.INVALID,
                    service.submit(player, asset.id(), source.length, source.length, 1, ignored -> {}));
                assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                    service.submit(player, asset.id(), source.length, 0L, 512, ignored -> {}));
                assertEquals(FiniteRangeReadService.Submission.OVER_LIMIT,
                    service.submit(player, asset.id(), source.length, 512L, 512, ignored -> {}));
            } finally {
                blocker.countDown();
                service.close();
            }
        }
    }
}
