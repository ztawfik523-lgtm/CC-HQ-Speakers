# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality programmable audio while preserving the standard CC:T speaker contract.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline / 21.1.248 compatibility

## Product direction

This mod is a **programmable audio peripheral**, not a built-in music player.

Lua decides whether audio is music, speech, alarms, notifications, ambience, soundboards, or something else. Java distinguishes sources only where technical capabilities differ.

The normal `computercraft:speaker` remains the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain CC:T compatibility requirements.

One physical speaker remains one mono positional source.

## Current development status

M1E server-authoritative finite playback and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

M1G progressive decode/rendering is integrated in source on `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, packaged-mod verification, and artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

A later 2026-09-14 full repository/source/docs audit reconciled stale documentation and recorded additional correctness/evidence findings without changing implementation.

Focused real-Minecraft M1F transport acceptance is not recorded. Focused audible M1G Minecraft acceptance is also not recorded. Green CI/component proof is not runtime proof.

Current finite architecture:

```text
ComputerCraft file
-> reusable server MediaAsset
-> server-authoritative finite playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client range requests / off-thread server reads
-> bounded sliding client encoded RAM
-> starvation-aware decoder-worker input
-> progressive MP3/common-WAV decoder
-> bounded mono S16 PCM at source rate
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared engine.

## M1G current boundary

Locked choices remain:

- A1: Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1: server-normalized common-WAV layout;
- C1: preserve source sample rate;
- D1: narrow PCM/float WAVEX;
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.

Modern prepared/local support is MP3 + supported common WAV. Historical OGG/AIFF/AU support in legacy code does not define the modern prepared surface.

Open current items are tracked in `docs/KNOWN-ISSUES.md`:

- KI-051 — owner choice for audible loop-wrap rejoin: L1 client EOF refresh, L2 server wrap STATE, or L3 client local modulo/restart;
- KI-053 — same-anchor STATE can reset a slid encoded window without restarting the live decoder epoch;
- KI-054 — shutdown deletion failure loses retry bookkeeping and can also skip media-services registry cleanup;
- KI-055 — real-MP3 progressive integration and focused renderer-adapter deterministic coverage are incomplete.

No implementation fix was made during the 2026-09-14 documentation audit.

## Lua finite-file API

Recommended one-call use:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Reusable helpers:

- `prepareFile`
- `preparedInfo`
- `playPrepared`
- `releasePrepared`

Finite controls:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

See `docs/LUA-API.md`.

## Milestone sequence

- **M1E:** server-authoritative finite timeline — source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded.
- **M1F:** bounded client-requested encoded transport — source/test/CI/package + component acceptance complete; focused Minecraft transport acceptance unrecorded.
- **M1G:** progressive MP3/common-WAV decode + positional renderer — integrated in source; correctness/evidence work and loop-wrap policy remain.
- **M1H:** dynamic listener/late-join/leave-return/recovery.
- **M1I:** optional gated native FLAC.
- later milestones cover multispeaker sync/sharing, legacy migration, RAW/OpenAL hardening, final testing, SPR, live streams, and release cleanup.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

## Documentation

Read current development docs in this order:

1. `docs/CURRENT-STATE.md`
2. `docs/KNOWN-ISSUES.md`
3. `docs/TESTING.md`
4. `docs/VERIFIED-FACTS.md`
5. `docs/HANDOFF-2026-09-13-M1G-START.md`
6. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
7. `docs/ROADMAP.md`
8. `docs/LUA-API.md`
9. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current records.
