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

Historical real-runtime evidence is unchanged: A1-A19 and R1-R9 are PASS, attempt 7 had C3 PASS, C1 was a harness false-negative, C2 used insufficient SPR evidence/Sable geometry, and C4 finite exposed the ~2.2-second admission stall before RAW could run.

The current branch now contains the recheck fixes:

- C1 judges Sable movement and final health instead of uninterrupted relevance history;
- finite range requests are shared fairly under the server's 4-request per-player budget;
- C4 finite and RAW execute independently;
- direct SPR `processSound` evidence is captured for finite, radio and RAW;
- C2 uses a normal-world wall and separately measures long-running refresh vs a fresh behind-wall start.

These changes are **CI-passed but pending runtime acceptance**. Use artifact `10922887347`; do not substitute the older runtime-tested JAR for the recheck because it does not contain the new harness/diagnostic/admission fixes.

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

The runner now proves the real SPR `processSound` path and performs two behind-wall measurements: first while the same HQ sound keeps playing, then after restarting behind the same wall. If restart occlusion works but the live source never gets reprocessed, that is direct evidence that HQ needs a client-only long-running SPR refresh path.

### C3 radio

C3 now also proves grouped radio sources enter SPR. Its test name was intentionally changed, so an old `--resume` log will not reuse the previous C3 PASS.

### C4 scale

The client-side admission fix is implemented. Exactly 8 finite endpoints must confirm that one endpoint no longer waits around the 2-second request timeout.

Finite and RAW are independent subchecks, so RAW evidence is recorded even if finite fails.

## Run

Normal master command remains:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

`--resume` should be used with the existing attempt-7 log for the next run. It reuses the already-passed A/R results. C1/C2/C4 previously failed and will rerun; C3's renamed test deliberately forces a fresh radio + SPR-path run.

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

A **TARGET FULL PASS** still requires all selected target checks to pass after the corrections above. Current status is not release-ready.

See `RUNTIME-RESULTS-V10.md` and `RUNTIME-INVESTIGATION-2026-09-27.md`.
