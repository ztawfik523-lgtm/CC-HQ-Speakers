# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G integrated progressive decode/render is in progress.**

Active branch:

`codex/m1g-progressive-finite-decode`

Current green integrated M1G source checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

Source CI:

`34778546164` — NeoForge 21.1.247 and 21.1.248 both passed build/tests/package verification/artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

Final M1F source/test candidate remains `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`.

Focused Minecraft M1F transport acceptance is not recorded. Focused audible M1G Minecraft acceptance is also not recorded.

## NEXT CHAT FIRST ACTION

Before making further M1G source changes, ask the owner to choose the remaining **loop-wrap rejoin architecture**:

- **L1:** client EOF refresh;
- **L2:** proactive server wrap STATE;
- **L3:** client local modulo/restart.

The full tradeoffs and stop condition are in `HANDOFF-2026-09-13-M1G-START.md` and `CURRENT-STATE.md`.

Do not silently choose one.

A1/B1/C1/D1/E1 are already resolved and should not be reopened without new substantive evidence.

## Read in this order

1. `HANDOFF-2026-09-13-M1G-START.md` — current continuation handoff and required L1/L2/L3 owner choice;
2. `M1G-DESIGN-DECISIONS-2026-09-13.md` — locked A1/B1/C1/D1/E1 decisions;
3. `CURRENT-STATE.md` — current project snapshot;
4. `TESTING.md` — deterministic evidence and remaining runtime proof;
5. `KNOWN-ISSUES.md` — active gaps;
6. `VERIFIED-FACTS.md` — current fact ledger;
7. `M1F-FINALIZATION-2026-09-13.md` — final M1F evidence;
8. `ROADMAP.md` — milestone sequence;
9. `LUA-API.md` — ComputerCraft programming surface;
10. `M1E-FINITE-STREAMING-DESIGN.md` — server-authority/transport boundary;
11. `PRE-M1G-PREPARATION.md` — historical tradeoff/source audit only;
12. exact current source and CI.

## Current integrated M1G pipeline

```text
server MediaAsset
-> canonical server playback clock/state
-> protocol v6 BEGIN decoder descriptor
-> codec-aware STATE anchor
-> M1F bounded encoded ranges
-> starvation-aware FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS category
```

Implemented in source:

- MP3/common-WAV-only modern prepared descriptor;
- normalized narrow classic/WAVEX common-WAV layout;
- exact WAV frame anchors;
- E1 conservative MP3 pre-roll anchors;
- starvation-aware progressive encoded input;
- bounded mono-S16 PCM queue;
- progressive common-WAV conversion;
- progressive JLayer MP3 decoding and pre-target discard;
- decoder-epoch cancellation/replacement;
- bounded prebuffer/catch-up toward the authoritative server clock;
- nonblocking Minecraft `AudioStream` integration;
- positional `SoundSource.BLOCKS` renderer;
- local pause/resume/volume projection;
- old decoder/PCM/renderer cancellation on seek/replacement/stop.

The modern path does **not** use the inherited complete-file JavaSound/mp3spi bridge.

## Locked decisions

- **A1:** Minecraft `AudioStream` / normal `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout;
- **C1:** preserve source sample rate while output representation is mono S16;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` compatibility;
- **E1:** coarse conservative MP3 pre-roll from an earlier analyzed seek point.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded by explicit owner decision.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1G integrated source/tests/package: green at `957832348eaa6e497282d923f2312c9c7d7c550f`.
- M1G loop-wrap architecture: owner choice required.
- M1G audible Minecraft PASS: unrecorded.
- Green CI is not runtime proof.

## Historical context

Older M1E/M1F reevaluation/preparation/handoff documents preserve their checkpoint history. `PRE-M1G-PREPARATION.md` preserves the pre-choice state and does not override the locked M1G decisions or current integrated source.