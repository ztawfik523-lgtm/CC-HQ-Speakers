# Runtime acceptance — protocol v10

Updated: 2026-09-27

## Goal

Use one master acceptance against the current release JAR. Built-in diagnostics judge real client/Minecraft/OpenAL behavior automatically; the operator only performs physical actions.

Chosen release-test scope: **singleplayer + Sable/Aeronautics + Sound Physics Remastered**. Dedicated-server/multiplayer and VS2 are intentionally outside this acceptance scope.

## Current candidate

Runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Runtime-tested candidate CI: `36238699768` — PASS  
Runtime-tested artifact: `10905071824`  
Runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Master-runner checkpoint: `7b8e4d4a095611bf89d05726c4824a95edcea3ba`  
Master-runner CI: `36285305761` — PASS  
Current source/recheck checkpoint: `87d08d3a62857dfeba16756591560eaa0e62d193`  
Current source/recheck CI: `36293523518` — PASS  
Current source/recheck artifact: `10922887347` — `hqspeaker-neoforge-21.1.247`  
Build baseline: NeoForge **21.1.247 only**.

Do not build a separate 21.1.248 JAR.

## Current runtime status

The latest rerun used the current 21.1.247 candidate JAR.

- R1-R9 passed again.
- C1 Sable passed cleanly.
- C3 radio/membership passed with direct SPR evidence and an 8-speaker rerun.
- C4 RAW passed at 8 speakers.
- C4 finite no longer shows the old ~2.2-second range-admission stall. Eight real channels started within about 58.7 ms and finite catch-up reconstructs to about 3.9 ms alignment. The old 81.29 ms failure was a diagnostic mistake caused by treating OpenAL's streamed-buffer-relative offset as an absolute song clock.
- C2 was skipped because no usable ordinary-world wall was available during the run.
- A8/A9/A18 were interrupted only by a missing `shorten()` helper in the runner's expected-error logger.

The branch now contains corrected acceptance logic plus standalone probes for the only remaining focused evidence. No product/JAR change is currently required by these results.

## Built-in diagnostic surface

The release JAR exposes dormant:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

SPR diagnostics now distinguish actual `processSound` calls from later `setEnvironment` writes and record the processed HQ position/category/sound id plus reflected-position output when present. The observation is client-only and scoped to `hqspeaker:hq_audio_source`.

## Target-check corrections required before the next acceptance

### C1 Sable

Correction is implemented. C1 now permits listener relevance leave/rejoin history while still requiring requested movement, actual OpenAL movement, final PLAYING state and no decoder failure.

### C2 Sound Physics

Use a **normal Minecraft-world wall**, not a Sable wall.

The runner now proves the real SPR `processSound` path with one endpoint: open air first, then a fresh restart behind a normal-world wall. The speaker/computer may stay on a parked Sable contraption. Live reprocessing is not a release gate when SPR's own moving-sound reevaluation is disabled.

### C3 radio

C3 now also proves grouped radio sources enter SPR. Its test name was intentionally changed, so an old `--resume` log will not reuse the previous C3 PASS.

### C4 scale

The client-side admission fix is runtime-proven to remove the old ~2.2-second outlier. Finite sync now accepts small endpoint start delays when the later endpoint catches up to the shared media position. The verdict uses finite media-zero alignment, with a separate 1-second startup-stall guard.

Finite and RAW remain independent subchecks. RAW already has an 8-speaker PASS; the standalone finite probe records the corrected finite verdict without rerunning RAW.

## Run

The master runner remains available:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

For the current final recheck, do **not** dismantle the 8-speaker setup just to satisfy the master's original two-speaker start condition. Use the focused scripts instead:

```
v10_rejection_recheck <mp3> <wav>
v10_c2_spr <mp3>
v10_c4_finite <mp3>
```

These cover the three automated checks interrupted by the logger bug, the remaining ordinary-world SPR proof, and the corrected 8+ finite catch-up verdict.

## Existing matrix

### Deterministic A1-A19

Frozen API, discovery/limits, staged media, native CC:T paths, finite MP3/WAV, malformed media rejection, RAW bounds/backpressure/All/At, shared finite authority, endpoint-local controls, gain/mute, shared controls, concurrency, repeated starts, stream security/metadata and final idle.

### Real-client R1-R9

Native channels; MP3/WAV renderer continuity; finite synchronization; endpoint-local client effects; continuous RAW; loop recovery/sync; listener leave/rejoin; F3+T recovery.

### Target C1-C4

1. Sable/Aeronautics source tracking;
2. Sound Physics Remastered processing;
3. grouped MP3/ICY radio + strict late membership/rerun;
4. 8+ finite + RAW scale.

## Human actions

The operator may be asked to move/rotate Sable, move behind an obstacle, connect a late radio speaker and connect enough speakers for scale.

The operator does **not** choose PASS/FAIL.

## Release meaning

A **TARGET FULL PASS** now requires the three focused rechecks above to pass. C1, C3, 8-speaker RAW and R1-R9 are already established.

See `RUNTIME-RESULTS-V10.md` and `RUNTIME-INVESTIGATION-2026-09-27.md`.
