package com.tom.hqspeaker.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SableCompanionPackagingTest {
    @Test
    void embeddedCompanionAlwaysProvidesASafeImplementation() {
        assertNotNull(SableCompanion.INSTANCE);
    }
}
