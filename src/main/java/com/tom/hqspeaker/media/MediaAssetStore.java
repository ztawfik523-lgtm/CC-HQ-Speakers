package com.tom.hqspeaker.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side owner for reusable encoded finite-media files.
 *
 * <p>An asset is independent of any physical speaker. Importing creates one owner reference. A prepared owner or
 * playback may {@link #retain(UUID)} another reference and must later {@link #release(UUID)} it. The encoded file is
 * deleted only when the final reference is released.</p>
 *
 * <p>The caller supplies both the per-asset and total-byte limits. This class intentionally does not decide a global
 * storage policy for the mod.</p>
 *
 * <p>Imports are written to a UUID-named {@code .part} file, forced to disk, and atomically renamed to
 * {@code .media}. On startup, managed {@code .part} and {@code .media} files left by an earlier process are removed:
 * reference ownership is process-local at this milestone, so no old file can still have a live playback reference.
 * A root-level file lock prevents a second live store from pruning files owned by the first.</p>
 */
public final class MediaAssetStore implements AutoCloseable {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final String LOCK_NAME = ".asset-store.lock";

    private static final class Entry {
        final MediaAsset asset;
        final Path path;
        int references = 1;

        Entry(MediaAsset asset, Path path) {
            this.asset = asset;
            this.path = path;
        }
    }

    private final Path root;
    private final long maxAssetBytes;
    private final long maxTotalBytes;
    private final FileChannel lockChannel;
    private final FileLock storeLock;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Set<Path> activeParts = new HashSet<>();
    private long committedBytes;
    private long reservedBytes;
    private boolean closed;
    private boolean closeCleanupDone;
    private boolean lockReleased;

    public MediaAssetStore(Path root, long maxAssetBytes, long maxTotalBytes) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (maxAssetBytes <= 0L) throw new IllegalArgumentException("maxAssetBytes must be positive");
        if (maxTotalBytes <= 0L) throw new IllegalArgumentException("maxTotalBytes must be positive");
        this.maxAssetBytes = maxAssetBytes;
        this.maxTotalBytes = maxTotalBytes;

        Files.createDirectories(this.root);
        FileChannel openedLockChannel = FileChannel.open(this.root.resolve(LOCK_NAME),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquiredLock = null;
        boolean initialized = false;
        try {
            try {
                acquiredLock = openedLockChannel.tryLock();
            } catch (OverlappingFileLockException ignored) {
                // Another store in this JVM already owns this directory.
            }
            if (acquiredLock == null) throw new IOException("media asset store directory is already in use");
            pruneManagedOrphans();
            initialized = true;
        } finally {
            if (!initialized) {
                if (acquiredLock != null) acquiredLock.release();
                openedLockChannel.close();
            }
        }
        lockChannel = openedLockChannel;
        storeLock = acquiredLock;
    }

    /**
     * Import one exact-size encoded source. The source channel remains owned by the caller and is not closed here.
     *
     * @param sourceName diagnostic/original name only; it is never used as a filesystem path
     * @param sizeBytes exact encoded size expected from {@code source}
     * @param source source channel positioned at the first encoded byte
     * @return a new asset with one owner reference
     */
    public MediaAsset importAsset(String sourceName, long sizeBytes, ReadableByteChannel source) throws IOException {
        Objects.requireNonNull(source, "source");
        validateImportSize(sizeBytes);

        UUID id = UUID.randomUUID();
        Path part = root.resolve(id + ".part");
        Path media = root.resolve(id + ".media");
        reserveImport(part, sizeBytes);

        boolean moved = false;
        try {
            writeExact(part, source, sizeBytes);
            Files.move(part, media, StandardCopyOption.ATOMIC_MOVE);
            moved = true;

            MediaAsset asset = new MediaAsset(id, sizeBytes, sourceName);
            synchronized (this) {
                finishReservation(part, sizeBytes);
                if (closed) throw new IOException("media asset store is closed");
                entries.put(id, new Entry(asset, media));
                committedBytes += sizeBytes;
            }
            return asset;
        } catch (IOException | RuntimeException exception) {
            cancelReservation(part, sizeBytes);
            try {
                Files.deleteIfExists(moved ? media : part);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            try {
                releaseRootLockIfReady();
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    /** Convenience import for a normal filesystem path. */
    public MediaAsset importFile(Path source, String sourceName) throws IOException {
        Objects.requireNonNull(source, "source");
        long size = Files.size(source);
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            return importAsset(sourceName, size, channel);
        }
    }

    public synchronized Optional<MediaAsset> get(UUID id) {
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        return entry == null ? Optional.empty() : Optional.of(entry.asset);
    }

    /** Add one owner/playback reference to an existing asset. */
    public synchronized boolean retain(UUID id) {
        ensureOpenUnchecked();
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        if (entry == null) return false;
        if (entry.references == Integer.MAX_VALUE) throw new IllegalStateException("asset reference count overflow");
        entry.references++;
        return true;
    }

    /**
     * Release one owner/playback reference. The encoded file is removed when this was the final reference.
     *
     * @return false when the asset does not exist, true when a reference was released
     */
    public synchronized boolean release(UUID id) throws IOException {
        ensureOpen();
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        if (entry == null) return false;
        if (entry.references > 1) {
            entry.references--;
            return true;
        }

        // Keep the bookkeeping/ref alive if deletion fails, so quota/accounting never claims space was freed when it
        // was not actually freed.
        Files.deleteIfExists(entry.path);
        entries.remove(id);
        committedBytes -= entry.asset.sizeBytes();
        return true;
    }

    /** Open the encoded asset for bounded/random reads. Callers must hold an owner/playback reference while open. */
    public synchronized SeekableByteChannel openRead(UUID id) throws IOException {
        ensureOpen();
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        if (entry == null) throw new IOException("unknown media asset " + id);
        return FileChannel.open(entry.path, StandardOpenOption.READ);
    }

    public synchronized int referenceCount(UUID id) {
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        return entry == null ? 0 : entry.references;
    }

    public synchronized int assetCount() {
        return entries.size();
    }

    /** Bytes belonging to completed, reachable assets. */
    public synchronized long committedBytes() {
        return committedBytes;
    }

    /** Bytes currently reserved by imports which have not completed yet. */
    public synchronized long reservedBytes() {
        return reservedBytes;
    }

    public long maxAssetBytes() {
        return maxAssetBytes;
    }

    public long maxTotalBytes() {
        return maxTotalBytes;
    }

    private void validateImportSize(long sizeBytes) throws IOException {
        if (sizeBytes <= 0L) throw new IOException("media asset is empty");
        if (sizeBytes > maxAssetBytes) {
            throw new IOException("media asset exceeds per-asset limit of " + maxAssetBytes + " bytes");
        }
    }

    private synchronized void reserveImport(Path part, long sizeBytes) throws IOException {
        ensureOpen();
        long available = maxTotalBytes - committedBytes;
        if (reservedBytes > available) {
            throw new IllegalStateException("media asset quota accounting is inconsistent");
        }
        available -= reservedBytes;
        if (sizeBytes > available) {
            throw new IOException("media asset store exceeds total limit of " + maxTotalBytes + " bytes");
        }
        reservedBytes += sizeBytes;
        activeParts.add(part);
    }

    private synchronized void finishReservation(Path part, long sizeBytes) {
        if (activeParts.remove(part)) reservedBytes -= sizeBytes;
    }

    private synchronized void cancelReservation(Path part, long sizeBytes) {
        if (activeParts.remove(part)) reservedBytes -= sizeBytes;
    }

    private static void writeExact(Path part, ReadableByteChannel source, long sizeBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(COPY_BUFFER_BYTES);
        long remaining = sizeBytes;
        try (FileChannel output = FileChannel.open(part,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while (remaining > 0L) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                int read = source.read(buffer);
                if (read < 0) throw new EOFException("media source ended before declared size");
                if (read == 0) {
                    Thread.onSpinWait();
                    continue;
                }
                remaining -= read;
                buffer.flip();
                while (buffer.hasRemaining()) output.write(buffer);
            }

            ByteBuffer extra = ByteBuffer.allocate(1);
            int extraRead = source.read(extra);
            if (extraRead > 0) throw new IOException("media source is larger than declared size");
            output.force(true);
        }
    }

    private void pruneManagedOrphans() throws IOException {
        try (var files = Files.list(root)) {
            for (Path path : files.toList()) {
                if (Files.isRegularFile(path) && isManagedName(path.getFileName().toString())) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean isManagedName(String name) {
        String suffix;
        if (name.endsWith(".part")) suffix = ".part";
        else if (name.endsWith(".media")) suffix = ".media";
        else return false;

        String id = name.substring(0, name.length() - suffix.length());
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("media asset store is closed");
    }

    private void ensureOpenUnchecked() {
        if (closed) throw new IllegalStateException("media asset store is closed");
    }

    private synchronized void releaseRootLockIfReady() throws IOException {
        if (!closed || !closeCleanupDone || !activeParts.isEmpty() || lockReleased) return;

        IOException failure = null;
        if (storeLock.isValid()) {
            try {
                storeLock.release();
            } catch (IOException exception) {
                failure = exception;
            }
        }
        try {
            lockChannel.close();
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
        lockReleased = true;
    }

    /**
     * Server-shutdown cleanup. Completed files are removed immediately. If an import is still active, the root lock
     * remains held until that import observes the closed store and cleans its unpublished file. A hard process crash is
     * handled by next-start orphan pruning.
     */
    @Override
    public void close() throws IOException {
        ArrayList<Entry> toDelete = null;
        synchronized (this) {
            if (!closed) {
                closed = true;
                toDelete = new ArrayList<>(entries.values());
                entries.clear();
                committedBytes = 0L;
            }
        }

        IOException failure = null;
        if (toDelete != null) {
            for (Entry entry : toDelete) {
                try {
                    Files.deleteIfExists(entry.path);
                } catch (IOException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            synchronized (this) {
                closeCleanupDone = true;
            }
        }

        try {
            releaseRootLockIfReady();
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
}
