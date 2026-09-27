package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.LuaState;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile the shipped master acceptance script with the same Cobalt parser CC:T uses at runtime. */
class MasterAcceptanceLuaSyntaxTest {
    @Test
    void masterAcceptanceCompilesWithComputerCraftLua() throws Exception {
        Path script = Path.of("scripts", "v10_acceptance.lua");
        assertTrue(Files.isRegularFile(script), "master acceptance script is missing");
        String source = Files.readString(script);
        assertFalse(source.matches("(?s).*\\bcollectgarbage\\s*\\(.*"),
            "CraftOS does not expose collectgarbage; the master runner must not call it");
        assertTrue(source.contains("--resume"), "master runner should support continuation without rerunning prior passes");
        assertTrue(source.contains("resumePassed[name]"), "resume mode must reuse only named prior PASS results");

        assertFalse(source.contains("peripheral.find(\"monitor\")"),
            "master acceptance should not carry a monitor/dashboard UI");
        assertFalse(source.contains("C1 Dimension leave/rejoin"),
            "dimension/chunk lifetime is not a release-acceptance gate");
        assertTrue(source.contains("optionalTotal = 4"),
            "target matrix should remain Sable + SPR + radio/membership + 8+ scale");
        assertTrue(source.contains("recordFailure(name, err"),
            "independent test failures should be recorded instead of aborting the suite");
        assertFalse(source.contains("MASTER ACCEPTANCE FAILED"),
            "an independent failure must not terminate the whole master run");
        assertTrue(source.contains("if message == \"terminated\""),
            "explicit operator termination must still stop the runner");
        assertFalse(source.contains("log(\"DIAG\", label .. \" = \" .. serialize(snap))"),
            "successful checks should not dump full diagnostic snapshots");
        assertTrue(source.contains("currentAudibleSpreadMs"),
            "sync verdicts should use current settled playback measurements");
        assertTrue(source.contains("consecutive >= 3"),
            "sync acceptance should require a short settled window, not one sample");
        assertFalse(source.contains("maxLogicalAudibleOffsetSpreadMs"),
            "historical worst-ever drift must not be a hard acceptance verdict");
        assertTrue(source.contains("assertSourceHealthy(source, \"Sable source \" .. i, 5, false)"),
            "C1 must judge Sable movement/final health without rejecting expected listener relevance cycling");
        assertTrue(source.contains("source.soundPhysicsProcessCalls"),
            "C2 must prove the real SPR processSound path, not only observe setEnvironment");
        assertTrue(source.contains("Use ONLY ordinary Minecraft-world geometry"),
            "C2 must keep Sable-wall acoustics outside the basic SPR integration check");
        assertTrue(source.contains("independentSubcheck(\"C4 finite scale\""),
            "C4 finite failure must not prevent RAW scale evidence");
        assertTrue(source.contains("independentSubcheck(\"C4 RAW scale\""),
            "C4 RAW must run independently from finite scale");

        int reloadBaseline = source.indexOf("local id, baseline = startRecoveryPlayback(\"resource reload\")");
        int reloadActionPrompt = source.indexOf("BASELINE READY -- NOW exit GUI.", reloadBaseline);
        assertTrue(reloadBaseline >= 0 && reloadActionPrompt > reloadBaseline,
            "R9 must finish its clean baseline before telling the operator to press F3+T");

        LuaState state = LuaState.builder().build();
        try (InputStream input = Files.newInputStream(script)) {
            assertDoesNotThrow(() -> LoadState.load(
                state, input, "@scripts/v10_acceptance.lua", state.globals()));
        }
    }
}
