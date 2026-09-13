package com.tom.hqspeaker.media;

import java.util.UUID;

/** Pure validation helpers for M1F request/completion identity and listener eligibility. */
public final class FiniteRangeValidation {
    private FiniteRangeValidation() {}

    public static boolean requestMatches(
            UUID expectedSource, UUID expectedAsset, long expectedGeneration, long totalBytes,
            UUID actualSource, UUID actualAsset, long actualGeneration, long offset, int length) {
        if (expectedSource == null || expectedAsset == null || actualSource == null || actualAsset == null) return false;
        if (!expectedSource.equals(actualSource) || !expectedAsset.equals(actualAsset)) return false;
        if (expectedGeneration <= 0L || actualGeneration != expectedGeneration || totalBytes <= 0L) return false;
        if (offset < 0L || offset >= totalBytes || length <= 0 || length > FiniteRangeLimits.MAX_RANGE_BYTES) return false;
        return length <= totalBytes - offset;
    }

    public static boolean completionMatches(
            UUID expectedAsset, long expectedGeneration, UUID completedAsset, long completedGeneration) {
        return expectedAsset != null && completedAsset != null
            && expectedGeneration > 0L && completedGeneration == expectedGeneration
            && expectedAsset.equals(completedAsset);
    }

    public static boolean listenerRelevant(boolean sameDimension, boolean removed, double distanceSquared, double radius) {
        return sameDimension && !removed && Double.isFinite(distanceSquared) && distanceSquared >= 0.0
            && Double.isFinite(radius) && radius >= 0.0 && distanceSquared <= radius * radius;
    }
}
