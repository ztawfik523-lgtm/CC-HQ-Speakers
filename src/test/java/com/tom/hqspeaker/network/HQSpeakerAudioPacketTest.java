package com.tom.hqspeaker.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HQSpeakerAudioPacketTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void finitePacketRetainsGenerationControlMetadata() {
        HQSpeakerAudioPacket packet = new HQSpeakerAudioPacket(
            SOURCE,
            HQSpeakerAudioPacket.AudioFormat.MP3,
            0.75f,
            1, 2, 3,
            1, 2, 3,
            new byte[]{1, 2, 3},
            20,
            null,
            0,
            42,
            true,
            true
        );

        assertEquals(42, packet.generation);
        assertTrue(packet.finiteLooping);
        assertTrue(packet.finitePaused);
        assertFalse(packet.isStreamingFormat());
        assertEquals(0.75f, packet.volume);
    }

    @Test
    void packetClampsLogicalVolume() {
        HQSpeakerAudioPacket high = new HQSpeakerAudioPacket(
            SOURCE, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            99.0f, 0, 0, 0, 0, 0, 0, new byte[]{1, 2});
        HQSpeakerAudioPacket low = new HQSpeakerAudioPacket(
            SOURCE, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            -5.0f, 0, 0, 0, 0, 0, 0, new byte[]{1, 2});

        assertEquals(3.0f, high.volume);
        assertEquals(0.0f, low.volume);
    }

    @Test
    void identifiesOpenEndedStreamFormats() {
        assertTrue(new HQSpeakerAudioPacket(
            SOURCE, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM,
            1.0f, 0, 0, 0, 0, 0, 0, "https://example.invalid/live.mp3").isStreamingFormat());

        assertFalse(new HQSpeakerAudioPacket(
            SOURCE, HQSpeakerAudioPacket.AudioFormat.OGG_VORBIS,
            1.0f, 0, 0, 0, 0, 0, 0, new byte[]{1}).isStreamingFormat());
    }
}
