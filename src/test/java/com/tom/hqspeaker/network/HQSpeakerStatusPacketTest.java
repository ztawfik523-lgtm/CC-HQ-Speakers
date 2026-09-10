package com.tom.hqspeaker.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HQSpeakerStatusPacketTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void acceptsSensibleFiniteTiming() {
        HQSpeakerStatusPacket packet = new HQSpeakerStatusPacket(
            SOURCE, 3, HQSpeakerStatusPacket.Transition.STARTED, 1.25, 8.0, "");

        assertTrue(packet.hasSensibleNumbers());
        assertEquals(3, packet.generation);
    }

    @Test
    void rejectsInvalidFiniteTiming() {
        assertFalse(packet(Double.NaN, 8.0).hasSensibleNumbers());
        assertFalse(packet(-1.0, 8.0).hasSensibleNumbers());
        assertFalse(packet(10.0, 8.0).hasSensibleNumbers());
        assertFalse(packet(0.0, HQSpeakerStatusPacket.MAX_DURATION_SECONDS + 1).hasSensibleNumbers());
    }

    @Test
    void capsErrorAndNormalizesGeneration() {
        String error = "x".repeat(HQSpeakerStatusPacket.MAX_ERROR_CHARS + 20);
        HQSpeakerStatusPacket packet = new HQSpeakerStatusPacket(
            SOURCE, -10, HQSpeakerStatusPacket.Transition.ERROR, 0.0, 0.0, error);

        assertEquals(0, packet.generation);
        assertEquals(HQSpeakerStatusPacket.MAX_ERROR_CHARS, packet.error.length());
    }

    private static HQSpeakerStatusPacket packet(double position, double duration) {
        return new HQSpeakerStatusPacket(
            SOURCE, 1, HQSpeakerStatusPacket.Transition.STARTED, position, duration, "");
    }
}
