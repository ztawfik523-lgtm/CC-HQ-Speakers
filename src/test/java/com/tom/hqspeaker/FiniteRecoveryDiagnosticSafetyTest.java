package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FiniteRecoveryDiagnosticSafetyTest {
    @Test
    void rendererCloseCancellationIsClassifiedAsRecoveryBeforeDecoderFailure() throws Exception {
        String source = Files.readString(Path.of(
            "src", "main", "java", "com", "tom", "hqspeaker", "client", "HQFiniteMediaClient.java"));

        int method = source.indexOf("private static void decoderFailed");
        int rendererClose = source.indexOf("stream != null && stream.closed() && !stream.reachedEof()", method);
        int recovery = source.indexOf("requestAuthoritativeRejoin(session", rendererClose);
        int decoderFailure = source.indexOf("HQDiagnostics.decoderFailure(session.begin.source())", method);

        assertTrue(method >= 0 && rendererClose > method && recovery > rendererClose && decoderFailure > recovery,
            "renderer-close cancellation must rejoin without incrementing the decoder-failure metric");
    }
}
