package com.tom.hqspeaker.media;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;

/**
 * Central retry owner for logical MediaAsset reference releases.
 *
 * <p>Once {@link #release(UUID)} returns DEFERRED, the caller has transferred responsibility for that one logical
 * reference to this queue and must not attempt to release the same reference again. The queue retries later without
 * losing quota/file bookkeeping when final file deletion temporarily fails.</p>
 */
public final class MediaAssetReleaseQueue {
    public enum Status { RELEASED, MISSING, DEFERRED }

    public record Result(Status status, String error) {
        public boolean deferred() { return status == Status.DEFERRED; }
    }

    @FunctionalInterface
    interface ReleaseOperation {
        boolean release(UUID id) throws IOException;
    }

    private final ReleaseOperation releaseOperation;
    private final ArrayDeque<UUID> pending = new ArrayDeque<>();

    public MediaAssetReleaseQueue(MediaAssetStore store) {
        this(Objects.requireNonNull(store, "store")::release);
    }

    MediaAssetReleaseQueue(ReleaseOperation releaseOperation) {
        this.releaseOperation = Objects.requireNonNull(releaseOperation, "releaseOperation");
    }

    /**
     * Release one logical reference now, or take ownership of retrying it later if the underlying release fails.
     */
    public synchronized Result release(UUID id) {
        Objects.requireNonNull(id, "id");
        try {
            return releaseOperation.release(id)
                ? new Result(Status.RELEASED, "")
                : new Result(Status.MISSING, "");
        } catch (IOException | RuntimeException e) {
            pending.addLast(id);
            return new Result(Status.DEFERRED, safeMessage(e));
        }
    }

    /** Retry each currently queued logical reference at most once during this call. */
    public synchronized int retryPending() {
        int attempts = pending.size();
        for (int i = 0; i < attempts; i++) {
            UUID id = pending.removeFirst();
            try {
                releaseOperation.release(id);
            } catch (IOException | RuntimeException e) {
                pending.addLast(id);
            }
        }
        return pending.size();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
