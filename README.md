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

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, packaged-mod verification, and artifact upload.

Documentation/audit work after that checkpoint has not changed implementation source.

Focused real-Minecraft M1F transport acceptance is not recorded. Focused audible M1G Minecraft acceptance is also not recorded. Green CI/component proof is not runtime proof.

Current finite implementation:

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

## Current M1G decisions

Locked media/renderer choices remain A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized common-WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll.

Later rechecks narrowed the remaining M1G scope:

- move to an explicit server-authoritative decoder/re-anchor revision, likely protocol v7; current source is still v6;
- keep a fixed **32-block** modern-finite core listening/delivery radius; HQ volume changes gain, not the core radius;
- global HQ volume zero keeps canonical server time running but hibernates client decode/render/range requests until unmuted;
- looping is ordinary replay of the same media after local physical EOF while authoritative state still says looping; a normal restart gap is acceptable;
- do not add gapless MP3/LAME padding work, permanent-source loop engineering, dynamic volume-aware range, or SPR acoustic/range integration to M1G;
- future Sound Physics Remastered compatibility owns deliberate extended range/acoustics and matching transport relevance.

One implementation-shape choice remains for the v7 work: keep finite CONTROL packets only as optional latency hints, or remove duplicate PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP projection and let STATE be the sole transition authority. Correctness must not depend on CONTROL/STATE ordering either way.

## Current blockers / hardening

The main M1G decoder cluster is KI-053/KI-056/KI-057: current v6 can rewind a live window, race expected seek cancellation into a fatal decoder error, and conflates ordinary STATE snapshots with decoder-reanchor intent.

Other current M1G items:

- KI-058: explicitly enforce the selected fixed 32-block channel attenuation distance while volume changes gain;
- KI-060: harden renderer-start/local-silent/global-volume-zero behavior;
- KI-051: implement selected ordinary replay;
- KI-055: add real-MP3 progressive integration and focused `FinitePcmAudioStream` proof;
- KI-061: clean leftover per-speaker staging files on whole-owner cleanup.

Rechecked repository-wide findings which also remain real:

- KI-062: synchronized dynamic stream dispatch can hold the composite monitor across blocking DNS while server tick or synchronized lifecycle cleanup waits on that monitor;
- KI-063: rejected/failed RAW or prepared replacement can destroy valid current playback before the replacement is admitted;
- KI-054: shutdown can lose deletion retry state or fail before media-store close, leaving the root lock/registry alive in the JVM;
- KI-064: media import can spin indefinitely on repeated zero-byte reads and lacks an unsupported-atomic-move fallback.

The latest practical sequencing suggestion is to remove KI-062 before relying on legacy stream calls, then complete the v7 decoder/reanchor cluster. A broader safety-first batch is also defensible; later HLS/legacy/CI/release cleanup should not derail M1G.

## Audit recheck notes

A September 16 full-repository audit was challenged against exact source. The important surviving findings above remain, but several first-draft claims were retracted:

- MP3 anchors contain only `(offset, seconds)` and STATE already carries both;
- modern STATE does not carry live x/y/z coordinates;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- the Level-keyed WeakHashMap intentionally relies on explicit lifecycle eviction because cached values reference their Level;
- inherited HTTP stream paths close their streams;
- release retry ticking is `ServerMediaAssets.tickPendingReleases()`.

For VS2 movement, modern BEGIN already carries block coordinates and the legacy client already transforms those coordinates client-side each tick. M1H may reuse that pattern or add explicit position updates later; no M1G protocol change is implied.

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
- **M1G:** progressive MP3/common-WAV decode + positional renderer — integrated in source; selected correctness/source/evidence work remains.
- **M1H:** dynamic listener/late-join/leave-return/recovery and final moving-source/VS2 lifecycle.
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
2. `docs/M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/FUTURE-CLEANUP.md`
7. `docs/ARCHITECTURE.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current records.
