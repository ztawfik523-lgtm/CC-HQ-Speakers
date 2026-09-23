package com.tom.hqspeaker.diagnostics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HQDiagnosticsTest {
    @AfterEach
    void disable() {
        HQDiagnostics.setEnabled(false);
    }

    @Test
    void recordsChannelHealthMovementSoundPhysicsAndSync() {
        HQDiagnostics.setEnabled(true);

        UUID one = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID two = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var id1 = new HQDiagnostics.SourceIdentity(one, "finite", "finite:test", 0, 64, 0, 48_000);
        var id2 = new HQDiagnostics.SourceIdentity(two, "finite", "finite:test", 2, 64, 0, 48_000);

        HQDiagnostics.registerSource(id1);
        HQDiagnostics.registerSource(id2);
        HQDiagnostics.channelStarted(id1, 7, 1.0f, 1.0f);
        HQDiagnostics.channelStarted(id2, 8, 1.0f, 1.0f);

        HQDiagnostics.recordBatch(List.of(
            sample(id1, "playing", 1.000, 0, 64, 0, 0, 64, 0, 7, 1.0f, 1.0f),
            sample(id2, "playing", 1.004, 2, 64, 0, 2, 64, 0, 8, 1.0f, 1.0f)
        ));
        HQDiagnostics.recordBatch(List.of(
            sample(id1, "playing", 1.100, 5, 64, 0, 5, 64, 0, 7, 0.85f, 0.40f),
            sample(id2, "playing", 1.105, 7, 64, 0, 7, 64, 0, 8, 0.90f, 0.50f)
        ));
        HQDiagnostics.recordBatch(List.of(
            sample(id1, "stopped", 1.100, 5, 64, 0, 5, 64, 0, 7, 0.85f, 0.40f),
            sample(id2, "playing", 1.110, 7, 64, 0, 7, 64, 0, 8, 0.90f, 0.50f)
        ));

        Map<String, Object> snapshot = HQDiagnostics.snapshot();
        assertEquals(2, ((Number) snapshot.get("sourceCount")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) snapshot.get("sources");
        Map<String, Object> first = sources.stream()
            .filter(row -> one.toString().equals(row.get("source")))
            .findFirst()
            .orElseThrow();

        assertEquals(1L, ((Number) first.get("playingToStoppedTransitions")).longValue());
        assertTrue(((Number) first.get("requestedMovement")).doubleValue() >= 5.0);
        assertTrue((Boolean) first.get("soundPhysicsProcessed"));
        assertTrue((Boolean) first.get("soundPhysicsChanged"));
        assertTrue(((Number) first.get("directGainHFRange")).doubleValue() > 0.5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) snapshot.get("groups");
        assertEquals(1, groups.size());
        Map<String, Object> group = groups.getFirst();
        assertEquals(2, ((Number) group.get("sourceCount")).intValue());
        assertEquals(3L, ((Number) group.get("completeBatches")).longValue());
        assertEquals(1L, ((Number) group.get("mixedStateBatches")).longValue());
        assertEquals(2, ((Number) group.get("channelMembers")).intValue());
        assertTrue(((Number) group.get("channelStartSkewMs")).doubleValue() >= 0.0);
        assertTrue(((Number) group.get("maxSecondsOffsetSpreadMs")).doubleValue() >= 4.0);
    }


    @Test
    void explicitNewGroupReplacesOldGroupButBlankContinuationDoesNot() {
        HQDiagnostics.setEnabled(true);

        UUID source = UUID.fromString("00000000-0000-0000-0000-000000000010");
        var first = new HQDiagnostics.SourceIdentity(source, "raw", "raw:100", 0, 64, 0, 48_000);
        var continuation = new HQDiagnostics.SourceIdentity(source, "raw", "", 0, 64, 0, 48_000);
        var replacement = new HQDiagnostics.SourceIdentity(source, "raw", "raw:200", 0, 64, 0, 48_000);

        HQDiagnostics.registerSource(first);
        HQDiagnostics.registerSource(continuation);
        assertEquals("raw:100", onlySource(HQDiagnostics.snapshot()).get("group"));

        HQDiagnostics.registerSource(replacement);
        assertEquals("raw:200", onlySource(HQDiagnostics.snapshot()).get("group"));
    }

    @Test
    void logicalSyncUsesCanonicalContentBase() {
        HQDiagnostics.setEnabled(true);

        UUID one = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID two = UUID.fromString("00000000-0000-0000-0000-000000000022");
        var id1 = new HQDiagnostics.SourceIdentity(one, "finite", "finite:logical", 0, 64, 0, 48_000, 10.000);
        var id2 = new HQDiagnostics.SourceIdentity(two, "finite", "finite:logical", 2, 64, 0, 48_000, 10.040);

        HQDiagnostics.registerSource(id1);
        HQDiagnostics.registerSource(id2);
        HQDiagnostics.recordBatch(List.of(
            sample(id1, "playing", 0.100, 0, 64, 0, 0, 64, 0, 0, 1.0f, 1.0f),
            sample(id2, "playing", 0.060, 2, 64, 0, 2, 64, 0, 0, 1.0f, 1.0f)
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) HQDiagnostics.snapshot().get("groups");
        Map<String, Object> group = groups.stream()
            .filter(row -> "finite:logical".equals(row.get("group")))
            .findFirst()
            .orElseThrow();

        assertTrue(((Number) group.get("maxSecondsOffsetSpreadMs")).doubleValue() >= 39.0);
        assertTrue(((Number) group.get("maxLogicalOffsetSpreadMs")).doubleValue() < 0.1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> onlySource(Map<String, Object> snapshot) {
        List<Map<String, Object>> sources = (List<Map<String, Object>>) snapshot.get("sources");
        assertEquals(1, sources.size());
        return sources.getFirst();
    }

    private static HQDiagnostics.ChannelSample sample(
        HQDiagnostics.SourceIdentity identity,
        String state,
        double seconds,
        float requestedX,
        float requestedY,
        float requestedZ,
        float actualX,
        float actualY,
        float actualZ,
        int directFilter,
        float directGain,
        float directGainHF
    ) {
        return new HQDiagnostics.ChannelSample(
            identity,
            state,
            4,
            0,
            0L,
            seconds,
            0.0,
            requestedX,
            requestedY,
            requestedZ,
            actualX,
            actualY,
            actualZ,
            directFilter,
            directGain,
            directGainHF
        );
    }
}
