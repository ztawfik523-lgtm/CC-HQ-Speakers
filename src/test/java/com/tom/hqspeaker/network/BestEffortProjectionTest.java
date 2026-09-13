package com.tom.hqspeaker.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BestEffortProjectionTest {
    @Test
    void successfulProjectionReturnsTrue() {
        AtomicBoolean ran = new AtomicBoolean();
        assertTrue(BestEffortProjection.run(() -> ran.set(true), failure -> fail(failure)));
        assertTrue(ran.get());
    }

    @Test
    void runtimeFailureIsContainedAndReported() {
        AtomicBoolean reported = new AtomicBoolean();
        assertDoesNotThrow(() -> {
            boolean success = BestEffortProjection.run(
                () -> { throw new IllegalStateException("wire failed"); },
                failure -> {
                    assertEquals("wire failed", failure.getMessage());
                    reported.set(true);
                });
            assertFalse(success);
        });
        assertTrue(reported.get());
    }

    @Test
    void diagnosticFailureCannotEscapeEither() {
        assertDoesNotThrow(() -> {
            boolean success = BestEffortProjection.run(
                () -> { throw new IllegalStateException("wire failed"); },
                failure -> { throw new IllegalStateException("logger failed"); });
            assertFalse(success);
        });
    }
}
