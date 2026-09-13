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

## Finite files

Final core target:

- MP3 / MPEG Layer III
- common WAV

Later gated extension:

- native FLAC only after its complete analyzer/decode/seek/package/runtime path is proven

Finite playback uses a server-owned timeline for duration, position, pause/resume, seek, loop, volume, and EOF.

### Current M1F transport

M1F now implements demand-driven finite transport:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> authoritative server playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded temporary client encoded RAM
```

The modern prepared path no longer downloads the whole song into client `.part/.media` files.

Current source/test/CI checkpoint:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI run `34731827907` passed NeoForge 21.1.247 and 21.1.248, including tests and package verification.

This is not Minecraft runtime proof.

M1G has not started. M1G will attach the progressive MP3/common-WAV decoder, bounded mono PCM queue, and positional renderer to the M1F encoded window.

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

The old project-specific prototype command `audioPlayStaged()` was removed in M1F. Staging remains import plumbing only.

See `docs/LUA-API.md` for the programming reference.

## HQ raw/feed audio

HQ `speakPCM` remains an open-ended producer feed with bounded backpressure and separate `hqspeaker_audio_empty` pacing. It does not pretend to have finite duration or arbitrary seek.

## Current milestone sequence

- **M1E:** server-authoritative finite timeline — source/tests/CI implemented; final manual Minecraft PASS was skipped by owner decision and must not be claimed.
- **M1F:** bounded client-requested encoded range transport — source/test/CI complete.
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

CI targets Java 21 on both supported NeoForge versions and verifies packaged metadata/mixins/dependencies plus the bundled ComputerCraft ROM module.

## Documentation

Read current development docs in this order:

1. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1F.md`
3. `docs/LUA-API.md`
4. `docs/CURRENT-STATE.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/M1E-SERVER-AUTHORITY.md`
8. `docs/M1E-FINITE-STREAMING-DESIGN.md`
9. `docs/ROADMAP.md`
10. `docs/KNOWN-ISSUES.md`
11. `docs/TESTING.md`
12. `docs/FUTURE-CLEANUP.md`

Older dated handoffs/preparation docs preserve history but do not override current source/CI and the M1F checkpoint.
