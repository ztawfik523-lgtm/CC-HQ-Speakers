# Current state

## Current checkpoint

The project is at the **M1F source/test/CI checkpoint**.

M1F has replaced the modern prepared-file whole-download bridge with bounded client-requested encoded ranges. The final code/test head before documentation follow-up is:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

GitHub Actions run `34731827907` passed NeoForge 21.1.247 and 21.1.248, including tests, packaged-mod verification, and artifact upload.

That is not Minecraft runtime proof. M1F has not been manually runtime-accepted in Minecraft.

M1G has **not started**.

Read `M1F-IMPLEMENTATION-2026-09-13.md` first for the exact transport checkpoint, then `LUA-API.md` for the ComputerCraft programming surface.

## Repository/target

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1f-demand-driven-finite`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR target 1.21.1-1.5.1

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`
- M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`
- pre-M1F documentation base: `bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`
- first M1F transport commit: `07fb22b7e127f74615cb2f381d04f21fd202e550`
- M1F lifecycle/retry hardening: `2de8398804eb40fbafd2ada79fd6ce188e109b34`
- final M1F code/test head before docs: `934e74b8ff619178d703f73df8a16ee97b3fc2af`

## Product identity

CC:HQ Speakers upgrades the normal CC:T `speaker` into a programmable audio peripheral.

Lua decides whether audio means music, effects, alarms, speech, notifications, ambience, soundboards, playlists, or anything else. Java exposes truthful technical capabilities only.

Do not create permanent Java music/effect/notification roles or playlist policy.

## Standard CC:T contract

The normal `computercraft:speaker` remains the product surface.

Preserve native/delegated CC:T behavior for:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW keeps separate `hqspeaker_audio_empty` backpressure.

## Modern Lua finite workflow

Recommended:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Reusable/preloaded form:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

Finite controls remain:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

`audioPlayStaged()` has now been **removed**. It was this project's prototype API, not an inherited HQ Speakers compatibility requirement.

## Finite architecture now implemented through M1F

```text
ComputerCraft file
    -> temporary import staging
    -> immutable server MediaAsset
    -> authoritative server playback timeline
    -> server-selected encoded anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded temporary client encoded RAM
```

The next part is still missing:

```text
bounded encoded RAM
    -> M1G progressive MP3/common-WAV decoder/converter
    -> bounded mono PCM
    -> positional Minecraft/OpenAL renderer
```

## M1E authority status

M1E source/tests/CI are implemented and preserved under M1F.

The server remains canonical for finite:

- generation;
- playing/paused/ended/error state;
- duration and position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

Client READY asks for current server STATE. Client ERROR remains diagnostic and cannot become canonical playback failure.

The final manual M1E Minecraft PASS was **not performed** because the project owner explicitly chose to skip it. Never describe M1E as runtime-verified.

## M1F transport status

M1F source/test/CI is implemented.

In user terms:

```text
server keeps the whole file
    -> client gets current playback state
    -> client asks for small encoded pieces
    -> old/unneeded pieces can be discarded
    -> file size no longer determines client disk/RAM use
```

Implemented facts:

- protocol v5 range request/data packets;
- maximum current range payload: 128 KiB;
- one active finite encoded client window: 512 KiB;
- bounded per-player outstanding requests/bytes;
- bounded two-thread server range-IO pool and bounded queue;
- server asset retained while each asynchronous read is in flight;
- no large asset read on server tick;
- generation/asset/player/dimension/range/bounds revalidation;
- stale completion discard;
- arbitrary encoded offsets;
- authoritative STATE carries encoded `anchorOffset` and `anchorTime`;
- first client demand waits for STATE rather than assuming byte zero;
- client window distinguishes data available, not-arrived-yet, true EOF, and stale/cancelled;
- modern client no longer writes `.part/.media` song files;
- old finite CHUNK/END packets removed;
- `audioPlayStaged()` removed.

`transferredBytes` is no longer part of the modern finite status because there is no whole-file transfer to count. `totalBytes` remains useful asset metadata.

## Decoder boundary

The old `FileFiniteAudioStream`/JavaSound/mp3spi bridge remains known-bad historical code but is no longer used by the modern M1F prepared client.

Do not repair it to make M1F audible.

M1G owns:

- progressive MP3 decode;
- common WAV layout/conversion;
- starvation versus true EOF at the decoder boundary;
- MP3 Layer III pre-roll;
- bounded PCM queues;
- mono conversion/downmix;
- audible positional rendering.

## Format direction

Final core finite target:

- MP3 / MPEG Layer III;
- common WAV.

Later gated extension:

- native FLAC, only if the complete analyzer/decode/seek/package/runtime contract is proven.

Not final finite requirements:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- unusual/compressed/telephony WAV
- >2-channel finite audio

Frozen M1D's broader analyzer surface is historical and does not redefine this target.

## M1H boundary still open

M1F rechecks relevance before serving/sending ranges, so stale asynchronous reads cannot send obsolete data after the player becomes irrelevant.

Full dynamic listener lifecycle remains M1H:

- discovering a player who enters range after playback already started;
- clean proactive leave-range client cancellation;
- returning/rejoining at current server time;
- resource reload/world/dimension renderer recovery;
- underrun refill/rejoin behavior;
- final VS2 moving-speaker listener lifecycle.

Do not claim M1F solved M1H.

## Evidence rule

Use this order when claims conflict:

1. exact target-stack Minecraft runtime evidence;
2. current source;
3. current CI/package evidence;
4. `M1F-IMPLEMENTATION-2026-09-13.md`;
5. `VERIFIED-FACTS.md`;
6. this file;
7. architecture/design docs;
8. roadmap;
9. older milestone/handoff documents.

Green CI is not runtime proof.

## Current read order

1. `M1F-IMPLEMENTATION-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1F.md`
3. `LUA-API.md`
4. `CURRENT-STATE.md`
5. `VERIFIED-FACTS.md`
6. `ARCHITECTURE.md`
7. `M1E-SERVER-AUTHORITY.md`
8. `M1E-FINITE-STREAMING-DESIGN.md`
9. `ROADMAP.md`
10. `KNOWN-ISSUES.md`
11. `TESTING.md`
12. `FUTURE-CLEANUP.md`
13. exact current source/CI
