# Known issues and risk register

Updated: 2026-09-28

Only unresolved release-target evidence and concrete deferred risks are listed here.

## Runtime blockers / open investigations

### KI-RUNTIME-005 — 8+ finite range-admission stall

Attempt 7's 8-speaker finite group had seven channel starts around 115-145 ms and one around 2334 ms, yielding 2219.15 ms start skew.

**Source status:** fix implemented. The client now shares the server's four-request per-player range budget across all local finite endpoints and schedules those slots fairly instead of letting every endpoint independently submit two requests.

The protocol and server bounds stay unchanged. The old 2-second request timeout remains only for true loss/recovery.

**Runtime status:** the latest 8-speaker rerun removed the old ~2.2-second fingerprint; real channel-start spread was about 58.7 ms. A focused standalone C4 finite rerun remains only to record the corrected catch-up-alignment verdict.

### KI-RUNTIME-008 — v11 live Sound Physics/acoustic integration needs runtime proof

Attempt 7 only proved that an SPR environment-write hook fired; the Sable wall used in that run was not valid evidence for ordinary SPR world geometry.

**Source status:** protocol v11 now contains the HQ-only movement-gated refresh scheduler, progressive direct occlusion, smoothing, reflection stabilization and private per-source EFX isolation. Exact SPR 1.21.1-1.5.1 source was rechecked for the targeted method/field layout. Global SPR "Update Moving Sounds" is intended to remain OFF.

**Remaining work:** run the focused v11 C2 script on normal Minecraft ground. The same continuously-playing source must reprocess as the listener moves behind the wall, show measurable occlusion, execute progressive probes and prove that a private HQ direct filter is active.

### KI-RUNTIME-009 — >8 Minecraft streamed-source capacity

Separate from KI-RUNTIME-005, Minecraft's streamed-source pool may become a real limit above eight simultaneous HQ streamed sounds.

Attempt 7 does **not** prove this limit caused the eight-speaker failure; the range-admission timeout explains that run better. Test >8 only after KI-RUNTIME-005 is fixed.

### KI-SPR-010 — multispeaker acoustic isolation runtime proof

The architecture reevaluation is complete. The current renderer keeps Minecraft playback ownership, uses the new HQ refresh scheduler, imports only the accepted progressive/smoothing/reflection behavior, and isolates HQ direct/send filters per OpenAL source. It does not restore the old whole-file transport or old scheduler.

Remaining risk is runtime verification with simultaneous speakers, especially proving distinct private filter state and no cross-speaker muffling contamination.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback still keeps independent endpoint decoders/renderers. This is not yet proven to be the cause of the attempt-7 scale failure. Profile only after the admission defect is fixed.

### KI-RELEASE-010 — v11 integrated acceptance incomplete

Historical v10 R1-R9, C1, C3, rejection checks, C4 RAW and corrected C4 finite evidence are established. Protocol v11 changes gain/range transport and SPR behavior, so focused v11 runtime rechecks are required before release acceptance.

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
