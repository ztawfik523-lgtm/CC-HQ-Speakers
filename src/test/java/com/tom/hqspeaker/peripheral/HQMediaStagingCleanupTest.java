package com.tom.hqspeaker.peripheral;

import dan200.computercraft.api.filesystem.WritableMount;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HQMediaStagingCleanupTest {
    @Test
    void wholeOwnerCleanupDeletesEveryTopLevelStagingEntry() throws Exception {
        FakeMount mount = new FakeMount(List.of("one.tmp", "nested", "two.tmp"));

        HQMediaStaging.clearMountContents(mount);

        assertEquals(List.of("one.tmp", "nested", "two.tmp"), mount.deleted);
    }

    @Test
    void cleanupAttemptsRemainingEntriesAfterOneDeleteFails() {
        FakeMount mount = new FakeMount(List.of("bad.tmp", "good.tmp"));
        mount.failDeletes.add("bad.tmp");

        IOException failure = assertThrows(IOException.class, () -> HQMediaStaging.clearMountContents(mount));
        assertEquals("cannot delete bad.tmp", failure.getMessage());
        assertEquals(List.of("bad.tmp", "good.tmp"), mount.deleted);
    }

    private static final class FakeMount implements WritableMount {
        private final List<String> entries;
        private final List<String> deleted = new ArrayList<>();
        private final Set<String> failDeletes = new HashSet<>();

        FakeMount(List<String> entries) { this.entries = new ArrayList<>(entries); }

        @Override public boolean exists(String path) { return true; }
        @Override public boolean isDirectory(String path) { return path.isEmpty(); }
        @Override public void list(String path, List<String> contents) {
            if (!path.isEmpty()) throw new IllegalArgumentException("test mount only lists root");
            contents.addAll(entries);
        }
        @Override public long getSize(String path) { return 0L; }
        @Override public SeekableByteChannel openForRead(String path) { throw new UnsupportedOperationException(); }
        @Override public void makeDirectory(String path) { throw new UnsupportedOperationException(); }
        @Override public void delete(String path) throws IOException {
            deleted.add(path);
            if (failDeletes.contains(path)) throw new IOException("cannot delete " + path);
        }
        @Override public void rename(String source, String dest) { throw new UnsupportedOperationException(); }
        @Override public SeekableByteChannel openFile(String path, Set<OpenOption> options) {
            throw new UnsupportedOperationException();
        }
        @Override public long getRemainingSpace() { return Long.MAX_VALUE; }
        @Override public long getCapacity() { return Long.MAX_VALUE; }
    }
}
