package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MediaAssetReleaseQueueTest {
    @Test
    void failedReleaseTransfersOwnershipToRetryQueue() {
        UUID id = UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger();
        MediaAssetReleaseQueue queue = new MediaAssetReleaseQueue(assetId -> {
            assertEquals(id, assetId);
            if (attempts.getAndIncrement() == 0) throw new IOException("temporary delete failure");
            return true;
        });

        MediaAssetReleaseQueue.Result first = queue.release(id);
        assertEquals(MediaAssetReleaseQueue.Status.DEFERRED, first.status());
        assertEquals("temporary delete failure", first.error());
        assertEquals(1, queue.pendingCount());

        assertEquals(0, queue.retryPending());
        assertEquals(2, attempts.get());
    }

    @Test
    void retryKeepsReferenceQueuedUntilReleaseActuallySucceeds() {
        UUID id = UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger();
        MediaAssetReleaseQueue queue = new MediaAssetReleaseQueue(assetId -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) throw new IOException("still busy");
            return true;
        });

        assertEquals(MediaAssetReleaseQueue.Status.DEFERRED, queue.release(id).status());
        assertEquals(1, queue.retryPending());
        assertEquals(0, queue.retryPending());
        assertEquals(3, attempts.get());
    }

    @Test
    void missingAssetIsResolvedAndNotQueued() {
        MediaAssetReleaseQueue queue = new MediaAssetReleaseQueue(assetId -> false);
        MediaAssetReleaseQueue.Result result = queue.release(UUID.randomUUID());

        assertEquals(MediaAssetReleaseQueue.Status.MISSING, result.status());
        assertFalse(result.deferred());
        assertEquals(0, queue.pendingCount());
    }
}
