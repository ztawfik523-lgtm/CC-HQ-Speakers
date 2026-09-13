package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FiniteRangeValidationTest {
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID ASSET = UUID.randomUUID();

    @Test
    void wireRangeUsesSharedPacketMaximum() {
        assertTrue(FiniteRangeValidation.wireRangeSensible(
            SOURCE, ASSET, 1L, 0L, FiniteRangeLimits.MAX_RANGE_BYTES));
        assertFalse(FiniteRangeValidation.wireRangeSensible(
            SOURCE, ASSET, 1L, 0L, FiniteRangeLimits.MAX_RANGE_BYTES + 1));
        assertFalse(FiniteRangeValidation.wireRangeSensible(SOURCE, ASSET, 1L, -1L, 1));
        assertFalse(FiniteRangeValidation.wireRangeSensible(SOURCE, ASSET, 1L, 0L, 0));
        assertFalse(FiniteRangeValidation.wireRangeSensible(null, ASSET, 1L, 0L, 1));
    }

    @Test
    void requestRequiresExactSourceGenerationAssetAndBounds() {
        assertTrue(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 7L, 123_456L, FiniteRangeLimits.MAX_RANGE_BYTES));

        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            UUID.randomUUID(), ASSET, 7L, 0L, 1));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, UUID.randomUUID(), 7L, 0L, 1));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 8L, 0L, 1));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 7L, -1L, 1));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 7L, 1_000_000L, 1));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 7L, 999_999L, 2));
        assertFalse(FiniteRangeValidation.requestMatches(
            SOURCE, ASSET, 7L, 1_000_000L,
            SOURCE, ASSET, 7L, 0L, FiniteRangeLimits.MAX_RANGE_BYTES + 1));
    }

    @Test
    void staleCompletionCannotMatchReplacement() {
        assertTrue(FiniteRangeValidation.completionMatches(ASSET, 4L, ASSET, 4L));
        assertFalse(FiniteRangeValidation.completionMatches(ASSET, 4L, ASSET, 5L));
        assertFalse(FiniteRangeValidation.completionMatches(ASSET, 4L, UUID.randomUUID(), 4L));
    }

    @Test
    void listenerMustRemainInSameDimensionAndRange() {
        double radius = 32.0;
        assertTrue(FiniteRangeValidation.listenerRelevant(true, false, radius * radius, radius));
        assertFalse(FiniteRangeValidation.listenerRelevant(false, false, 0.0, radius));
        assertFalse(FiniteRangeValidation.listenerRelevant(true, true, 0.0, radius));
        assertFalse(FiniteRangeValidation.listenerRelevant(true, false, radius * radius + 0.001, radius));
    }
}
