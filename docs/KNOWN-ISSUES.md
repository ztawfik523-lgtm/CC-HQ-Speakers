# Known issues and risk register

Updated: 2026-09-27

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-RUNTIME-003 — C1 Sable harness assertion

Attempt 7 produced strong Sable tracking evidence: requested and actual OpenAL movement matched over roughly 52-53 blocks, both sources ended PLAYING and no decoder failure occurred.

The runner failed C1 because it reused the generic continuous-playback health assertion and saw one historical PLAYING->STOPPED transition while the long movement test crossed the 32-block listener relevance boundary. Repeated BEGINs for the same playback/generation confirm leave/rejoin activity.

Fix the C1 assertion; do not change working Sable movement code for this result.

### KI-RUNTIME-005 — 8+ finite range-admission stall

Attempt 7's 8-speaker finite group had seven channel starts around 115-145 ms and one around 2334 ms, yielding 2219.15 ms start skew.

Current client sources can each maintain 2 in-flight finite range requests, while `FiniteRangeReadService` admits only 4 outstanding requests / 512 KiB per player. Server `OVER_LIMIT` currently sends no rejection/deferral signal, so the client waits for its 2-second request timeout before retrying.

This is a concrete release blocker for the scale target.

### KI-RUNTIME-006 — 8+ RAW not yet executed

Attempt 7 failed inside the finite half of C4, so the RAW scale half never ran. The runner should let finite and RAW scale evidence complete independently.

### KI-RUNTIME-008 — Sound Physics integration not yet proven

Attempt 7 observed SPR environment writes on HQ finite sources, but both open-air and wall direct gain/HF remained exactly 1.0000.

The current diagnostic hook observes `setEnvironment`, not actual `SoundPhysics.processSound` invocation. The user's later visual-ray observation happened after the radio had already started, and SPR 1.21.1 defaults moving-sound reevaluation off, so that observation alone is not conclusive.

Next evidence must record actual SPR processing/ray evaluation and isolate normal-world geometry from Sable sub-level geometry.

### KI-RUNTIME-009 — >8 Minecraft streamed-source capacity

Separate from KI-RUNTIME-005, Minecraft's streamed-source pool may become a real limit above eight simultaneous HQ streamed sounds.

Attempt 7 does **not** prove this limit caused the eight-speaker failure; the range-admission timeout explains that run better. Test >8 only after KI-RUNTIME-005 is fixed.

### KI-SPR-010 — integration architecture reevaluation

The historical `cchq-soundphysics-compat` implementation contains substantial validated acoustic work, but it targets an older HQ transport and intercepts old whole-file payloads while excluding current `*_STREAM` and `PCM_S16LE` paths.

The current fork can change its own renderer architecture, so integration should be reevaluated for simplicity and correctness rather than mechanically ported.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback still keeps independent endpoint decoders/renderers. This is not yet proven to be the cause of the attempt-7 scale failure. Profile only after the admission defect is fixed.

### KI-RELEASE-010 — integrated acceptance incomplete

A1-A19 and R1-R9 are runtime-passed and C3 is target-passed, but C1/C2/C4 still require corrected evidence/fixes.

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
