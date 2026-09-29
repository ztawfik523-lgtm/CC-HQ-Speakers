# Known issues and risk register

Updated: 2026-09-29

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-RUNTIME-008 — v11 live Sound Physics/acoustic integration needs runtime proof

Historical testing established the practical root cause: a long-lived HQ source was processed by SPR at start but did not update wall occlusion as listener geometry changed unless SPR's global Update Moving Sounds option was enabled.

**Source status:** protocol v11 now contains the HQ-only movement-gated refresh scheduler, progressive direct occlusion, smoothing, reflection stabilization and private per-source EFX isolation. Exact SPR 1.21.1-1.5.1 source was rechecked for the targeted method/field layout. Global SPR Update Moving Sounds is intended to remain OFF.

**Remaining work:** run the focused `v11_c2_spr` script on normal Minecraft ground. The same continuously-playing source must reprocess as the listener moves behind the wall, show measurable occlusion, execute progressive probes and prove that a private HQ direct filter is active.

### KI-SPR-010 — multispeaker acoustic isolation runtime proof

The architecture reevaluation is complete. The current renderer keeps Minecraft playback ownership, uses the HQ refresh scheduler, imports the accepted progressive/smoothing/reflection behavior, and isolates HQ direct/send filters per OpenAL source.

Remaining risk is runtime verification with simultaneous speakers, especially proving a clear source does not inherit muffling from an occluded source.

### KI-RELEASE-010 — v11 integrated acceptance incomplete

Historical v10 A1-A19, R1-R9, C1, C3, rejection checks, C4 RAW and corrected C4 finite evidence are established.

Protocol v11 changes gain/range transport and SPR behavior, so focused v11 runtime rechecks are still required before release acceptance:

- live SPR/acoustic C2;
- volume/range anchors + explicit override;
- live config reload/new-source behavior;
- finite/RAW/radio regression;
- 8-speaker finite + RAW;
- multispeaker private-filter isolation.

### KI-RUNTIME-009 — >8 Minecraft streamed-source capacity

Minecraft's streamed-source pool may become a real limit above eight simultaneous HQ streamed sounds.

This is separate from the old finite range-admission defect, which is fixed and runtime-rechecked at eight speakers. The selected release target guarantees eight, not >8.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback still keeps independent endpoint decoders/renderers.

This is not a demonstrated release-target problem at eight speakers. Profile only if runtime evidence shows decoder cost is actually limiting.

### KI-REPO-011 — release branch hygiene

The product branch is `codex/m1j-multispeaker`; GitHub default `main` remains historical until explicitly promoted/merged for release.

## Resolved issue retained for history

### KI-RUNTIME-005 — 8+ finite range-admission stall — resolved

Attempt 7 exposed one ~2.2-second late finite endpoint because local sessions could collectively exceed the server's four-request per-player range-admission budget.

The client was changed to share that budget fairly across local endpoints. The later eight-speaker runtime rerun removed the ~2.2-second fingerprint, and the focused corrected C4 finite test passed using catch-up alignment rather than the invalid streamed-buffer-relative offset metric.

This is no longer an open release blocker.

## Non-blocking maintenance note

NeoForge emits a deprecation warning for the `EventBusSubscriber.Bus.MOD` annotation form used by the diagnostic sound-engine reload hook. It compiles and runs on the 21.1.247 baseline. This is maintenance debt, not a demonstrated runtime defect.

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
