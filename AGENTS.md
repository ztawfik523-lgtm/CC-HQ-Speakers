# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1F finite range transport is source/test/CI complete; M1G has not started.**

Read in this order before changing implementation:

1. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1F.md`
3. `docs/CURRENT-STATE.md`
4. `docs/LUA-API.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/M1E-SERVER-AUTHORITY.md`
8. `docs/M1E-FINITE-STREAMING-DESIGN.md`
9. `docs/ROADMAP.md`
10. `docs/KNOWN-ISSUES.md`
11. `docs/TESTING.md`
12. `docs/CC-T-COMPATIBILITY-CONTRACT.md`
13. `docs/FUTURE-CLEANUP.md`
14. exact current source and CI

Older M1E/pre-M1F handoffs are historical context and do not override the M1F checkpoint.

## Exact current implementation checkpoint

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Branch: `codex/m1f-demand-driven-finite`

Pre-M1F docs base:

`bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`

M1F code/test head before docs:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Exact CI:

`34731827907`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Do not call that Minecraft runtime proof.

## Target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target 1.21.1-1.5.1

## Product rule

This is a programmable ComputerCraft speaker peripheral.

Lua decides application meaning and policy: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc.

Java models truthful technical capabilities only.

Do not create permanent Java concepts such as music/effect/notification lanes, playlist/album managers, or application-level priority policy.

## Explain behavior before code

When discussing the project with the user, explain what happens in Minecraft/ComputerCraft first.

For finite playback after M1F:

```text
ComputerCraft file
-> server MediaAsset
-> server-owned playback clock/state
-> client asks for small encoded pieces
-> bounded client RAM
-> M1G will decode those pieces into sound
```

Only then introduce packet/class/executor details when useful.

## Standard CC:T compatibility

The normal `computercraft:speaker` remains the product surface and exposed type remains `speaker`.

Standard calls must remain delegated/native:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW uses separate `hqspeaker_audio_empty` backpressure.

## Modern finite Lua/API

Recommended one-call path:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, path, { volume = 0.6 })
```

Reusable path:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

Modern finite controls:

```text
audioStatus
audioPause
audioResume
audioSeek
audioSetVolume
audioSetLooping
audioStop
```

`audioPlayStaged()` was our prototype API and is now removed. Do not restore it.

## M1E authority remains mandatory

M1F must not regress M1E server authority.

Server owns finite:

- generation;
- PLAYING/PAUSED/ENDED/ERROR;
- duration/position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

Client READY requests fresh state. Client ERROR is diagnostic only.

The final manual M1E Minecraft PASS was skipped by explicit owner decision. Never claim it passed.

## M1F contract now implemented

Modern prepared finite transport is protocol v5 client-pulled ranges.

Current source guarantees:

- no modern whole-file server push;
- no modern `.part/.media` client file;
- no modern `HQFiniteMediaChunkPacket` / `HQFiniteMediaEndPacket`;
- first range demand waits for authoritative STATE/anchor;
- bounded 128 KiB range packets;
- bounded 512 KiB active client encoded window;
- per-player outstanding request/byte caps;
- bounded server IO pool/queue;
- no asset reads on server tick;
- in-flight range reads retain the MediaAsset;
- generation/asset/player/relevance/bounds rechecked before send;
- stale completion discarded;
- server range IO stops/drains before media store close;
- availability distinguishes data present, data not arrived, true EOF, and stale/cancelled.

Those numeric limits are implementation tuning values, not frozen product semantics.

## M1G next boundary

Do not repair the old JavaSound/mp3spi complete-file decoder.

M1G owns the real progressive engine:

- MP3 progressive JLayer path unless another decoder is proven;
- temporary missing encoded data must not become EOF;
- MP3 earlier-anchor reservoir pre-roll;
- common WAV layout/time-to-byte metadata;
- WAV integer/float conversion and stereo-to-mono downmix;
- bounded PCM queue;
- decoder cancellation;
- actual positional Minecraft/OpenAL finite rendering.

Core final formats remain MP3 + common WAV. FLAC remains later gated M1I.

## M1H remains separate

M1F revalidates current player relevance for range reads/completions, but full dynamic listener lifecycle is M1H:

- late range entry;
- proactive leave cleanup;
- return/rejoin;
- dimension/world/resource reload;
- underrun rejoin;
- VS2 moving-speaker listener lifecycle.

Do not claim M1F solved M1H.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. exact current CI/package evidence;
4. current implementation checkpoint docs;
5. verified-facts ledger;
6. architecture/design docs;
7. roadmap;
8. older milestone/handoff docs.

Green CI is not Minecraft runtime proof.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior for cleanliness alone.
- Preserve useful compatibility, but not proven bugs or our discarded prototype APIs.
- Keep server semantic state separate from transfer/decoder/renderer state.
- Never invent finite duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- Do not start M1G unless explicitly asked.
- If a recheck discovers a genuine architecture/product decision with meaningful tradeoffs, present the options to the user before implementing a direction.
- Handle ordinary correctness/robustness fixes without unnecessary questions.
- Do not report a runtime script PASS unless it actually ran successfully.
