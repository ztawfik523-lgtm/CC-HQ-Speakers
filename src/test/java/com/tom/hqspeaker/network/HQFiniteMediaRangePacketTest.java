package com.tom.hqspeaker.network;

import com.tom.hqspeaker.media.FiniteRangeLimits;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HQFiniteMediaRangePacketTest {
    @Test
    void requestWireSanityUsesSharedMaximum() {
        UUID source = UUID.randomUUID();
        UUID asset = UUID.randomUUID();

        assertTrue(new HQFiniteMediaRangeRequestPacket(
            source, asset, 1L, 0L, FiniteRangeLimits.MAX_RANGE_BYTES).sensible());
        assertFalse(new HQFiniteMediaRangeRequestPacket(
            source, asset, 1L, 0L, FiniteRangeLimits.MAX_RANGE_BYTES + 1).sensible());
        assertFalse(new HQFiniteMediaRangeRequestPacket(source, asset, 1L, -1L, 1).sensible());
        assertFalse(new HQFiniteMediaRangeRequestPacket(source, asset, 1L, 0L, 0).sensible());
    }

    @Test
    void responseWireSanityUsesSharedMaximum() {
        UUID source = UUID.randomUUID();
        UUID asset = UUID.randomUUID();

        assertTrue(new HQFiniteMediaRangeDataPacket(
            source, asset, 1L, 0L, new byte[FiniteRangeLimits.MAX_RANGE_BYTES]).sensible());
        assertFalse(new HQFiniteMediaRangeDataPacket(
            source, asset, 1L, 0L, new byte[FiniteRangeLimits.MAX_RANGE_BYTES + 1]).sensible());
        assertFalse(new HQFiniteMediaRangeDataPacket(source, asset, 1L, -1L, new byte[1]).sensible());
        assertFalse(new HQFiniteMediaRangeDataPacket(source, asset, 1L, 0L, new byte[0]).sensible());
    }
}
