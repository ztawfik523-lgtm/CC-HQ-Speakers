package com.tom.hqspeaker.media;

/** Pure arithmetic helpers for translating HQ Speaker storage policy into downstream capacity APIs. */
public final class MediaStorageLimits {
    private MediaStorageLimits() {}

    /**
     * Clamp a policy limit to the largest value a writable mount may safely add its own accounting overhead to.
     *
     * <p>ComputerCraft's WritableFileMount stores {@code capacity + MINIMUM_FILE_SIZE}. Passing Long.MAX_VALUE for
     * HQ Speaker's "unlimited" policy would therefore overflow to a negative capacity without this clamp.</p>
     */
    public static long writableMountCapacity(long policyBytes, long accountingOverheadBytes) {
        if (policyBytes <= 0L) throw new IllegalArgumentException("policyBytes must be positive");
        if (accountingOverheadBytes < 0L) throw new IllegalArgumentException("accountingOverheadBytes must be non-negative");
        long maximum = Long.MAX_VALUE - accountingOverheadBytes;
        return Math.min(policyBytes, maximum);
    }
}
