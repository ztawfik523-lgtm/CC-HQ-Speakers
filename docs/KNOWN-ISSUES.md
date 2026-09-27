# Known issues and risk register

Updated: 2026-09-27

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-RUNTIME-005 — 8+ finite range-admission stall

Attempt 7's 8-speaker finite group had seven channel starts around 115-145 ms and one around 2334 ms, yielding 2219.15 ms start skew.

**Source status:** fix implemented. The client now shares the server's four-request per-player range budget across all local finite endpoints and schedules those slots fairly instead of letting every endpoint independently submit two requests.

The protocol and server bounds stay unchanged. The old 2-second request timeout remains only for true loss/recovery.

**Runtime status:** the latest 8-speaker rerun removed the old ~2.2-second fingerprint; real channel-start spread was about 58.7 ms. A focused standalone C4 finite rerun remains only to record the corrected catch-up-alignment verdict.

### KI-RUNTIME-008 — Sound Physics integration not yet proven

Attempt 7 only proved that an SPR environment-write hook fired; the Sable wall used in that run was not valid evidence for ordinary SPR world geometry.

**Source status:** direct `SoundPhysics.processSound` diagnostics are implemented and scoped only to HQ custom sounds. Radio and 8-speaker RAW already passed with direct SPR evidence. C2 is now a one-speaker open-air vs normal-world-wall restart check; the speaker may remain on a parked Sable contraption.

**Remaining work:** run the standalone C2 and prove that a fresh HQ source behind ordinary-world geometry receives measurable SPR occlusion. HQ-only live refresh is not a release requirement when SPR's own moving-sound reevaluation is disabled.

### KI-RUNTIME-009 — >8 Minecraft streamed-source capacity

Separate from KI-RUNTIME-005, Minecraft's streamed-source pool may become a real limit above eight simultaneous HQ streamed sounds.

Attempt 7 does **not** prove this limit caused the eight-speaker failure; the range-admission timeout explains that run better. Test >8 only after KI-RUNTIME-005 is fixed.

### KI-SPR-010 — integration architecture reevaluation

The historical `cchq-soundphysics-compat` implementation contains substantial validated acoustic work, but it targets an older HQ transport and intercepts old whole-file payloads while excluding current `*_STREAM` and `PCM_S16LE` paths.

The current fork can change its own renderer architecture, so integration should be reevaluated for simplicity and correctness rather than mechanically ported.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback still keeps independent endpoint decoders/renderers. This is not yet proven to be the cause of the attempt-7 scale failure. Profile only after the admission defect is fixed.

### KI-RELEASE-010 — integrated acceptance incomplete

R1-R9, C1, C3 and 8-speaker RAW are runtime-passed. The remaining focused evidence is A8/A9/A18 rejection rerun, standalone C2 ordinary-world SPR occlusion and standalone C4 finite catch-up alignment.

### KI-REPO-011 — release branch hygiene

The product branch is `codex/m1j-multispeaker`; GitHub default `main` remains historical until explicitly promoted/merged for release.

## Non-blocking maintenance note

NeoForge emits a deprecation warning for the `EventBusSubscriber.Bus.MOD` annotation form used by the diagnostic sound-engine reload hook. It compiles and runs on the 21.1.247 baseline. This is maintenance debt, not a demonstrated runtime defect.

## Explicitly not release blockers for the selected target

- dedicated-server/multiplayer runtime behavior;
- VS2 runtime behavior;
- a separate NeoForge 21.1.248 build/runtime pass.

## Frozen semantics, not issues

- `audioStopAt` is endpoint-local;
- grouped radio has no automatic membership;
- `isStreaming` means server-side stream ownership/request state, not guaranteed audibility;
- HLS/TS are removed.
