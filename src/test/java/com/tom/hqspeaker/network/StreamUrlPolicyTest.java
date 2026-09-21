package com.tom.hqspeaker.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class StreamUrlPolicyTest {
    @Test
    void acceptsDirectPublicHttpEndpoints() throws Exception {
        assertEquals("8.8.8.8", StreamUrlPolicy.validate("http://8.8.8.8:80/radio.mp3").getHost());
        assertEquals("1.1.1.1", StreamUrlPolicy.validate("https://1.1.1.1/radio").getHost());
    }

    @Test
    void rejectsLocalPrivateAndUserinfoTargets() {
        assertThrows(IOException.class, () -> StreamUrlPolicy.validate("http://127.0.0.1/radio"));
        assertThrows(IOException.class, () -> StreamUrlPolicy.validate("http://10.0.0.1/radio"));
        assertThrows(IOException.class, () -> StreamUrlPolicy.validate("http://localhost/radio"));
        assertThrows(IOException.class, () -> StreamUrlPolicy.validate("http://user@example.com/radio"));
        assertThrows(IOException.class, () -> StreamUrlPolicy.validate("http://8.8.8.8:22/radio"));
    }

    @Test
    void blocksCarrierGradeNatAndIpv6UniqueLocal() throws Exception {
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("fc00::1")));
        assertFalse(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void blocksDocumentationBenchmarkAndReservedIpv4Ranges() throws Exception {
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("0.1.2.3")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("192.0.2.10")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("198.18.0.1")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("198.51.100.10")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("203.0.113.10")));
        assertTrue(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("240.0.0.1")));
        assertFalse(StreamUrlPolicy.isBlockedAddress(InetAddress.getByName("1.1.1.1")));
    }
}
