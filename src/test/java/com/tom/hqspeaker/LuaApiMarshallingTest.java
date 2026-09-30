package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaApiMarshallingTest {

    @Test
    void multispeakerDiscoveryUsesCompositeRegistryOnly() throws Exception {
        String legacy = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "peripheral", "HQSpeakerPeripheral.java"));
        String composite = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "peripheral", "HQSpeakerCompositePeripheral.java"));

        assertFalse(legacy.contains("COMPUTER_SPEAKERS"),
            "legacy peripheral must not keep a second computer-to-speaker registry");
        assertFalse(legacy.contains("getSpeakerCount(IComputerAccess"),
            "group discovery must not fall through to the legacy peripheral");
        assertTrue(composite.contains("GROUP_DISCOVERY = Set.of("));
        assertTrue(composite.contains("\"getSpeakerCount\", \"getSpeakers\", \"getSpeakerPos\""));
        assertTrue(composite.contains("if (GROUP_DISCOVERY.contains(name)) return callGroupDiscovery(name, computer, args);"));
        assertTrue(composite.contains("List<HQSpeakerCompositePeripheral> members = membersFor(computer);"),
            "discovery must use the same registry as v11 grouped playback");
        assertFalse(legacy.contains("speakerReadyPending"),
            "HQ RAW must not keep the dead native speaker_audio_empty readiness path");
        assertFalse(composite.contains("Proxy.newProxyInstance"),
            "composite attachment must not wrap IComputerAccess just to suppress a dead event");
        assertTrue(composite.contains("attachedComputerIds"),
            "composite cleanup must track its own group registrations explicitly");
    }


    @Test
    void radioAndAudioStreamSurfaceStayNarrow() throws Exception {
        String transport = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "peripheral", "HQSpeakerPeripheral.java"));
        String stream = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "client", "HQAudioStream.java"));

        assertTrue(transport.contains("new String[]{\".mp3\"}"),
            "radio capability surface should advertise MP3 only");
        assertFalse(transport.contains("\".mp2\""),
            "radio capability surface must not advertise unsupported MPEG Layer II");
        assertFalse(stream.contains("getStreamingSource()"));
        assertFalse(stream.contains("getSharedStreamingTap()"));
        assertFalse(stream.contains("public boolean isStreaming()"));
        assertFalse(stream.contains("public boolean isEmpty()"));
        assertFalse(stream.contains("public boolean hasRealData()"));
    }

    @Test
    void streamUrlUsesLuaMarshalableReturnType() throws Exception {
        Path sourcePath = Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "peripheral", "HQSpeakerPeripheral.java");
        String source = Files.readString(sourcePath);

        assertFalse(source.matches("(?s).*@LuaFunction\\s+public\\s+final\\s+Optional<String>\\s+getStreamUrl\\s*\\(\\).*"),
            "getStreamUrl must not expose java.util.Optional to ComputerCraft Lua");
        assertTrue(source.matches("(?s).*@LuaFunction\\s+public\\s+final\\s+Object\\[\\]\\s+getStreamUrl\\s*\\(\\).*"),
            "getStreamUrl should marshal nil/string as zero/one Lua return values");
    }
}
