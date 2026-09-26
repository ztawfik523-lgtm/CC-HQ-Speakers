package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticOpenALSafetyTest {
    @Test
    void diagnosticsNeverQueryWriteOnlyDirectFilterSourceProperty() throws Exception {
        String diagnostics = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "client", "HQAudioDiagnosticsClient.java"));

        assertFalse(diagnostics.matches("(?s).*alGetSourcei\\s*\\([^;]*AL_DIRECT_FILTER.*"),
            "OpenAL Soft rejects AL_DIRECT_FILTER as a source query property");
    }

    @Test
    void soundPhysicsEvidenceComesFromSprApplicationHook() throws Exception {
        Path mixin = Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "mixin", "client",
            "SoundPhysicsEnvironmentMixin.java");
        String source = Files.readString(mixin);

        assertTrue(source.contains("setEnvironment"));
        assertTrue(source.contains("HQAudioDiagnosticsClient.soundPhysicsApplied"));
    }
}
