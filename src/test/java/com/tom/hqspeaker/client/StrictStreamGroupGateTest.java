package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StrictStreamGroupGateTest {
    @Test
    void sealsTheMembersSeenBeforeTheDeadlineWithoutExpectedCount() {
        UUID group = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        StrictStreamGroupGate gate = new StrictStreamGroupGate(group);

        assertTrue(gate.accept(a, 120, 100));
        assertTrue(gate.accept(b, 120, 119));
        assertFalse(gate.isSealed());
        assertFalse(gate.sealIfDue(119));
        assertTrue(gate.sealIfDue(120));
        assertEquals(java.util.List.of(a, b), gate.members());
    }

    @Test
    void rejectsLateMembersUntilANewGroupIsCreated() {
        UUID group = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        StrictStreamGroupGate gate = new StrictStreamGroupGate(group);

        assertTrue(gate.accept(a, 50, 40));
        assertTrue(gate.sealIfDue(50));
        assertFalse(gate.accept(late, 50, 51));
        assertEquals(java.util.List.of(a), gate.members());

        StrictStreamGroupGate rerun = new StrictStreamGroupGate(UUID.randomUUID());
        assertTrue(rerun.accept(late, 80, 60));
    }

    @Test
    void packetArrivingAtTheCutoffIsAlreadyLate() {
        StrictStreamGroupGate gate = new StrictStreamGroupGate(UUID.randomUUID());
        assertFalse(gate.accept(UUID.randomUUID(), 25, 25));
        assertFalse(gate.isSealed());
        assertTrue(gate.sealIfDue(25));
        assertTrue(gate.isSealed());
        assertTrue(gate.members().isEmpty());
    }

    @Test
    void latePacketCannotSkipTheSealTransitionForExistingMembers() {
        StrictStreamGroupGate gate = new StrictStreamGroupGate(UUID.randomUUID());
        UUID early = UUID.randomUUID();

        assertTrue(gate.accept(early, 40, 30));
        assertFalse(gate.accept(UUID.randomUUID(), 40, 40));
        assertFalse(gate.isSealed());
        assertTrue(gate.sealIfDue(40));
        assertEquals(java.util.List.of(early), gate.members());
    }

    @Test
    void cannotMarkStartedBeforeSeal() {
        StrictStreamGroupGate gate = new StrictStreamGroupGate(UUID.randomUUID());
        assertThrows(IllegalStateException.class, gate::markStarted);
    }
}
