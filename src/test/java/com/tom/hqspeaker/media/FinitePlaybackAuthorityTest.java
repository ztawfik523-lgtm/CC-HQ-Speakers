package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinitePlaybackAuthorityTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void oneAuthorityIsTheSingleCanonicalTimelineForEveryEndpoint() {
        UUID id = UUID.randomUUID();
        FinitePlaybackAuthority authority = new FinitePlaybackAuthority(id, 20.0, 0L);

        assertEquals(id, authority.playbackId());
        assertEquals(3.0, authority.position(3 * SECOND), 1.0e-9);

        assertTrue(authority.pause(3 * SECOND));
        assertEquals(3.0, authority.position(9 * SECOND), 1.0e-9);

        assertTrue(authority.resume(9 * SECOND));
        assertEquals(4.0, authority.position(10 * SECOND), 1.0e-9);
    }

    @Test
    void semanticSeekAdvancesSharedStateAndDecodeRevisionsTogether() {
        FinitePlaybackAuthority authority = new FinitePlaybackAuthority(20.0, 0L);
        long stateRevision = authority.stateRevision();
        long decodeRevision = authority.decodeRevision();

        var result = authority.seek(8.0, SECOND);

        assertTrue(result.accepted());
        assertFalse(result.ended());
        assertEquals(stateRevision + 1L, authority.stateRevision());
        assertEquals(decodeRevision + 1L, authority.decodeRevision());
        assertEquals(8.0, authority.position(SECOND), 1.0e-9);
    }

    @Test
    void ordinaryStateChangesDoNotInventDecoderRestarts() {
        FinitePlaybackAuthority authority = new FinitePlaybackAuthority(20.0, 0L);
        long decodeRevision = authority.decodeRevision();

        assertTrue(authority.pause(SECOND));
        assertTrue(authority.resume(2 * SECOND));
        assertTrue(authority.setLooping(true, 3 * SECOND));

        assertEquals(decodeRevision, authority.decodeRevision());
        assertTrue(authority.stateRevision() > 1L);
    }

    @Test
    void snapshotIsInternallyCoherentAndCarriesPlaybackIdentity() {
        UUID id = UUID.randomUUID();
        FinitePlaybackAuthority authority = new FinitePlaybackAuthority(id, 10.0, 0L);
        assertTrue(authority.setLooping(true, SECOND));

        FinitePlaybackAuthority.Snapshot snapshot = authority.snapshot(12 * SECOND);

        assertEquals(id, snapshot.playbackId());
        assertEquals(FinitePlaybackAuthority.State.PLAYING, snapshot.state());
        assertEquals(2.0, snapshot.position(), 1.0e-9);
        assertEquals(10.0, snapshot.duration(), 1.0e-9);
        assertTrue(snapshot.looping());
        assertEquals(authority.stateRevision(), snapshot.stateRevision());
        assertEquals(authority.decodeRevision(), snapshot.decodeRevision());
    }

    @Test
    void sharedFailureFreezesCanonicalTime() {
        FinitePlaybackAuthority authority = new FinitePlaybackAuthority(20.0, 0L);

        assertTrue(authority.fail("shared asset failed", 4 * SECOND));
        assertEquals(FinitePlaybackAuthority.State.ERROR, authority.state());
        assertEquals(4.0, authority.position(15 * SECOND), 1.0e-9);
        assertEquals("shared asset failed", authority.error());
        assertFalse(authority.fail("second", 16 * SECOND));
    }
}
