package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolV11SafetyTest {
    @Test
    void protocolIsV11AndStillRegistersExactlyNinePayloads() throws Exception {
        String source = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "network", "HQSpeakerNetwork.java"));

        assertTrue(source.contains("PROTOCOL_VERSION = \"11\""));
        long registrations = source.lines()
            .filter(line -> line.contains("registrar.playToClient(") || line.contains("registrar.playToServer("))
            .count();
        assertEquals(9L, registrations);
    }

    @Test
    void v11AudioPacketsCarryResolvedGainAndRange() throws Exception {
        String audio = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "network", "HQSpeakerAudioPacket.java"));
        String begin = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "network", "HQFiniteMediaBeginPacket.java"));
        String state = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "network", "HQFiniteMediaStatePacket.java"));

        assertTrue(audio.contains("public final float gain;"));
        assertTrue(audio.contains("public final float range;"));
        assertTrue(audio.contains("public final boolean explicitRange;"));
        assertTrue(begin.contains("float volume, float gain, float range, boolean explicitRange"));
        assertTrue(state.contains("float volume, float gain, float range, boolean explicitRange"));
    }
}
