# Known issues and risk register

Updated: 2026-10-01

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-SPR-010 — multispeaker acoustic isolation runtime proof

The architecture reevaluation is complete. The current renderer keeps Minecraft playback ownership, uses the HQ refresh scheduler, imports the accepted progressive/smoothing/reflection behavior, and isolates HQ direct/send filters per OpenAL source.

Remaining risk is runtime verification with simultaneous speakers, especially proving a clear source does not inherit muffling from an occluded source.

### KI-RELEASE-010 — v11 integrated acceptance incomplete

Historical v10 A1-A19, R1-R9, C1, C3, rejection checks, C4 RAW and corrected C4 finite evidence are established. Focused v11 C2 is also PASS.

The remaining release evidence is deliberately condensed to two integrated runs on the audited candidate:

- `v11_runtime_1`: tuning/range/rejection, endpoint controls, live config reload, simultaneous private-filter isolation, F3+T recovery and 70-80 block SPR behavior;
- `v11_runtime_2`: eight-speaker finite, Sable movement, eight-speaker RAW continuation/backpressure and grouped MP3/ICY radio + metadata.

### KI-RUNTIME-009 — >8 Minecraft streamed-source capacity

Minecraft's streamed-source pool may become a real limit above eight simultaneous HQ streamed sounds.

This is separate from the old finite range-admission defect, which is fixed and runtime-rechecked at eight speakers. The selected release target guarantees eight, not >8.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback still keeps independent endpoint decoders/renderers.

This is not a demonstrated release-target problem at eight speakers. Profile only if runtime evidence shows decoder cost is actually limiting.

### KI-REPO-011 — release branch hygiene

The product branch is `codex/m1j-multispeaker`; GitHub default `main` remains historical until explicitly promoted/merged for release.

## Resolved issue retained for history

### KI-RUNTIME-008 — v11 live Sound Physics/acoustic integration — resolved

Focused C2 passed with SPR Update Moving Sounds OFF on one continuous finite source: repeated real `processSound`, progressive full/partial probes, stable private direct filter with zero private-EFX fallback, clean source continuity and measurable open-to-wall gain/HF/occlusion change.

Later cleanup did not touch the SPR scheduler/progressive/private-EFX implementation, so C2 is not repeated.

### KI-RUNTIME-005 — 8+ finite range-admission stall — resolved

Attempt 7 exposed one ~2.2-second late finite endpoint because local sessions could collectively exceed the server's four-request per-player range-admission budget.

The client was changed to share that budget fairly across local endpoints. The later eight-speaker runtime rerun removed the ~2.2-second fingerprint, and the focused corrected C4 finite test passed using catch-up alignment rather than the invalid streamed-buffer-relative offset metric.

This is no longer an open release blocker.

## Explicitly not release blockers for the selected target

- dedicated-server/multiplayer runtime behavior;
- VS2 runtime behavior;
- a separate NeoForge 21.1.248 build/runtime pass;
- >8 streamed-source guarantee.

## Frozen semantics, not issues

- `audioStopAt` is endpoint-local;
- grouped radio has no automatic membership;
- `isStreaming` means server-side stream ownership/request state, not guaranteed audibility;
- HLS/TS are removed.
