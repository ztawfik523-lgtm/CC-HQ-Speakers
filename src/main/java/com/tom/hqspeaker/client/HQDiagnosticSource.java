package com.tom.hqspeaker.client;

import com.tom.hqspeaker.diagnostics.HQDiagnostics;

/** Marker implemented only by HQ-owned Minecraft sound instances. */
interface HQDiagnosticSource {
    HQDiagnostics.SourceIdentity hqspeaker$diagnosticIdentity();
}
