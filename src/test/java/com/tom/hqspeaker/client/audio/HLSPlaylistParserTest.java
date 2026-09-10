package com.tom.hqspeaker.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HLSPlaylistParserTest {
    @Test
    void parsesLiveMediaSequenceAndResolvesRelativeSegments() {
        String playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:101
            #EXTINF:6.0,
            seg101.ts
            #EXTINF:6.0,
            /live/seg102.ts
            """;

        HLSPlaylistParser.Playlist parsed =
            HLSPlaylistParser.parse(playlist, "https://radio.example/station/");

        assertEquals(HLSPlaylistParser.PlaylistType.MEDIA, parsed.type);
        assertTrue(parsed.isLive());
        assertEquals(101L, parsed.mediaSequence);
        assertEquals(6.0, parsed.targetDuration);
        assertEquals(2, parsed.segments.size());
        assertEquals("https://radio.example/station/seg101.ts", parsed.segments.get(0).url);
        assertEquals("https://radio.example/live/seg102.ts", parsed.segments.get(1).url);
    }

    @Test
    void parsesMasterPlaylistAndResolvesVariant() {
        String playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            audio/128.m3u8
            """;

        HLSPlaylistParser.Playlist parsed =
            HLSPlaylistParser.parse(playlist, "https://radio.example/root/");

        assertEquals(HLSPlaylistParser.PlaylistType.MASTER, parsed.type);
        assertEquals(1, parsed.variants.size());
        assertEquals(128000, parsed.variants.get(0).bandwidth);
        assertEquals("mp4a.40.2", parsed.variants.get(0).codecs);
        assertEquals("https://radio.example/root/audio/128.m3u8", parsed.variants.get(0).url);
    }

    @Test
    void classifiesAudioOnlyCodecLists() {
        assertTrue(HLSPlaylistParser.isAudioOnly("mp4a.40.2"));
        assertTrue(HLSPlaylistParser.isAudioOnly("mp3"));
        assertFalse(HLSPlaylistParser.isAudioOnly("avc1.640029,mp4a.40.2"));
        assertFalse(HLSPlaylistParser.isAudioOnly(""));
        assertFalse(HLSPlaylistParser.isAudioOnly(null));
    }
}
