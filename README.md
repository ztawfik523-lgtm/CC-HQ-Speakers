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

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Branch:

`codex/m1f-finalization`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 M1F candidate JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

Focused real-Minecraft M1F transport acceptance is **not recorded**. CI/component proof is not being presented as runtime proof.

The current finite architecture is:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> server-authoritative finite playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded sliding client encoded RAM
    -> M1G progressive decode/PCM/rendering
```

Read `docs/M1F-FINALIZATION-2026-09-13.md` and `docs/CURRENT-STATE.md` before continuing implementation.

## M1E finite server authority

Prepared finite playback has hardened server-owned semantics:

- successful playback starts canonical time immediately;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position, duration, pause/resume, seek, loop, volume, and natural EOF;
- server ERROR freezes position;
- client READY requests current state only;
- client ERROR is diagnostic only;
- prepared-start construction finishes before canonical session installation;
- client delivery is best-effort and isolated per recipient;
- MediaAsset final-release failures transfer to retry-safe ownership.

The final focused Minecraft M1E acceptance script was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

## M1F finite encoded transport

Protocol v5 uses bounded client-requested ranges rather than whole-song transfer.

Current implementation values:

- max range: 128 KiB;
- client encoded window: 512 KiB;
- max outstanding requests/player: 4;
- max outstanding encoded bytes/player: 512 KiB;
- range IO workers: 2;
- range IO queue: 64.

The client window is now genuinely progressive: it waits for authoritative STATE before first demand, supports arbitrary re-anchor, can slide forward while preserving unread prefetched bytes, refills past a full window, and keeps memory bounded independent of track duration.

The server validates source/asset/generation/bounds/relevance, reads ranges off-thread, retains assets while work is in flight, discards stale work, and drains/cancels range IO before media-store shutdown.

The modern prepared path has no complete-song client `.part/.media` cache, no modern CHUNK/END whole-file packets, and no project-prototype `audioPlayStaged()` route.

M1F intentionally does **not** decode/render the file. M1G owns progressive MP3/common-WAV decoding and actual audible positional rendering.

## Lua finite-file API

Recommended one-call use:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Preload/reuse helpers:

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

Staging remains import plumbing only. See `docs/LUA-API.md` for the programming reference.

## HQ raw/feed audio

HQ `speakPCM` remains an open-ended producer feed with bounded backpressure and separate `hqspeaker_audio_empty` pacing. It does not pretend to have finite duration or arbitrary seek.

## Current milestone sequence

- **M1E:** server-authoritative finite timeline — source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded by owner decision.
- **M1F:** bounded client-requested encoded range transport — source/test/CI/package complete; focused Minecraft transport acceptance unrecorded.
- **M1G:** progressive MP3/common-WAV decode + audible renderer — next, not started.
- **M1H:** dynamic listener/late-join/leave-return/underrun recovery.
- **M1I:** optional gated native FLAC.
- **M1J/K:** functional multispeaker shared clocks, then optional active-session sharing optimization.
- later milestones cover legacy migration, RAW/OpenAL hardening, final testing, SPR, live streams, and release cleanup.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

## Documentation

Read current development docs in this order:

1. `docs/M1F-FINALIZATION-2026-09-13.md`
2. `docs/M1E-FINAL-HARDENING-2026-09-13.md`
3. `docs/CURRENT-STATE.md`
4. `docs/KNOWN-ISSUES.md`
5. `docs/TESTING.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
8. `docs/M1E-FINITE-STREAMING-DESIGN.md`
9. `docs/ROADMAP.md`
10. `docs/LUA-API.md`
11. `docs/ARCHITECTURE.md`
12. `docs/FUTURE-CLEANUP.md`

Older dated milestone/handoff documents preserve checkpoint history but do not override current source, finalization evidence, or current-state docs.
