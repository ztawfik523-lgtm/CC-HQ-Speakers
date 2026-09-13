package com.tom.hqspeaker.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bounded off-thread reader for M1F encoded media ranges.
 *
 * <p>Every accepted read takes its own asset reference before it enters the executor. That reference remains held
 * until the read finishes or a queued task is cancelled, so playback stop/final release cannot delete the file under
 * an in-flight read.</p>
 */
public final class FiniteRangeReadService implements AutoCloseable {
    public enum Submission { ACCEPTED, INVALID, OVER_LIMIT, UNKNOWN_ASSET, BUSY, CLOSED }

    public record ReadResult(long offset, byte[] data, String error) {
        public boolean success() { return error == null || error.isBlank(); }
    }

    private static final class Account {
        int requests;
        long bytes;
    }

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    private final MediaAssetStore store;
    private final ExecutorService executor;
    private final int maxRequestsPerPlayer;
    private final long maxBytesPerPlayer;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private boolean closed;

    public FiniteRangeReadService(MediaAssetStore store) {
        this(store, productionExecutor(),
            FiniteRangeLimits.MAX_OUTSTANDING_REQUESTS_PER_PLAYER,
            FiniteRangeLimits.MAX_OUTSTANDING_BYTES_PER_PLAYER);
    }

    FiniteRangeReadService(MediaAssetStore store, ExecutorService executor,
                           int maxRequestsPerPlayer, long maxBytesPerPlayer) {
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maxRequestsPerPlayer <= 0) throw new IllegalArgumentException("maxRequestsPerPlayer must be positive");
        if (maxBytesPerPlayer <= 0L) throw new IllegalArgumentException("maxBytesPerPlayer must be positive");
        this.maxRequestsPerPlayer = maxRequestsPerPlayer;
        this.maxBytesPerPlayer = maxBytesPerPlayer;
    }

    public Submission submit(UUID playerId, UUID assetId, long totalBytes, long offset, int length,
                             Consumer<ReadResult> completion) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(completion, "completion");

        if (totalBytes <= 0L || offset < 0L || offset >= totalBytes || length <= 0
                || length > FiniteRangeLimits.MAX_RANGE_BYTES || length > totalBytes - offset) {
            return Submission.INVALID;
        }
        if (!acquire(playerId, length)) return closedSnapshot() ? Submission.CLOSED : Submission.OVER_LIMIT;

        boolean retained;
        try {
            retained = store.retain(assetId);
        } catch (IllegalStateException e) {
            releaseAccount(playerId, length);
            return Submission.CLOSED;
        }
        if (!retained) {
            releaseAccount(playerId, length);
            return Submission.UNKNOWN_ASSET;
        }

        RangeTask task = new RangeTask(playerId, assetId, offset, length, completion);
        try {
            executor.execute(task);
            return Submission.ACCEPTED;
        } catch (RejectedExecutionException e) {
            task.cancelQueued();
            return closedSnapshot() ? Submission.CLOSED : Submission.BUSY;
        }
    }

    public synchronized int outstandingRequests(UUID playerId) {
        Account account = accounts.get(playerId);
        return account == null ? 0 : account.requests;
    }

    public synchronized long outstandingBytes(UUID playerId) {
        Account account = accounts.get(playerId);
        return account == null ? 0L : account.bytes;
    }

    private synchronized boolean acquire(UUID playerId, int length) {
        if (closed) return false;
        Account account = accounts.computeIfAbsent(playerId, ignored -> new Account());
        if (account.requests >= maxRequestsPerPlayer || account.bytes > maxBytesPerPlayer - length) {
            if (account.requests == 0 && account.bytes == 0L) accounts.remove(playerId);
            return false;
        }
        account.requests++;
        account.bytes += length;
        return true;
    }

    private synchronized void releaseAccount(UUID playerId, int length) {
        Account account = accounts.get(playerId);
        if (account == null) return;
        account.requests = Math.max(0, account.requests - 1);
        account.bytes = Math.max(0L, account.bytes - length);
        if (account.requests == 0 && account.bytes == 0L) accounts.remove(playerId);
    }

    private synchronized boolean closedSnapshot() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        List<Runnable> queued;
        synchronized (this) {
            if (closed) return;
            closed = true;
        }

        queued = new ArrayList<>(executor.shutdownNow());
        for (Runnable runnable : queued) {
            if (runnable instanceof RangeTask task) task.cancelQueued();
        }

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IOException("finite range IO workers did not stop before media-store shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while stopping finite range IO workers", e);
        }

        synchronized (this) {
            if (!accounts.isEmpty()) {
                throw new IOException("finite range IO stopped with outstanding player accounting");
            }
        }
    }

    private final class RangeTask implements Runnable {
        private final UUID playerId;
        private final UUID assetId;
        private final long offset;
        private final int length;
        private final Consumer<ReadResult> completion;
        private final AtomicBoolean claimed = new AtomicBoolean();

        RangeTask(UUID playerId, UUID assetId, long offset, int length, Consumer<ReadResult> completion) {
            this.playerId = playerId;
            this.assetId = assetId;
            this.offset = offset;
            this.length = length;
            this.completion = completion;
        }

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) return;
            byte[] data = null;
            String error = "";
            try (SeekableByteChannel channel = store.openRead(assetId)) {
                channel.position(offset);
                data = readExact(channel, length);
            } catch (Exception e) {
                error = safeMessage(e);
            }

            String releaseError = releaseLease();
            if (!releaseError.isBlank() && error.isBlank()) error = releaseError;
            try {
                completion.accept(new ReadResult(offset, data == null ? new byte[0] : data, error));
            } catch (RuntimeException ignored) {
            }
        }

        void cancelQueued() {
            if (!claimed.compareAndSet(false, true)) return;
            releaseLease();
        }

        private String releaseLease() {
            String error = "";
            try {
                store.release(assetId);
            } catch (Exception e) {
                error = "could not release in-flight media asset: " + safeMessage(e);
            } finally {
                releaseAccount(playerId, length);
            }
            return error;
        }
    }

    private static byte[] readExact(SeekableByteChannel channel, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        int zeroReads = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) throw new EOFException("media asset ended before requested range completed");
            if (read == 0) {
                if (++zeroReads > 32) throw new IOException("media asset range read made no progress");
                Thread.onSpinWait();
                continue;
            }
            zeroReads = 0;
        }
        return buffer.array();
    }

    private static ExecutorService productionExecutor() {
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "hqspeaker-range-io-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
            FiniteRangeLimits.SERVER_IO_THREADS,
            FiniteRangeLimits.SERVER_IO_THREADS,
            30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(FiniteRangeLimits.SERVER_IO_QUEUE),
            threads,
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
