package com.tom.hqspeaker.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HQSpeakerAudioPacketTest {
    @Test
    void rawV11RoundTripPreservesResolvedTuning() {
        UUID source = UUID.randomUUID();
        byte[] pcm = { 1, 0, 2, 0, 3, 0, 4, 0 };
        HQSpeakerAudioPacket input = new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            2.25f, 0.755f, 83.0f, true,
            10.5f, 20.5f, 30.5f,
            10, 20, 30,
            pcm, 123L
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HQSpeakerAudioPacket.encode(input, buf);
        HQSpeakerAudioPacket output = HQSpeakerAudioPacket.decode(buf);

        assertEquals(source, output.source);
        assertEquals(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, output.format);
        assertEquals(2.25f, output.volume, 1.0e-6f);
        assertEquals(0.755f, output.gain, 1.0e-6f);
        assertEquals(83.0f, output.range, 1.0e-6f);
        assertTrue(output.explicitRange);
        assertArrayEquals(pcm, output.data);
        assertEquals(123L, output.startTick);
        assertTrue(output.sensible());
    }

    @Test
    void radioV11RoundTripPreservesResolvedTuningAndGroup() {
        UUID source = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        HQSpeakerAudioPacket input = new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM,
            1.5f, 0.5f, 48.0f, false,
            1.5f, 2.5f, 3.5f,
            1, 2, 3,
            "https://example.com/radio.mp3", 456L, group
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HQSpeakerAudioPacket.encode(input, buf);
        HQSpeakerAudioPacket output = HQSpeakerAudioPacket.decode(buf);

        assertEquals(source, output.source);
        assertEquals(HQSpeakerAudioPacket.AudioFormat.MP3_STREAM, output.format);
        assertEquals(1.5f, output.volume, 1.0e-6f);
        assertEquals(0.5f, output.gain, 1.0e-6f);
        assertEquals(48.0f, output.range, 1.0e-6f);
        assertFalse(output.explicitRange);
        assertEquals("https://example.com/radio.mp3", output.streamUrl);
        assertEquals(456L, output.startTick);
        assertEquals(group, output.syncGroupId);
        assertTrue(output.sensible());
    }

    @Test
    void nonsensicalResolvedTuningIsRejected() {
        UUID source = UUID.randomUUID();

        assertFalse(new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            1.5f, Float.NaN, 48.0f, false,
            0, 0, 0, 0, 0, 0, new byte[]{ 1, 0 }, 0L
        ).sensible());

        assertFalse(new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            1.5f, 0.5f, HQSpeakerAudioPacket.MAX_WIRE_RANGE + 1.0f, false,
            0, 0, 0, 0, 0, 0, new byte[]{ 1, 0 }, 0L
        ).sensible());

        assertFalse(new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            1.5f, 0.5f, 0.0f, false,
            0, 0, 0, 0, 0, 0, new byte[]{ 1, 0 }, 0L
        ).sensible());

        assertTrue(new HQSpeakerAudioPacket(
            source, HQSpeakerAudioPacket.AudioFormat.PCM_S16LE,
            0.0f, 0.0f, 0.0f, false,
            0, 0, 0, 0, 0, 0, new byte[]{ 1, 0 }, 0L
        ).sensible());
    }
}
