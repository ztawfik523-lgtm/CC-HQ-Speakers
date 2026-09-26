# Known issues and risk register

Updated: 2026-09-26

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime evidence still required

### KI-RUNTIME-001 — finite listener/recovery lifecycle

The code and diagnostics exist, but the current candidate still needs in-game proof for leave/re-enter at current playback time, F3+T sound-engine rebuild recovery and the selected dimension leave/rejoin scenario.

### KI-RUNTIME-003 — Sable movement

All HQ positional paths use Sable -> VS2 -> static resolution. The selected release target requires actual Sable/Aeronautics translation/rotation to be observed by the built-in diagnostics. VS2 runtime validation is intentionally outside this release target.

### KI-RUNTIME-005 — finite multispeaker

Current diagnostics can measure real channel starts, canonical playback drift, PCM feed spread, endpoint-local gain/mute/stop and recovery. The current JAR still needs the in-game 2-speaker core and 8+ speaker stress pass.

### KI-RUNTIME-006 — MP3/ICY radio

If radio is included in final target acceptance, verify grouped radio, metadata, strict snapshot late membership/rerun and sustained client playback with a direct MP3/ICY URL.

### KI-RUNTIME-007 — RAW continuation

The producer-fed RAW continuation bug was fixed by waking the existing Minecraft/OpenAL streaming channel when later PCM arrives. CI is green and diagnostics now detect mid-stream stop/starvation, but the fix still needs current in-game confirmation in R6 and scale stress.

### KI-RUNTIME-008 — Sound Physics Remastered

The diagnostics can observe Sound Physics direct-filter processing and measurable filter changes. The current candidate still needs the prepared open-air/behind-wall in-game check.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback intentionally keeps independent endpoint decoders/renderers. The 8+ speaker master test is the release-target evidence. Shared finite decode fan-out remains deferred unless real profiling demonstrates a problem.

### KI-RELEASE-010 — integrated acceptance

The final target-scope master run has not yet been recorded as PASS. CI alone is insufficient.

### KI-REPO-011 — release branch hygiene

The product branch is `codex/m1j-multispeaker`; GitHub default `main` remains historical until explicitly promoted/merged for release.

## Non-blocking maintenance note

NeoForge currently emits deprecation warnings for the `EventBusSubscriber.Bus.MOD` annotation form used by the diagnostic sound-engine reload hook. It compiles and CI passes on the 21.1.247 baseline. This is maintenance debt, not a demonstrated runtime defect.

## Explicitly not release blockers for the selected target

- dedicated-server/multiplayer runtime behavior;
- VS2 runtime behavior;
- a separate NeoForge 21.1.248 build/runtime pass.

## Frozen semantics, not issues

- `audioStopAt` is endpoint-local;
- grouped radio has no automatic membership;
- `isStreaming` means server-side stream ownership/request state, not guaranteed audibility;
- HLS/TS are removed;
- shared finite decode fan-out is not selected.
