# Known issues and risk register

Updated: 2026-09-27

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-RUNTIME-003 — C1 Sable harness assertion

Attempt 7's actual Sable tracking was good: requested and actual OpenAL movement matched over roughly 52-53 blocks, both sources ended PLAYING and there were no decoder failures.

**Source status:** fixed in the acceptance runner. C1 now allows expected listener relevance detach/rejoin history and requires movement plus a healthy final PLAYING source instead of zero historical STOPPED transitions.

**Remaining work:** one clean runtime rerun to turn the previous harness false-negative into recorded PASS evidence.

### KI-RUNTIME-005 — 8+ finite range-admission stall

Attempt 7's 8-speaker finite group had seven channel starts around 115-145 ms and one around 2334 ms, yielding 2219.15 ms start skew.

**Source status:** fix implemented. The client now shares the server's four-request per-player range budget across all local finite endpoints and schedules those slots fairly instead of letting every endpoint independently submit two requests.

The protocol and server bounds stay unchanged. The old 2-second request timeout remains only for true loss/recovery.

**Remaining work:** rerun C4 exactly-8 finite. Do not call the bug closed until runtime confirms the late-source fingerprint is gone.

### KI-RUNTIME-006 — 8+ RAW not yet executed

Attempt 7 failed inside finite before RAW ran.

**Source status:** the C4 runner now executes finite and RAW as independent subchecks and records each result even if the other fails.

**Remaining work:** run C4 and obtain real 8+ RAW evidence.

### KI-RUNTIME-008 — Sound Physics integration not yet proven

Attempt 7 only proved that an SPR environment-write hook fired; the Sable wall used in that run was not valid evidence for ordinary SPR world geometry.

**Source status:** direct `SoundPhysics.processSound` diagnostics are implemented and scoped only to HQ custom sounds. C2 now uses a normal-world wall, checks live long-running behavior before restart, then restarts behind the same wall to separate stale refresh from startup processing.

Radio and RAW target checks also require direct SPR process evidence.

**Remaining work:** runtime C2 decides whether any actual product integration behavior is missing. No refresh/smoothing/diffraction system will be added unless the new evidence requires it.

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
