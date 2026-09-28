package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HQSoundPhysicsRefreshPolicyTest {
    private static final long MS = 1_000_000L;

    @Test
    void tinyJitterDoesNotBecomeMovementButSafetyStillFires() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(200 * MS, 0.01, 0, 0, 0.01, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(400 * MS, -0.01, 0, 0, -0.01, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(999 * MS, 0.01, 0, 0, 0.01, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.HARD_STALE,
            policy.due(1_000 * MS, 0.01, 0, 0, 0.01, 0, 0));
    }

    @Test
    void slowMovementAccumulatesFromLastRefresh() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(100 * MS, 0.05, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(200 * MS, 0.10, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.MOVEMENT,
            policy.due(300 * MS, 0.16, 0, 0, 0, 0, 0));
    }

    @Test
    void ordinaryMovementRespectsMinimumInterval() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(50 * MS, 0.20, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.MOVEMENT,
            policy.due(100 * MS, 0.20, 0, 0, 0, 0, 0));
    }

    @Test
    void largeDisplacementIsUrgentImmediately() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.URGENT,
            policy.due(10 * MS, 1.01, 0, 0, 0, 0, 0));
    }

    @Test
    void movementGetsOneFinalSettleRefresh() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        long movementTime = 200 * MS;
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.MOVEMENT,
            policy.due(movementTime, 0.16, 0, 0, 0, 0, 0));
        policy.recordRefresh(
            movementTime,
            0.16, 0, 0,
            0, 0, 0,
            HQSoundPhysicsRefreshPolicy.Reason.MOVEMENT);

        // The listener continues a little further, then stops before crossing another 0.15 block threshold.
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(300 * MS, 0.25, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(500 * MS, 0.25, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.SETTLE,
            policy.due(560 * MS, 0.25, 0, 0, 0, 0, 0));

        policy.recordRefresh(
            560 * MS,
            0.25, 0, 0,
            0, 0, 0,
            HQSoundPhysicsRefreshPolicy.Reason.SETTLE);

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(800 * MS, 0.25, 0, 0, 0, 0, 0));
    }

    @Test
    void explicitUrgencyClearsAfterSuccessfulRefresh() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();
        policy.markUrgent();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.URGENT,
            policy.due(10 * MS, 0, 0, 0, 0, 0, 0));
        policy.recordRefresh(
            10 * MS,
            0, 0, 0,
            0, 0, 0,
            HQSoundPhysicsRefreshPolicy.Reason.URGENT);

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(20 * MS, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void externalSprRefreshResetsSafetyDeadline() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        policy.recordExternalRefresh(
            800 * MS,
            0, 0, 0,
            0, 0, 0);

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(1_700 * MS, 0, 0, 0, 0, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.HARD_STALE,
            policy.due(1_800 * MS, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void speakerMovementUsesSameThresholdAsListenerMovement() {
        HQSoundPhysicsRefreshPolicy policy = policyAtOrigin();

        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.NONE,
            policy.due(150 * MS, 0, 0, 0, 0.14, 0, 0));
        assertEquals(HQSoundPhysicsRefreshPolicy.Reason.MOVEMENT,
            policy.due(200 * MS, 0, 0, 0, 0.16, 0, 0));
    }

    private static HQSoundPhysicsRefreshPolicy policyAtOrigin() {
        return new HQSoundPhysicsRefreshPolicy(
            0L,
            0, 0, 0,
            0, 0, 0);
    }
}
