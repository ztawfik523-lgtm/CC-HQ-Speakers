# CC:HQ Speakers — next-chat handoff

Date: 2026-09-14

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

Source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build/tests/package verification/artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed both targets in CI `34780519972`. Later 2026-09-14 repository/source audits updated documentation and recorded additional findings without changing implementation.

## Current status

M1E and M1F remain complete at source/test/CI/package level. M1E final focused Minecraft acceptance was skipped/unrecorded; M1F focused Minecraft transport acceptance is unrecorded.

M1G progressive decode/render is **integrated in source**. Do not describe `HQFiniteMediaClient` as transport-only and do not route modern prepared playback back through the old complete-file bridge.

Modern finite pipeline:

```text
server MediaAsset
-> canonical server playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> M1F bounded ranges / sliding encoded window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

Locked choices A1/B1/C1/D1/E1 remain unchanged.

## Highest-priority source work — decoder epoch/reanchor correctness

Do **not** fix KI-053 in isolation.

### KI-053 — same-anchor STATE/window reset

An ordinary STATE can reset an already-slid encoded window back to an unchanged coarse anchor while preserving the existing decoder epoch. The live decoder cursor can therefore end up ahead of the reset window.

### KI-056 — expected SEEK cancellation can fail the session

`CONTROL SEEK` cancels the current decoder/PCM/renderer epoch before invalidating the worker epoch token. The cancelled worker can therefore report its expected cancellation through `decoderFailed()` while it still appears current, killing a valid client session before replacement STATE arrives.

Any implementation must invalidate stale worker identity before cancellation can wake/report.

### KI-057 — STATE conflates timeline snapshot and decoder-reanchor intent

The server sends STATE after pause/resume/seek/volume/loop and recomputes its codec anchor from canonical time. Client `anchorChanged` currently restarts decoding. Exact WAV anchors can therefore restart healthy playback after ordinary controls, while same-coarse-anchor semantic SEEK still depends on receiving the preceding CONTROL to set `restartRequested`.

Owner decision required before coding this cluster:

- **minimal v6/client repair:** less protocol churn, but semantic seek/reanchor remains more coupled to CONTROL/STATE ordering/projection;
- **explicit server-authoritative decode/reanchor revision:** protocol change (likely v7), more churn, but STATE becomes self-sufficient about when fresh codec state is required and ordinary snapshots can be non-destructive.

## Volume/render/relevance findings

### KI-058 — live volume leaves attenuation stale

The modern finite client updates sound volume/category gain but not live channel linear-attenuation distance. Target CC:T 1.120.0 explicitly refreshes `linearAttenuation(Math.max(volume, 1) * attenuationDistance)` on speaker volume changes because Minecraft leaves attenuation stale.

### KI-059 — fixed 32-block relevance vs volume 3

Modern finite server relevance is fixed at 32 blocks while normal speaker-style volume semantics allow volume up to 3, which can imply about 48 blocks of audible distance from a 16-block base.

Owner choice:

- fixed max delivery radius (simple; extra network/decode work for low-volume listeners),
- dynamic volume-aware relevance (efficient/exact; pulls late-entry/leave lifecycle forward from M1H),
- intentional 32-block cap (simple but deliberately diverges from normal high-volume distance semantics).

### KI-060 — renderer-start/silent behavior

`rendererStarted` is latched before `SoundManager.play()` proves the sound became active. Minecraft exposes `SoundInstance.canStartSilent()` for long-lived silent sounds.

Canonical volume-zero playback already keeps server time running. Owner choice for local behavior:

- start/keep the stream active while silent (`canStartSilent` style): simplest synchronization, consumes a local audio source while muted;
- defer local renderer while inaudible, then catch up and start on unmute: saves the source, adds lifecycle/catch-up complexity.

Whichever is chosen, failed/deferred renderer start must not permanently latch the session silent.

## Storage findings

### KI-061 — per-speaker staging mount leftovers

Each `HQMediaStaging` uses a fresh persistent ComputerCraft save-directory mount under `hqspeaker/staging/<uuid>`. Cleanup unmounts computers but does not clear arbitrary leftover staged files. Low-level/interrupted staging can therefore become unreachable and accumulate across speaker recreation/restarts.

Likely fix: clear the shared staging mount on whole `HQMediaStaging.cleanup()` after all computers are unmounted, not on individual detach. Add failure handling/test coverage.

### KI-054 — shutdown deletion retry and registry lifetime

`MediaAssetStore.close()` loses completed-file retry bookkeeping on shutdown deletion failure. A thrown close also skips `ServerMediaAssets` static registry removal; the server-stop hook currently only logs the failure. Lower-frequency than the M1G playback cluster, but still unresolved.

## Evidence gaps — KI-055

There is no real-MP3 progressive JLayer integration test across range-window sliding/starvation/pre-roll and no focused `FinitePcmAudioStreamTest` in the current client test tree.

`scripts/m1d_media_analysis_test.lua` is historical and expects the old broad M1D prepared format surface. `m1_player_test.lua` / `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern prepared path.

## Loop decision — KI-051

Choose loop-wrap rejoin after/alongside the corrected reanchor model:

- **L1 — client EOF refresh:** local physical EOF requests fresh authoritative STATE. Strong authority/simple server; possible roundtrip/prebuffer gap.
- **L2 — server wrap STATE:** server detects canonical wrap and proactively projects STATE. Potentially tighter boundary; more server wrap/fanout state.
- **L3 — client local modulo/restart:** client predicts/restarts locally and reconciles later. Lowest boundary latency; adds client drift/reconciliation state and weakens authority purity.

Do not silently select one.

## Recommended implementation sequence

1. Choose decoder snapshot/reanchor model, then fix KI-053/KI-056/KI-057 as one cluster with deterministic ordering tests.
2. Choose volume-zero and server-relevance semantics; fix KI-058/KI-059/KI-060 and test gain/distance/start behavior.
3. Add real-MP3 progressive and `FinitePcmAudioStream` coverage plus repeated-seek/cancellation stress.
4. Choose/implement KI-051 loop on top of the corrected reanchor mechanism.
5. Fix KI-061 staging cleanup; then KI-054 shutdown cleanup/retry hardening (or intentionally move both earlier if storage debt should be closed before runtime acceptance).
6. Re-run both NeoForge targets/package verification and focused Minecraft audible acceptance.

## Evidence boundary

```text
M1E source/test/CI: complete
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: complete
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G volume/range/start correctness: open KI-058/KI-059/KI-060
M1G staging cleanup: open KI-061
M1G real-MP3 progressive integration coverage: incomplete
M1G focused renderer-adapter coverage: incomplete
M1G loop-wrap architecture: owner choice required
M1G audible Minecraft acceptance: unrecorded
```

## M1H boundary

M1H still owns full late-entry discovery, proactive out-of-range cleanup, leave/return rejoin, dimension/world/resource-reload recovery, robust general underrun recovery, and final VS2 moving-listener lifecycle, unless the owner deliberately pulls the needed enter/leave subset forward for dynamic volume-aware relevance.

## Read order

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `HANDOFF-2026-09-13-M1G-START.md`
6. `M1G-DESIGN-DECISIONS-2026-09-13.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `M1F-FINALIZATION-2026-09-13.md`
10. exact current source and CI

Historical handoffs/preparation docs preserve earlier checkpoints and do not override current records.
