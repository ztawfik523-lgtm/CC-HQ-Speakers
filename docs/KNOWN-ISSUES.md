# Known issues and risk register

Updated: 2026-09-26

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime evidence still required

### KI-RUNTIME-001 — finite listener/recovery lifecycle

Range leave/re-enter at current playback time is runtime-confirmed by R8, and the corrected R9 now runtime-confirms a real F3+T sound-engine rebuild/rejoin with decoderFailures=0. C1 dimension leave/rejoin still has no verdict: on the current Sable contraption, changing dimension caused the Sable-hosted computer/sub-level to be torn down/re-attached, so the runner could not survive to judge the return. C1 should only be retried with a setup that can actually keep that computer/sub-level alive, or explicitly skipped as unavailable for this environment.

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

### KI-RUNTIME-009 — audible attenuation versus 32-block relevance radius

R8 confirms client range leave/rejoin behavior, but it does not grade subjective loudness. During the range action at test volume 0.55, the operator reported the speaker was inaudible at 32 blocks, faint at roughly 22 blocks, and inaudible farther away. The finite relevance radius is 32 blocks while the Minecraft sound source still uses linear attenuation, so useful audible range can end before the transport/relevance boundary. Release behavior should explicitly choose whether to keep vanilla-like falloff or extend HQ-speaker audibility.

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
