package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaStorageLimitsTest {
    @Test
    void keepsNormalConfiguredCapacityUnchanged() {
        assertEquals(512L * 1024L * 1024L,
            MediaStorageLimits.writableMountCapacity(512L * 1024L * 1024L, 500L));
    }

    @Test
    void clampsUnlimitedSentinelBeforeWritableMountAccountingAddition() {
        assertEquals(Long.MAX_VALUE - 500L,
            MediaStorageLimits.writableMountCapacity(Long.MAX_VALUE, 500L));
    }

    @Test
    void validatesInputs() {
        assertThrows(IllegalArgumentException.class,
            () -> MediaStorageLimits.writableMountCapacity(0L, 500L));
        assertThrows(IllegalArgumentException.class,
            () -> MediaStorageLimits.writableMountCapacity(1L, -1L));
    }
}
