# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality programmable audio while preserving the standard CC:T speaker contract.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline / 21.1.248 compatibility

## Product direction

This mod is a **programmable audio peripheral**, not a built-in music player.

Lua decides whether audio is music, speech, alarms, notifications, ambience, soundboards, or something else. Java distinguishes sources only where their technical capabilities differ.

The normal `computercraft:speaker` remains the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain CC:T compatibility requirements.

One physical speaker remains one mono positional source.

## Current development status

M1E server-authoritative finite playback and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

M1G progressive decode/rendering has **started** on:

`codex/m1g-progressive-finite-decode`

M1G preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed NeoForge 21.1.247 and 21.1.248 including tests, package verification, and artifact upload.

Final M1F source/test candidate remains `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`, with final M1F CI `34763362365` green on both targets.

Focused real-Minecraft M1F transport acceptance is **not recorded**. M1G audible runtime acceptance is also not recorded. CI/component proof is not runtime proof.

The current finite architecture is:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> server-authoritative finite playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded sliding client encoded RAM
    -> starvation-aware decoder-worker input
    -> progressive MP3/common-WAV decoder/converter (next integration)
    -> bounded mono signed-16 PCM queue at source sample rate
    -> Minecraft AudioStream / SoundManager positional renderer (next integration)
```

Read `docs/M1G-DESIGN-DECISIONS-2026-09-13.md` and `docs/HANDOFF-2026-09-13-M1G-START.md` before continuing M1G.

## M1E finite server authority

Prepared finite playback has hardened server-owned semantics:

- successful playback starts canonical time immediately;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position, duration, pause/resume, seek, loop, volume, and natural EOF;
- server ERROR freezes position;
- client READY requests current state only;
- client ERROR is diagnostic only;
- client delivery is best-effort/per-recipient;
- MediaAsset release is retry-safe.

Final focused Minecraft M1E acceptance was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

## M1F finite encoded transport

The finalized M1F range transport uses bounded client-requested ranges rather than whole-song transfer.

Current implementation values:

- max range 128 KiB;
- client encoded window 512 KiB;
- max outstanding requests/player 4;
- max outstanding encoded bytes/player 512 KiB;
- range IO workers 2;
- range IO queue 64.

The client waits for authoritative STATE before first demand, supports arbitrary re-anchor, slides forward while retaining useful unread prefetch, and distinguishes missing data from true EOF/stale state.

The modern prepared path has no complete-song client `.part/.media` cache, no modern CHUNK/END whole-file packets, and no `audioPlayStaged()` route.

M1F intentionally does **not** decode/render the file.

## M1G implementation

The owner decisions are resolved as:

- **A1:** normal Minecraft `AudioStream` / `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout;
- **C1:** preserve source sample rate;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` compatibility;
- **E1:** coarse conservative MP3 pre-roll from an earlier existing seek point.

See `docs/M1G-DESIGN-DECISIONS-2026-09-13.md` for rationale and constraints.

The current green M1G start checkpoint implements:

- normalized `WavLayout` metadata;
- bounded `CommonWavAnalyzer` for classic common PCM/float WAV and narrow WAVEX;
- declared RIFF-container and complete-frame validation;
- `ModernFiniteMediaAnalyzer` narrowing newly prepared/local assets to MP3 + common WAV;
- `FiniteDecodeDescriptor` as the modern decoder-facing MP3/WAV contract;
- `FiniteDecodeAnchorSelector` with exact WAV frame anchors and E1 MP3 pre-roll anchors;
- `HQMediaStaging.prepareAsset(...)` using the modern acceptance gate;
- decoder-worker `FiniteEncodedInputStream` over M1F where starvation waits/refills instead of becoming EOF, with cancellation and lost-wakeup protection;
- fixed-capacity `FinitePcmQueue` with decoder backpressure and nonblocking DATA/STARVED/EOF/CANCELLED renderer states;
- deterministic tests for layout/anchor behavior and the encoded-input/PCM concurrency boundaries.

The modern client is still silent because those new primitives are not yet wired into an active progressive decoder and Minecraft renderer.

Next work is the MP3/WAV modern wire descriptor + codec-aware server STATE anchors, then client decode-epoch integration, common-WAV conversion, JLayer progressive MP3, and the A1 positional renderer.

Do not route modern prepared playback back through inherited complete-file/whole-decoded-track finite classes.

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
- **M1G:** progressive MP3/common-WAV decode + audible renderer — **in progress**.
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

1. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1G-START.md`
3. `docs/CURRENT-STATE.md`
4. `docs/TESTING.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/PRE-M1G-PREPARATION.md` for historical tradeoff analysis only
7. `docs/M1F-FINALIZATION-2026-09-13.md`
8. `docs/VERIFIED-FACTS.md`
9. `docs/ROADMAP.md`
10. `docs/LUA-API.md`
11. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current M1G records.
