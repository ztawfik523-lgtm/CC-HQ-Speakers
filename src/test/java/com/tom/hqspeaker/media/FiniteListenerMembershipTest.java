package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FiniteListenerMembershipTest {
    @Test
    void walkInStayLeaveAndReturnOnlyProduceRealTransitions() {
        FiniteListenerMembership membership = new FiniteListenerMembership();
        UUID player = UUID.randomUUID();

        assertTrue(membership.plan(Set.of()).joined().isEmpty());
        assertTrue(membership.plan(Set.of()).left().isEmpty());

        FiniteListenerMembership.Delta entered = membership.plan(Set.of(player));
        assertEquals(Set.of(player), entered.joined());
        assertTrue(entered.left().isEmpty());
        assertTrue(membership.admit(player));

        FiniteListenerMembership.Delta stayed = membership.plan(Set.of(player));
        assertTrue(stayed.joined().isEmpty());
        assertTrue(stayed.left().isEmpty());

        FiniteListenerMembership.Delta left = membership.plan(Set.of());
        assertTrue(left.joined().isEmpty());
        assertEquals(Set.of(player), left.left());
        assertTrue(membership.remove(player));

        FiniteListenerMembership.Delta returned = membership.plan(Set.of(player));
        assertEquals(Set.of(player), returned.joined());
        assertTrue(returned.left().isEmpty());
    }

    @Test
    void failedJoinOrLeaveCanBeRetriedWithoutInventingExtraTransitions() {
        FiniteListenerMembership membership = new FiniteListenerMembership();
        UUID player = UUID.randomUUID();

        assertEquals(Set.of(player), membership.plan(Set.of(player)).joined());
        assertEquals(Set.of(player), membership.plan(Set.of(player)).joined());

        membership.admit(player);
        assertEquals(Set.of(player), membership.plan(Set.of()).left());
        assertEquals(Set.of(player), membership.plan(Set.of()).left());

        membership.remove(player);
        assertTrue(membership.plan(Set.of()).left().isEmpty());
    }

    @Test
    void disconnectOrDimensionMismatchPrunesOnlyTheMissingMember() {
        FiniteListenerMembership membership = new FiniteListenerMembership();
        UUID staying = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        membership.admit(staying);
        membership.admit(gone);

        FiniteListenerMembership.Delta delta = membership.plan(Set.of(staying));
        assertTrue(delta.joined().isEmpty());
        assertEquals(Set.of(gone), delta.left());

        membership.remove(gone);
        assertTrue(membership.contains(staying));
        assertFalse(membership.contains(gone));
        assertTrue(membership.plan(Set.of(staying)).left().isEmpty());
    }

    @Test
    void stopReplacementOrTerminalClearDropsAllMembership() {
        FiniteListenerMembership membership = new FiniteListenerMembership();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        membership.admit(first);
        membership.admit(second);

        assertEquals(Set.of(first, second), membership.snapshot());
        membership.clear();

        assertTrue(membership.isEmpty());
        assertTrue(membership.snapshot().isEmpty());
        assertEquals(Set.of(first), membership.plan(Set.of(first)).joined());
    }
}
