package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FiniteRecoveryCoordinatorTest {
    @Test
    void continuousSilenceBecomesLongStarvationAndDataClearsIt() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();

        recovery.observeRead(FinitePcmReadAdapter.State.SILENCE, 100L);
        assertFalse(recovery.longStarved(599L, 500L));
        assertTrue(recovery.longStarved(600L, 500L));

        recovery.observeRead(FinitePcmReadAdapter.State.DATA, 601L);
        assertFalse(recovery.longStarved(2_000L, 500L));
    }

    @Test
    void rejoinReadyRetriesUntilAnAuthoritativeStateArrives() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();
        recovery.beginRejoin();

        assertTrue(recovery.awaitingState());
        assertTrue(recovery.readyDue(1_000L, 250L));

        recovery.markReadyAttempt(1_000L);
        assertFalse(recovery.readyDue(1_249L, 250L));
        assertTrue(recovery.readyDue(1_250L, 250L));

        recovery.markReadyAttempt(1_250L);
        recovery.stateReceived();

        assertFalse(recovery.awaitingState());
        assertFalse(recovery.readyDue(10_000L, 250L));
    }

    @Test
    void beginningRecoveryClearsAnOldStarvationTimer() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();
        recovery.observeRead(FinitePcmReadAdapter.State.SILENCE, 100L);
        assertTrue(recovery.longStarved(1_000L, 500L));

        recovery.beginRejoin();

        assertFalse(recovery.longStarved(10_000L, 500L));
        assertTrue(recovery.awaitingState());
    }

    @Test
    void repeatedBeginRejoinDoesNotResetReadyRetryTiming() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();
        recovery.beginRejoin();
        recovery.markReadyAttempt(1_000L);

        recovery.beginRejoin();

        assertFalse(recovery.readyDue(1_100L, 250L));
        assertTrue(recovery.readyDue(1_250L, 250L));
    }
    @Test
    void negativeNanoTimeValuesDoNotLookLikeNoStarvation() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();

        recovery.observeRead(FinitePcmReadAdapter.State.SILENCE, -1_000L);

        assertFalse(recovery.longStarved(-501L, 500L));
        assertTrue(recovery.longStarved(-500L, 500L));
    }

    @Test
    void readyRetrySurvivesNanoTimeWraparound() {
        FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();
        recovery.beginRejoin();
        recovery.markReadyAttempt(Long.MAX_VALUE - 100L);

        assertTrue(recovery.readyDue(Long.MIN_VALUE + 200L, 250L));
    }

}

