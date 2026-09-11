package com.tom.hqspeaker.media;

import java.util.UUID;

/**
 * Public identity/size information for one encoded finite-media asset.
 *
 * The actual disk path is deliberately owned by {@link MediaAssetStore}: callers refer to media by UUID instead of
 * tying playback state to a speaker-specific file path.
 */
public record MediaAsset(UUID id, long sizeBytes, String sourceName) {
    public MediaAsset {
        if (id == null) throw new NullPointerException("id");
        if (sizeBytes <= 0L) throw new IllegalArgumentException("sizeBytes must be positive");
        sourceName = sourceName == null ? "" : sourceName;
    }
}
