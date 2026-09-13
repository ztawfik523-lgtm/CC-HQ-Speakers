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

M1E server-authoritative finite playback is complete at the source/test/CI level.

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Exact M1E code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 final-hardening JAR SHA-256:

`da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`

The final focused Minecraft M1E acceptance script was explicitly skipped by the owner, so there is **no recorded final M1E runtime PASS**. CI is not being presented as runtime proof.

The core architecture is:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> server-authoritative finite playback state
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded temporary client encoded RAM
    -> later progressive decode/PCM/rendering
```

M1F range transport is implemented but remains provisional for its sliding-window consumer boundary and remaining deterministic acceptance work. M1G has not started.

Read `docs/M1E-FINAL-HARDENING-2026-09-13.md` and `docs/CURRENT-STATE.md` before continuing implementation.

## M1E finite server authority

Prepared finite playback now has hardened server-owned semantics:

- successful playback starts canonical time immediately;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position, duration, pause/resume, seek, loop, volume, and natural EOF;
- non-looping exact-duration seek ends;
- looping exact-duration seek wraps to zero;
- server ERROR freezes position;
- client READY requests current state only;
- client ERROR is diagnostic only;
- prepared-start construction finishes before canonical session installation;
- client delivery is best-effort and isolated per recipient;
- MediaAsset final-release failures transfer to retry-safe ownership.

A slow or broken client cannot canonically pause, rewind, end, or prevent server playback from progressing.

## Finite files

Final core target:

- MP3 / MPEG Layer III
- common WAV

Later gated extension:

- native FLAC only after its complete analyzer/decode/seek/package/runtime path is proven

The modern prepared path no longer downloads the whole song into client `.part/.media` files. Protocol v5 uses bounded client-requested encoded ranges.

M1F currently uses bounded server range IO and bounded client encoded RAM. It still needs a true sliding consume/discard encoded window and the remaining transport acceptance coverage before the M1F milestone should be called complete.

M1G will own progressive MP3/common-WAV decoding and actual audible positional rendering. Do not repair the obsolete complete-file JavaSound/mp3spi bridge merely for temporary M1F audibility.

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

- **M1E:** server-authoritative finite timeline — source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded by owner decision.
- **M1F:** bounded client-requested encoded range transport — architecture implemented; completion/acceptance still provisional.
- **M1G:** progressive MP3/common-WAV decode + audible renderer — not started.
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

1. `docs/M1E-FINAL-HARDENING-2026-09-13.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/M1E-SERVER-AUTHORITY.md`
7. `docs/M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit; M1E findings resolved, M1F findings remain relevant
8. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/ROADMAP.md`
11. `docs/LUA-API.md`
12. `docs/ARCHITECTURE.md`
13. `docs/FUTURE-CLEANUP.md`

Older dated finalization/handoff docs preserve checkpoint history but do not override current source, final-hardening evidence, or current-state docs.
