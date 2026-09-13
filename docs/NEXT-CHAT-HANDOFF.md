# CC:HQ Speakers — next-chat handoff

Date: 2026-09-14

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

Source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build/tests/package verification/artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed both targets in CI `34780519972`. A later 2026-09-14 full repository/source/docs audit reconciled current documentation and recorded new findings without changing implementation.

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

## Audit findings which are documented but not fixed

### KI-053 — same-anchor STATE/window reset

An ordinary STATE can reset an already-slid encoded window back to an unchanged coarse anchor while preserving the existing decoder epoch. The live decoder cursor can therefore end up ahead of the reset window. Any fix must preserve the separate rule that semantic seek recreates decoder state even when the selected anchor byte is unchanged.

### KI-054 — shutdown deletion retry and registry lifetime

`MediaAssetStore.close()` clears completed-entry bookkeeping before attempting completed-file deletion. A failed shutdown deletion is therefore not retained for a later `close()` retry, although next-start orphan pruning normally recovers the managed file.

`ServerMediaAssets.closeServer()` removes the stopped server from its static registry only after `store.close()` succeeds. If that close throws, registry removal is skipped; the current server-stop hook logs the exception and does not schedule another retry. In a long-lived JVM/integrated-server restart scenario, the stopped server/services can therefore remain reachable until process exit or another explicit successful close.

### KI-055 — evidence/script mismatch

There is no real-MP3 progressive JLayer integration test across range-window sliding/starvation and no focused `FinitePcmAudioStreamTest` in the current client test tree.

`scripts/m1d_media_analysis_test.lua` is historical and expects the old broad M1D prepared format surface. `m1_player_test.lua` / `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern prepared path.

The audit did not modify Java/Lua/test scripts.

## Remaining architecture question before loop implementation

Ask the owner to choose the loop-wrap rejoin policy before implementing loop behavior:

- **L1 — client EOF refresh:** local physical EOF requests fresh authoritative STATE. Cleanest server-authority model; may have roundtrip/prebuffer gap.
- **L2 — server wrap STATE:** server detects canonical wrap and proactively projects STATE. Potentially tighter boundary; more server wrap/fanout state.
- **L3 — client local modulo/restart:** client predicts/restarts locally and reconciles later. Lowest latency; adds client drift/reconciliation state and weakens authority purity.

Do not silently select one.

After the owner chooses, KI-053 must also be resolved and the missing deterministic coverage added before focused M1G runtime acceptance.

## Evidence boundary

```text
M1E source/test/CI: complete
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: complete
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G real-MP3 progressive integration coverage: incomplete
M1G focused renderer-adapter coverage: incomplete
M1G loop-wrap architecture: owner choice required
M1G audible Minecraft acceptance: unrecorded
```

## M1H boundary

M1H still owns full late-entry discovery, proactive out-of-range cleanup, leave/return rejoin, dimension/world/resource-reload recovery, robust general underrun recovery, and final VS2 moving-listener lifecycle.

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
