package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    void productionReadAndCompletionRunOffSubmittingThread() throws Exception {
        byte[] source = new byte[2048];
        try (MediaAssetStore store = new MediaAssetStore(temp.resolve("thread-store"), 1_000_000L, 2_000_000L);
             FiniteRangeReadService service = new FiniteRangeReadService(store)) {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            Thread submittingThread = Thread.currentThread();
            AtomicReference<Thread> completionThread = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(UUID.randomUUID(), asset.id(), source.length, 0L, 1024, read -> {
                    completionThread.set(Thread.currentThread());
                    done.countDown();
                }));

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNotNull(completionThread.get());
            assertNotSame(submittingThread, completionThread.get());
            assertTrue(completionThread.get().getName().startsWith("hqspeaker-range-io-"));
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
                assertEquals(FiniteRangeReadService.Submission.INVALID,
                    service.submit(player, asset.id(), (long) FiniteRangeLimits.MAX_RANGE_BYTES + 2L, 0L,
                        FiniteRangeLimits.MAX_RANGE_BYTES + 1, ignored -> {}));
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

    @Test
    void shutdownCancelsQueuedReadAndReleasesLeaseAndAccounting() throws Exception {
        byte[] source = new byte[2048];
        MediaAssetStore store = new MediaAssetStore(temp.resolve("shutdown-store"), 1_000_000L, 2_000_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        executor.submit(() -> {
            blockerStarted.countDown();
            try { blocker.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(blockerStarted.await(5, TimeUnit.SECONDS));

        FiniteRangeReadService service = new FiniteRangeReadService(store, executor, 4, 1024 * 1024L);
        AtomicBoolean completionRan = new AtomicBoolean();
        try {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            UUID player = UUID.randomUUID();

            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(player, asset.id(), source.length, 0L, 1024, read -> completionRan.set(true)));
            assertEquals(2, store.referenceCount(asset.id()));
            assertEquals(1, service.outstandingRequests(player));
            assertEquals(1024L, service.outstandingBytes(player));

            assertTrue(store.release(asset.id()));
            assertEquals(1, store.referenceCount(asset.id()));

            service.close();
            assertFalse(completionRan.get());
            assertEquals(0, service.outstandingRequests(player));
            assertEquals(0L, service.outstandingBytes(player));
            assertEquals(0, store.referenceCount(asset.id()));
        } finally {
            blocker.countDown();
            service.close();
            store.close();
        }
    }

    @Test
    void beginCloseImmediatelyRejectsNewWorkBeforeFinalDrain() throws Exception {
        byte[] source = new byte[512];
        MediaAssetStore store = new MediaAssetStore(temp.resolve("begin-close-store"), 1_000_000L, 2_000_000L);
        MediaAsset asset = store.importAsset("fixture.bin", source.length,
            Channels.newChannel(new ByteArrayInputStream(source)));
        FiniteRangeReadService service = new FiniteRangeReadService(store);
        try {
            service.beginClose();
            assertEquals(FiniteRangeReadService.Submission.CLOSED,
                service.submit(UUID.randomUUID(), asset.id(), source.length, 0L, 128, ignored -> {}));
            service.close();
        } finally {
            service.close();
            store.close();
        }
    }

    @Test
    void shutdownWaitCanBeRetriedAfterInitialTimeout() throws Exception {
        byte[] source = new byte[512];
        MediaAssetStore store = new MediaAssetStore(temp.resolve("shutdown-retry-store"), 1_000_000L, 2_000_000L);
        MediaAsset asset = store.importAsset("fixture.bin", source.length,
            Channels.newChannel(new ByteArrayInputStream(source)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            while (true) {
                try {
                    releaseWorker.await();
                    return;
                } catch (InterruptedException ignored) {
                    // Deliberately remain alive through the first shutdownNow so close() must time out and be retried.
                }
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        FiniteRangeReadService service = new FiniteRangeReadService(
            store, new MediaAssetReleaseQueue(store), executor, 4, 1024 * 1024L, 25L);
        try {
            IOException first = assertThrows(IOException.class, service::close);
            assertTrue(first.getMessage().contains("did not stop"));
            assertTrue(store.get(asset.id()).isPresent(), "store must remain open while range shutdown is incomplete");

            releaseWorker.countDown();
            service.close();
            assertTrue(store.get(asset.id()).isPresent());
        } finally {
            releaseWorker.countDown();
            service.close();
            store.close();
        }
    }

    @Test
    void failedInflightReleaseTransfersToSharedRetryOwner() throws Exception {
        byte[] source = new byte[1024];
        MediaAssetStore store = new MediaAssetStore(temp.resolve("retry-store"), 1_000_000L, 2_000_000L);
        AtomicInteger releaseAttempts = new AtomicInteger();
        MediaAssetReleaseQueue releases = new MediaAssetReleaseQueue(id -> {
            if (releaseAttempts.getAndIncrement() == 0) throw new IOException("temporary release failure");
            return store.release(id);
        });
        FiniteRangeReadService service = new FiniteRangeReadService(
            store, releases, Executors.newSingleThreadExecutor(), 4, 1024 * 1024L);
        try {
            MediaAsset asset = store.importAsset("fixture.bin", source.length,
                Channels.newChannel(new ByteArrayInputStream(source)));
            UUID player = UUID.randomUUID();
            CountDownLatch done = new CountDownLatch(1);

            assertEquals(FiniteRangeReadService.Submission.ACCEPTED,
                service.submit(player, asset.id(), source.length, 0L, 512, read -> done.countDown()));
            assertTrue(store.release(asset.id()));
            assertEquals(1, store.referenceCount(asset.id()));

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(1, releases.pendingCount());
            assertEquals(1, store.referenceCount(asset.id()));

            assertEquals(0, releases.retryPending());
            assertEquals(0, store.referenceCount(asset.id()));
            assertEquals(2, releaseAttempts.get());
        } finally {
            service.close();
            store.close();
        }
    }
}
