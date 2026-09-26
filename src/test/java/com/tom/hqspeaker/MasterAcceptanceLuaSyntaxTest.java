package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.LuaState;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile the shipped master acceptance script with the same Cobalt parser CC:T uses at runtime. */
class MasterAcceptanceLuaSyntaxTest {
    @Test
    void masterAcceptanceCompilesWithComputerCraftLua() throws Exception {
        Path script = Path.of("scripts", "v10_acceptance.lua");
        assertTrue(Files.isRegularFile(script), "master acceptance script is missing");

        LuaState state = LuaState.builder().build();
        try (InputStream input = Files.newInputStream(script)) {
            assertDoesNotThrow(() -> LoadState.load(
                state, input, "@scripts/v10_acceptance.lua", state.globals()));
        }
    }
}
