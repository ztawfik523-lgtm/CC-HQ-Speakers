# Known issues and risk register

Updated: 2026-09-26

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime evidence still required

### KI-RUNTIME-001 — finite listener/recovery lifecycle

Range leave/re-enter at current playback time is now runtime-confirmed by R8. F3+T reached the real sound-engine rebuild/rejoin path in attempt 4, but a harness timing ambiguity and diagnostic misclassification required fixes, so R9 still needs one clean rerun. The selected dimension leave/rejoin scenario remains pending.

### KI-RUNTIME-003 — Sable movement

All HQ positional paths use Sable -> VS2 -> static resolution. The selected release target requires actual Sable/Aeronautics translation/rotation to be observed by the built-in diagnostics. VS2 runtime validation is intentionally outside this release target.

### KI-RUNTIME-005 — finite multispeaker

The 2-speaker real-client finite synchronization and endpoint-local control checks passed in R4-R5. Only the 8+ speaker finite/RAW scale stress remains as release-target evidence.

### KI-RUNTIME-006 — MP3/ICY radio

If radio is included in final target acceptance, verify grouped radio, metadata, strict snapshot late membership/rerun and sustained client playback with a direct MP3/ICY URL.

### KI-RUNTIME-007 — RAW continuation

The producer-fed RAW continuation fix is now runtime-confirmed by R6: later PCM continued on the existing Minecraft/OpenAL streaming channel without recreating it. The remaining RAW evidence is the 8+ speaker portion of C6 scale stress.

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
