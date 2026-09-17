package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FiniteDecodeCoordinatorTest {
    @Test
    void revisionChangesRestartButOrdinarySnapshotsKeepHealthyDecoder() {
        FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();

        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(1L, false, false, false));
        assertEquals(FiniteDecodeCoordinator.StateDecision.KEEP,
            coordinator.observeState(1L, true, false, false));
        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(2L, true, false, false));
        assertEquals(2L, coordinator.serverRevision());
    }

    @Test
    void staleServerRevisionIsRejected() {
        FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();
        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(3L, false, false, false));
        assertEquals(FiniteDecodeCoordinator.StateDecision.STALE,
            coordinator.observeState(2L, true, false, false));
        assertEquals(3L, coordinator.serverRevision());
    }

    @Test
    void hibernationKeepsRevisionAndUnmuteRebuildsWithoutSyntheticSeek() {
        FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();

        assertEquals(FiniteDecodeCoordinator.StateDecision.HIBERNATE,
            coordinator.observeState(4L, true, false, true));
        assertEquals(4L, coordinator.serverRevision());
        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(4L, false, false, false));
    }

    @Test
    void exhaustedNonLoopingEpochStaysExhaustedUntilRevisionOrLoopEnable() {
        FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();
        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(1L, false, false, false));
        assertEquals(FiniteDecodeCoordinator.StateDecision.KEEP,
            coordinator.observeState(1L, false, true, false));

        // Enabling looping means exhaustion no longer blocks rebuilding the same server revision.
        assertEquals(FiniteDecodeCoordinator.StateDecision.RESTART,
            coordinator.observeState(1L, false, false, false));
    }

    @Test
    void localWorkerIsInvalidatedBeforeCancellationCanReport() {
        FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();

        long first = coordinator.invalidateLocalEpoch();
        assertTrue(coordinator.isCurrentLocalEpoch(first));

        long replacement = coordinator.invalidateLocalEpoch();
        assertFalse(coordinator.isCurrentLocalEpoch(first));
        assertTrue(coordinator.isCurrentLocalEpoch(replacement));
    }
}
