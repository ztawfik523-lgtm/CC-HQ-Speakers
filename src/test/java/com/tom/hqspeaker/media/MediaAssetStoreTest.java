package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaAssetStoreTest {
    @TempDir Path temp;

    @Test
    void importCreatesAtomicCompletedAssetWithInitialOwnerReference() throws Exception {
        byte[] source = bytes(32);
        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            MediaAsset asset = importBytes(store, "tone.ogg", source, source.length);

            assertEquals(source.length, asset.sizeBytes());
            assertEquals("tone.ogg", asset.sourceName());
            assertEquals(1, store.referenceCount(asset.id()));
            assertEquals(1, store.assetCount());
            assertEquals(source.length, store.committedBytes());
            assertEquals(0L, store.reservedBytes());

            assertTrue(Files.exists(temp.resolve(asset.id() + ".media")));
            assertFalse(Files.exists(temp.resolve(asset.id() + ".part")));

            ByteBuffer read = ByteBuffer.allocate(source.length);
            try (SeekableByteChannel channel = store.openRead(asset.id())) {
                while (read.hasRemaining() && channel.read(read) >= 0) {}
            }
            assertArrayEquals(source, read.array());
        }
    }

    @Test
    void retainAndReleaseDeleteOnlyAfterFinalReference() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            MediaAsset asset = importBytes(store, "shared.mp3", bytes(24), 24);
            Path media = temp.resolve(asset.id() + ".media");

            assertTrue(store.retain(asset.id()));
            assertEquals(2, store.referenceCount(asset.id()));
            assertTrue(store.release(asset.id()));
            assertEquals(1, store.referenceCount(asset.id()));
            assertTrue(Files.exists(media));

            assertTrue(store.release(asset.id()));
            assertEquals(0, store.referenceCount(asset.id()));
            assertEquals(0, store.assetCount());
            assertEquals(0L, store.committedBytes());
            assertFalse(Files.exists(media));
            assertFalse(store.release(asset.id()));
        }
    }

    @Test
    void enforcesPerAssetAndTotalQuotasWithoutLeakingReservations() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 20, 30)) {
            IOException perAsset = assertThrows(IOException.class,
                () -> importBytes(store, "too-big.ogg", bytes(21), 21));
            assertTrue(perAsset.getMessage().contains("per-asset"));
            assertEquals(0L, store.reservedBytes());

            MediaAsset first = importBytes(store, "first.ogg", bytes(20), 20);
            IOException total = assertThrows(IOException.class,
                () -> importBytes(store, "second.ogg", bytes(11), 11));
            assertTrue(total.getMessage().contains("total limit"));
            assertEquals(20L, store.committedBytes());
            assertEquals(0L, store.reservedBytes());
            assertEquals(1, store.assetCount());

            assertTrue(store.release(first.id()));
            MediaAsset second = importBytes(store, "second.ogg", bytes(11), 11);
            assertEquals(11L, store.committedBytes());
            assertTrue(store.release(second.id()));
        }
    }

    @Test
    void inProgressReservationPreventsConcurrentQuotaOvercommit() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 20, 20)) {
            BlockingChannel blocked = new BlockingChannel(bytes(15));
            var executor = Executors.newSingleThreadExecutor();
            try {
                var firstFuture = executor.submit(() -> store.importAsset("first.ogg", 15, blocked));
                assertTrue(blocked.awaitRead(2, TimeUnit.SECONDS));
                assertEquals(15L, store.reservedBytes());
                assertEquals(0L, store.committedBytes());

                IOException total = assertThrows(IOException.class,
                    () -> importBytes(store, "second.ogg", bytes(6), 6));
                assertTrue(total.getMessage().contains("total limit"));
                assertEquals(15L, store.reservedBytes());

                blocked.allowRead();
                MediaAsset first = firstFuture.get(2, TimeUnit.SECONDS);
                assertEquals(0L, store.reservedBytes());
                assertEquals(15L, store.committedBytes());
                assertTrue(store.release(first.id()));
            } finally {
                blocked.allowRead();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void shortSourceDeletesPartialAndReleasesReservation() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            IOException failure = assertThrows(IOException.class,
                () -> importBytes(store, "short.wav", bytes(9), 10));
            assertTrue(failure.getMessage().contains("ended before declared size"));
            assertEquals(0, store.assetCount());
            assertEquals(0L, store.committedBytes());
            assertEquals(0L, store.reservedBytes());
            assertEquals(0L, managedFileCount(temp));
        }
    }

    @Test
    void longSourceDeletesPartialAndReleasesReservation() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            IOException failure = assertThrows(IOException.class,
                () -> importBytes(store, "long.wav", bytes(11), 10));
            assertTrue(failure.getMessage().contains("larger than declared size"));
            assertEquals(0, store.assetCount());
            assertEquals(0L, store.committedBytes());
            assertEquals(0L, store.reservedBytes());
            assertEquals(0L, managedFileCount(temp));
        }
    }

    @Test
    void startupPrunesManagedCrashOrphansButLeavesUnrelatedFiles() throws Exception {
        UUID partialId = UUID.randomUUID();
        UUID completeId = UUID.randomUUID();
        Files.write(temp.resolve(partialId + ".part"), bytes(4));
        Files.write(temp.resolve(completeId + ".media"), bytes(5));
        Files.writeString(temp.resolve("keep.txt"), "unrelated");
        Files.writeString(temp.resolve("not-a-uuid.media"), "unrelated");

        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            assertEquals(0, store.assetCount());
            assertFalse(Files.exists(temp.resolve(partialId + ".part")));
            assertFalse(Files.exists(temp.resolve(completeId + ".media")));
            assertTrue(Files.exists(temp.resolve("keep.txt")));
            assertTrue(Files.exists(temp.resolve("not-a-uuid.media")));
        }
    }

    @Test
    void secondLiveStoreCannotPruneFirstStoreDirectory() throws Exception {
        try (MediaAssetStore first = new MediaAssetStore(temp, 1024, 4096)) {
            MediaAsset asset = importBytes(first, "owned.ogg", bytes(16), 16);
            Path media = temp.resolve(asset.id() + ".media");
            assertTrue(Files.exists(media));

            IOException failure = assertThrows(IOException.class,
                () -> new MediaAssetStore(temp, 1024, 4096));
            assertTrue(failure.getMessage().contains("already in use"));
            assertTrue(Files.exists(media), "second store must not prune the live store's asset");
        }
    }

    @Test
    void closeDuringImportLeavesNoPublishedOrPartialAsset() throws Exception {
        MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096);
        BlockingChannel blocked = new BlockingChannel(bytes(16));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> store.importAsset("shutdown.ogg", 16, blocked));
            assertTrue(blocked.awaitRead(2, TimeUnit.SECONDS));
            assertEquals(16L, store.reservedBytes());

            store.close();
            blocked.allowRead();

            ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals(0L, managedFileCount(temp));
        } finally {
            blocked.allowRead();
            executor.shutdownNow();
            store.close();
        }
    }

    @Test
    void closeRemovesCompletedAssetsAndRejectsFurtherOwnershipChanges() throws Exception {
        MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096);
        MediaAsset asset = importBytes(store, "close.ogg", bytes(12), 12);
        Path media = temp.resolve(asset.id() + ".media");

        store.close();
        assertFalse(Files.exists(media));
        assertThrows(IllegalStateException.class, () -> store.retain(asset.id()));
        assertThrows(IOException.class, () -> store.release(asset.id()));
    }

    @Test
    void rejectsEmptyImport() throws Exception {
        try (MediaAssetStore store = new MediaAssetStore(temp, 1024, 4096)) {
            assertThrows(IOException.class, () -> importBytes(store, "empty.wav", new byte[0], 0));
            assertEquals(0L, managedFileCount(temp));
        }
    }

    private static MediaAsset importBytes(MediaAssetStore store, String name, byte[] data, long declaredSize)
            throws IOException {
        try (var channel = Channels.newChannel(new java.io.ByteArrayInputStream(data))) {
            return store.importAsset(name, declaredSize, channel);
        }
    }

    private static byte[] bytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) (i * 31 + 7);
        return data;
    }

    private static long managedFileCount(Path root) throws IOException {
        try (var files = Files.list(root)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".part") || name.endsWith(".media");
            }).count();
        }
    }

    private static final class BlockingChannel implements ReadableByteChannel {
        private final byte[] data;
        private final CountDownLatch readEntered = new CountDownLatch(1);
        private final CountDownLatch mayRead = new CountDownLatch(1);
        private int position;
        private volatile boolean open = true;

        BlockingChannel(byte[] data) {
            this.data = data;
        }

        boolean awaitRead(long timeout, TimeUnit unit) throws InterruptedException {
            return readEntered.await(timeout, unit);
        }

        void allowRead() {
            mayRead.countDown();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!open) throw new IOException("channel is closed");
            if (position >= data.length) return -1;
            if (position == 0) {
                readEntered.countDown();
                try {
                    if (!mayRead.await(5, TimeUnit.SECONDS)) throw new IOException("test read timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test read interrupted", exception);
                }
            }
            int count = Math.min(dst.remaining(), data.length - position);
            dst.put(data, position, count);
            position += count;
            return count;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            mayRead.countDown();
        }
    }
}
