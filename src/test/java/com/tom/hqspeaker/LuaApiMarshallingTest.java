package com.tom.hqspeaker;

import com.tom.hqspeaker.peripheral.HQSpeakerPeripheral;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LuaApiMarshallingTest {
    @Test
    void streamUrlUsesLuaMarshalableReturnType() throws Exception {
        var method = HQSpeakerPeripheral.class.getMethod("getStreamUrl");
        assertFalse(Optional.class.isAssignableFrom(method.getReturnType()),
            "Lua-facing methods must not return java.util.Optional");
        assertEquals(Object[].class, method.getReturnType());

        var peripheral = new HQSpeakerPeripheral(BlockPos.ZERO, null);
        assertArrayEquals(new Object[0], peripheral.getStreamUrl());

        Field streamUrl = HQSpeakerPeripheral.class.getDeclaredField("streamUrl");
        streamUrl.setAccessible(true);
        streamUrl.set(peripheral, "https://example.invalid/radio.mp3");
        assertArrayEquals(new Object[]{"https://example.invalid/radio.mp3"}, peripheral.getStreamUrl());
    }
}
