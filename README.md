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

M1G progressive decode/rendering is **prepared but not started**.

Current preparation branch:

`codex/m1g-preparation`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 M1F candidate JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

The finalized-M1F documentation head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` also passed run `34763711105` on both targets, and the owner-requested fresh rerun passed both target jobs again.

Focused real-Minecraft M1F transport acceptance is **not recorded**. CI/component proof is not runtime proof.

The current finite architecture is:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> server-authoritative finite playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded sliding client encoded RAM
    -> M1G progressive decoder/converter worker
    -> bounded mono PCM
    -> positional speaker renderer
```

Read `docs/HANDOFF-2026-09-13-PRE-M1G.md` and `docs/PRE-M1G-PREPARATION.md` before any M1G implementation.

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

Protocol v5 uses bounded client-requested ranges rather than whole-song transfer.

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

## M1G preparation

M1G will add progressive MP3/common-WAV decoding, bounded mono PCM, and actual positional rendering on top of M1F.

Exact source recheck found that modern `HQFiniteMediaClient` is transport-only, which is the clean M1G insertion point. Do not route modern prepared playback back through the inherited complete-file/whole-decoded-track finite classes.

The project already packages JLayer `1.0.1.4`; inherited live MP3 code proves frame-by-frame JLayer use is available in the shipped dependency path.

Before M1G code starts, the owner must choose three documented design gates:

- Minecraft `AudioStream`/SoundManager renderer vs direct Channel/OpenAL queue;
- server-normalized WAV layout vs client progressive WAV parsing;
- preserve source sample rate vs normalize modern finite PCM to 48 kHz.

See `docs/PRE-M1G-PREPARATION.md` for tradeoffs and the complete acceptance plan.

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
- **M1G:** progressive MP3/common-WAV decode + audible renderer — prepared, not started.
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

1. `docs/HANDOFF-2026-09-13-PRE-M1G.md`
2. `docs/PRE-M1G-PREPARATION.md`
3. `docs/M1F-FINALIZATION-2026-09-13.md`
4. `docs/CURRENT-STATE.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ROADMAP.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/LUA-API.md`
11. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current finalization/preparation records.
