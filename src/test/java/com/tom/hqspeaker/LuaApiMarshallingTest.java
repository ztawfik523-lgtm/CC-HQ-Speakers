package com.tom.hqspeaker;

import com.tom.hqspeaker.peripheral.HQSpeakerPeripheral;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LuaApiMarshallingTest {
    @Test
    void streamUrlUsesLuaMarshalableReturnType() throws Exception {
        var method = HQSpeakerPeripheral.class.getMethod("getStreamUrl");
        assertFalse(Optional.class.isAssignableFrom(method.getReturnType()),
            "Lua-facing methods must not return java.util.Optional");
        assertEquals(Object[].class, method.getReturnType(),
            "getStreamUrl should marshal nil/string as zero/one Lua return values");
    }
}
