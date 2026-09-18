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

M1E, M1F, and M1G are complete at source/test/CI/package level.

Final M1G source checkpoint on `codex/m1g-progressive-finite-decode`:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Final M1G CI `35297026277` passed NeoForge 21.1.247 and 21.1.248 including build, deterministic tests, packaged-mod verification, and artifact upload.

Focused real-Minecraft M1F transport acceptance remains unrecorded. Focused **M1G audible/core Minecraft acceptance is recorded PASS** on NeoForge 21.1.247 in integrated singleplayer. The separate post-M1G Option A hardening pass is now green on both supported NeoForge targets; M1H lifecycle work is next.

Current finite implementation:

```text
ComputerCraft file
-> reusable server MediaAsset
-> server-authoritative finite playback state
-> protocol v7 STATE decodeRevision + codec-aware anchor
-> bounded client range requests / off-thread server reads
-> bounded sliding client encoded RAM
-> starvation-aware decoder-worker input
-> progressive MP3/common-WAV decoder
-> bounded mono S16 PCM at source rate
-> pure renderer-read policy
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared engine.

## Completed M1G contract

Locked media/renderer choices remain A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized common-WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll.

M1G now implements:

- explicit server-authoritative decoder/re-anchor revision in protocol v7;
- semantic seek increments revision while ordinary STATE preserves healthy decoder/window state;
- stale local worker identity is invalidated before cancellation;
- STATE is the sole nonterminal transition authority; explicit STOP remains;
- fixed **32-block** modern-finite core delivery and attenuation radius;
- HQ volume changes gain, not radius;
- global HQ volume zero keeps canonical time running while client transport/decode/render hibernates;
- client-local mute support through `canStartSilent()`;
- renderer activation/loss recovery through authoritative rejoin;
- ordinary non-gapless replay after physical EOF while authoritative looping remains enabled;
- real packaged-JLayer MP3 coverage across starvation/refill/sliding/pre-target discard;
- deterministic renderer-read policy coverage;
- persistent staging-leftover cleanup on whole-owner destruction.

Resolved M1G issues: KI-051, KI-053, KI-055, KI-056, KI-057, KI-058, KI-060, KI-061. KI-059 was already a product decision.

M1G intentionally does not include gapless MP3, permanent-source loop engineering, dynamic volume-aware listener membership, full M1H rejoin/movement lifecycle, or Sound Physics Remastered range/acoustic integration.

## Post-M1G hardening

The selected Option A hardening pass is complete at `e836dfac702dcc438fa0366dc2fba2132b5140c1`, CI `35404646105`.

Closed:

- KI-062 — DNS/server-monitor coupling;
- KI-063 — destructive replacement before admission;
- KI-054 — shutdown cleanup/root-lock retry handling;
- KI-064 — import no-progress and atomic-move fallback.

The next active milestone is **M1H**, which owns dynamic listener entry/leave/rejoin, recovery, and final moving-source/VS2 lifecycle.

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
- **M1G:** progressive MP3/common-WAV decode + positional renderer — source/test/CI/package/component complete; focused audible/core Minecraft acceptance PASS recorded on NeoForge 21.1.247 (2026-09-19).
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
2. `docs/HANDOFF-2026-09-18-M1G-COMPLETE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/FUTURE-CLEANUP.md`
7. `docs/ARCHITECTURE.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current records.
