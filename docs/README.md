# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both target NeoForge versions. A later 2026-09-14 full audit reconciled current docs and recorded KI-053 through KI-055 without changing source.

Focused Minecraft M1F transport acceptance is unrecorded. Focused audible M1G Minecraft acceptance is also unrecorded.

## Current open M1G items

- **KI-051:** owner must choose loop-wrap rejoin architecture: L1 client EOF refresh, L2 proactive server wrap STATE, or L3 client local modulo/restart.
- **KI-053:** ordinary same-anchor STATE can reset a slid encoded window without restarting the existing decoder epoch.
- **KI-055:** real-MP3 progressive integration and focused renderer-adapter deterministic coverage remain incomplete.

KI-054 tracks a separate shutdown `MediaAssetStore.close()` deletion-retry gap.

No source fix was made during the 2026-09-14 docs audit.

## Read in this order

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

Historical preparation/milestone/handoff documents preserve their checkpoint history and do not override current records.

## Current integrated M1G pipeline

```text
server MediaAsset
-> canonical server playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded encoded ranges / sliding window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

The modern path does not use the inherited complete-file JavaSound/mp3spi bridge.

## Locked decisions

A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll are already selected.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1G integrated source/tests/package: green at `957832348eaa6e497282d923f2312c9c7d7c550f`.
- M1G real-MP3 progressive integration coverage: incomplete.
- M1G focused renderer-adapter coverage: incomplete.
- M1G loop-wrap architecture: owner choice required.
- M1G audible Minecraft PASS: unrecorded.
