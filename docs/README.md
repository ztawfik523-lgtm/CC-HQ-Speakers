# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both target NeoForge versions. Later audits and scope decisions changed documentation only; source remains at the green integrated checkpoint above.

Focused Minecraft M1F transport acceptance is unrecorded. Focused audible M1G Minecraft acceptance is also unrecorded.

## Current M1G items

- **KI-051:** owner policy is selected: ordinary local replay of the same media after physical EOF while authoritative state still says looping; a normal restart gap is acceptable. Source work remains.
- **KI-053 / KI-056 / KI-057:** decoder epoch/re-anchor correctness cluster. The selected direction is an explicit server-authoritative decoder/re-anchor revision rather than inferring restart intent from anchor movement.
- **KI-058 / KI-059 / KI-060:** fixed 32-block M1G core range, volume-as-gain contract, global-volume-zero hibernation, and renderer-start robustness. KI-059's product policy is selected; KI-058/KI-060 still need source work.
- **KI-055:** real-MP3 progressive integration and focused renderer-adapter deterministic coverage remain incomplete.
- **KI-061:** per-speaker staging cleanup can leave unreachable files.
- **KI-054:** shutdown media-store cleanup/lock lifetime remains a separate hardening area.

Current owner scope is recorded in `M1G-SCOPE-DECISIONS-2026-09-14.md`. Older L1/L2/L3 loop-choice and dynamic-volume-range option lists are historical and must not override it.

## Read in this order

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `M1G-SCOPE-DECISIONS-2026-09-14.md`
6. `HANDOFF-2026-09-13-M1G-START.md`
7. `M1G-DESIGN-DECISIONS-2026-09-13.md`
8. `ROADMAP.md`
9. `LUA-API.md`
10. `M1F-FINALIZATION-2026-09-13.md`
11. exact current source and CI

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

The later M1G scope record additionally selects explicit decoder/re-anchor revision semantics, a fixed 32-block core radius with volume affecting gain rather than radius, global-volume-zero transport/render hibernation, and ordinary non-gapless loop replay.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1G integrated source/tests/package: green at `957832348eaa6e497282d923f2312c9c7d7c550f`.
- M1G real-MP3 progressive integration coverage: incomplete.
- M1G focused renderer-adapter coverage: incomplete.
- M1G selected loop replay: not implemented.
- M1G audible Minecraft PASS: unrecorded.
