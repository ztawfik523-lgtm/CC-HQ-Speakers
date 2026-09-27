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
Build baseline: NeoForge **21.1.247 only**.

Do not build a separate 21.1.248 JAR.

## Current runtime status

A1-A19 and R1-R9 are already real-runtime PASS.

Attempt 7 ran all four target scenarios:

- C1 recorded FAIL, but the actual Sable movement evidence was good; the assertion incorrectly rejected expected listener leave/rejoin history;
- C2 remains unresolved because the current SPR hook saw environment writes but not a measurable open/wall difference and does not prove `processSound` ran;
- C3 radio/membership passed completely;
- C4 finite exposed a real ~2-second scale stall caused by the finite range admission/retry contract; C4 RAW was not reached.

Do not rerun the whole core merely to reproduce those target findings. Fix/isolate the affected target paths first.

## Built-in diagnostic surface

The release JAR exposes dormant:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

The current SPR diagnostic records `setEnvironment` values. Before C2 can be considered authoritative it must additionally record actual SPR `processSound` execution/results.

## Target-check corrections required before the next acceptance

### C1 Sable

C1 must judge source tracking, not generic uninterrupted audibility. Listener relevance leave/rejoin during a long movement run is allowed if:

- requested movement is >= threshold;
- actual OpenAL movement is >= threshold;
- the source recovers and ends healthy/PLAYING;
- no real decoder failure is recorded.

### C2 Sound Physics

Do not infer success merely from one `setEnvironment` write.

The next diagnostic should prove whether SPR actually invokes `processSound` for each HQ source and capture the resulting acoustic values. Test a known static-world wall and, separately if relevant, a wall made from Sable sub-level geometry.

### C4 scale

Fix finite range request admission before retesting.

The finite and RAW scale halves must report independently so a finite assertion cannot prevent RAW evidence from running.

After the finite admission fix, test exactly 8 first. If supporting more than eight simultaneous speakers is part of the release target, add an explicit >8 source-capacity check rather than inferring it from the 8-speaker result.

## Run

Normal master command remains:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

`--resume` can reuse the already-passed A/R results after the target harness/source fixes.

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
