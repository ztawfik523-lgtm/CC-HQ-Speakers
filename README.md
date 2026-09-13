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

## Current development status

The project is currently on an **M1E/M1F reevaluation hold**.

The core architecture remains:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> authoritative server playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded temporary client encoded RAM
    -> later progressive decode/PCM/rendering
```

Current Java/source head:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI run `34731827907` passed NeoForge 21.1.247 and 21.1.248, including tests/package verification/artifact upload.

That evidence remains valid, but the earlier labels `M1E finalized` and `M1F source/test/CI complete` are superseded by the 2026-09-13 reevaluation.

The reevaluation found:

- packet-send exception paths are not fully isolated from authoritative server transitions;
- `playPrepared()` rollback can leave installed finite session state inconsistent after a thrown start;
- playback asset-reference bookkeeping can forget ownership before a failed final release succeeds;
- the M1F encoded window can re-anchor but lacks the intended sliding consume/discard API;
- current deterministic M1F tests cover only part of the original acceptance matrix.

No Java was changed during that reevaluation.

Read `docs/M1E-M1F-REEVALUATION-2026-09-13.md` before continuing implementation.

## Finite files

Final core target:

- MP3 / MPEG Layer III
- common WAV

Later gated extension:

- native FLAC only after its complete analyzer/decode/seek/package/runtime path is proven

Finite playback uses a server-owned timeline for duration, position, pause/resume, seek, loop, volume, and EOF.

The modern prepared path no longer downloads the whole song into client `.part/.media` files. Protocol v5 uses bounded client-requested encoded ranges.

M1G has **not started** and should not start until the owner chooses how to close/defer the reopened M1E/M1F issues.

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

- **M1E:** server-authoritative finite timeline — semantic design implemented; hardening/acceptance reopened; final focused Minecraft PASS skipped/unrecorded.
- **M1F:** bounded client-requested encoded range transport — architecture implemented and CI-green; acceptance/completeness reopened.
- **M1G:** progressive MP3/common-WAV decode + audible renderer — not started; currently blocked on reevaluation decision.
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

1. `docs/M1E-M1F-REEVALUATION-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`
3. `docs/CURRENT-STATE.md`
4. `docs/KNOWN-ISSUES.md`
5. `docs/TESTING.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1E-SERVER-AUTHORITY.md`
8. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/ROADMAP.md`
11. `docs/LUA-API.md`
12. `docs/ARCHITECTURE.md`
13. `docs/FUTURE-CLEANUP.md`

Older dated finalization/handoff docs preserve history but do not override current source or the reevaluation status.
