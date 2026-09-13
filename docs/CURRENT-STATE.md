# Current state

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, package verification, and artifact upload. Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

A full repository/source/docs audit on 2026-09-14 reconciled stale current documentation and recorded additional findings. A second source-grounded pass refined the shutdown/lifecycle wording and runtime-starvation acceptance wording. No implementation change was made by either pass.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance remains unrecorded.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Locked M1G decisions

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer.
- B1: server-normalized common-WAV layout.
- C1: preserve source sample rate.
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support.
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Integrated source

M1G source now includes the MP3/common-WAV prepared gate, protocol-v6 decode descriptor, exact WAV/E1 MP3 anchors, starvation-aware `FiniteEncodedInputStream`, bounded `FinitePcmQueue`, progressive WAV conversion, progressive JLayer MP3 decoding, decoder-epoch cancellation, bounded prebuffer/catch-up, `FinitePcmAudioStream`, and positional `FiniteSpeakerSound` through `SoundSource.BLOCKS`.

Pause/resume/volume use the Minecraft channel-control path. Seek/replacement/stop discard old decoder/PCM/renderer state. Temporary PCM starvation becomes bounded silence rather than fake EOF.

## Open findings from the 2026-09-14 audit

- **KI-053:** an ordinary STATE whose coarse anchor is unchanged can reset an already-slid client encoded window without restarting the existing decoder epoch. That can rewind useful range state behind the decoder cursor. Semantic seek must still restart codec state even when the coarse anchor byte is unchanged.
- **KI-054:** `MediaAssetStore.close()` clears completed-entry bookkeeping before shutdown deletion attempts, so a failed shutdown deletion is not retained for a later `close()` retry. Because `ServerMediaAssets.closeServer()` removes its server-registry entry only after `store.close()` succeeds, such a failure also skips registry removal; the current server-stop hook logs the exception without scheduling another retry. Next-start orphan pruning normally recovers the managed file, but a stopped server/services object can remain reachable in a long-lived JVM until process exit or another explicit close retry.
- **KI-055:** current evidence is uneven. There is no real-MP3 progressive JLayer fixture test across window progression/starvation and no focused `FinitePcmAudioStreamTest`; several Lua scripts are historical/legacy rather than modern prepared-path acceptance.

See `KNOWN-ISSUES.md` and `TESTING.md`. These findings are documented only; they have not been fixed.

## Remaining owner decision — loop-wrap rejoin

The server clock already owns canonical looping. After local physical EOF during a looping session, choose one architecture before implementing loop-wrap behavior:

- **L1:** client EOF requests fresh authoritative STATE. Strongest authority model; possible roundtrip/prebuffer gap.
- **L2:** server detects canonical wraps and proactively projects STATE. Potentially tighter boundary; more server wrap/fanout state.
- **L3:** client predicts/restarts locally and reconciles later. Lowest latency; weaker server-authority purity and added drift/reconciliation state.

Do not choose L1/L2/L3 silently.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F focused Minecraft transport acceptance: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G real-MP3 progressive integration coverage: incomplete
M1G focused FinitePcmAudioStream coverage: incomplete
M1G loop-wrap rejoin: unresolved owner choice
M1G audible Minecraft PASS: unrecorded
```

Full late-entry/leave-return/dimension-reload/general-underrun/final-VS2 lifecycle remains M1H.

## Read order

1. `HANDOFF-2026-09-13-M1G-START.md`
2. `M1G-DESIGN-DECISIONS-2026-09-13.md`
3. `CURRENT-STATE.md`
4. `KNOWN-ISSUES.md`
5. `TESTING.md`
6. `VERIFIED-FACTS.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `M1F-FINALIZATION-2026-09-13.md`
10. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
