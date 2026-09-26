package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaApiMarshallingTest {
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
