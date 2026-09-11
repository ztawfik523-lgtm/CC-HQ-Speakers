package com.tom.hqspeaker.media;

import java.util.Objects;
import java.util.UUID;

/**
 * Public identity/size/analysis information for one encoded finite-media asset.
 *
 * <p>The store creates the identity when the encoded file is committed. M1D then attaches the already-computed
 * server metadata before the asset ID is returned to Lua. The actual disk path remains private to the store.</p>
 */
public final class MediaAsset {
    private final UUID id;
    private final long sizeBytes;
    private final String sourceName;
    private volatile MediaMetadata metadata;

    public MediaAsset(UUID id, long sizeBytes, String sourceName) {
        this.id = Objects.requireNonNull(id, "id");
        if (sizeBytes <= 0L) throw new IllegalArgumentException("sizeBytes must be positive");
        this.sizeBytes = sizeBytes;
        this.sourceName = sourceName == null ? "" : sourceName;
    }

    public UUID id() { return id; }
    public long sizeBytes() { return sizeBytes; }
    public String sourceName() { return sourceName; }
    public MediaMetadata metadata() { return metadata; }
    public boolean analyzed() { return metadata != null; }

    /** Attach the server analysis exactly once, before the asset is exposed to Lua/playback. */
    public synchronized void attachMetadata(MediaMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (this.metadata != null) throw new IllegalStateException("media asset metadata is already attached");
        this.metadata = metadata;
    }
}
