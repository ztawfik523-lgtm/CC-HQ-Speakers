# CC:HQ Speakers — agent guide

## Start here

This repository improves the normal CC:Tweaked `speaker` peripheral into a high-quality programmable audio peripheral for the exact ATM10 target stack.

Before changing implementation, read in this order:

1. `docs/HANDOFF-2026-09-13-PRE-M1F.md`
2. `docs/LUA-API.md`
3. `docs/CURRENT-STATE.md`
4. `docs/VERIFIED-FACTS.md`
5. `docs/ARCHITECTURE.md`
6. `docs/M1E-SERVER-AUTHORITY.md`
7. `docs/M1E-FINITE-STREAMING-DESIGN.md`
8. `docs/ROADMAP.md`
9. `docs/KNOWN-ISSUES.md`
10. `docs/TESTING.md`
11. `docs/CC-T-COMPATIBILITY-CONTRACT.md` before changing standard speaker behavior
12. `docs/FUTURE-CLEANUP.md` before deleting or migrating old code
13. exact current branch source and current CI

Older handoffs, `PRE-M1F-PREPARATION.md`, the M1E finalization doc, and dated historical docs remain useful evidence/context but do not override the current handoff, exact current source, or current project decisions.

## Current checkpoint: documentation only, before M1F

M1E server-authority source/tests/CI are finalized and re-reviewed.

The project owner explicitly chose **not to perform the final manual M1E Minecraft acceptance run**. Therefore:

- never claim M1E has a recorded Minecraft-runtime PASS;
- preserve the missing runtime-PASS evidence as an explicit gap;
- do not treat that skipped manual run as a blocker before later M1F implementation unless the owner changes the decision.

M1F implementation has **not started** at this checkpoint.

Do not implement M1F, change M1E semantics, repair the temporary decoder, or start M1G unless explicitly requested after this documentation checkpoint.

## How to explain the project

Explain behavior in normal ComputerCraft/Minecraft terms first. Do not lead with classes, packets, executors, generations, or buffer abstractions when a user-facing description will do.

For finite files, start with:

```text
ComputerCraft has a file
    -> HQ Speakers imports it into server-owned media storage
    -> the speaker starts a server-owned playback timeline
    -> each relevant Minecraft client asks for only the small encoded pieces it currently needs
    -> later the client decodes those pieces into positional sound
```

Only then give the implementation details needed for the question.

## Exact target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Active branch:

`codex/m1e-server-authoritative-finite`

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`;
- diagnostic Java head used for the 2026-09-12 runtime investigation: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`;
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`;
- last pre-documentation branch head: `ff8fc52e8660249150e056d1dff4307377afe7c4`.

M1E finalization CI run `34725651930` passed both target NeoForge versions with tests/package verification.

## Product rule

This is not a built-in music player, notification system, sound-effect player, or Java playlist manager.

Lua decides whether audio is music, alarms, speech, ambience, notifications, soundboards, playlists, or anything else. Java distinguishes sources only by truthful technical capabilities.

Do not create permanent Java concepts such as music/effect/notification lanes, automatic application priorities, playlists, albums, or a client music library.

## Standard CC:T compatibility is mandatory

The normal `computercraft:speaker` remains the main product surface and exposed peripheral type remains `speaker`.

Standard behavior must continue to come from CC:T's real `SpeakerPeripheral` unless exact target-stack evidence requires otherwise:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ APIs extend that contract; they do not redefine it.

## Lua/API surface

Read `docs/LUA-API.md` before adding, removing, renaming, or changing Lua-facing behavior.

Recommended modern finite helpers:

- `hq.playFile`
- `hq.prepareFile`
- `hq.preparedInfo`
- `hq.playPrepared`
- `hq.releasePrepared`

Modern finite controls:

- `audioStatus`
- `audioPause`
- `audioResume`
- `audioSeek`
- `audioSetVolume`
- `audioSetLooping`
- `audioStop`

HQ raw/feed uses `speakPCM` and separate `hqspeaker_audio_empty` pacing.

### `audioPlayStaged` decision

`audioPlayStaged()` is **our prototype API**, not an inherited/original HQ Speakers compatibility API.

It is absent from the untouched inherited baseline and appears in this project's staged/local-file prototype.

Project decision on 2026-09-13:

**when M1F implementation begins, remove `audioPlayStaged()` instead of carrying that direct-staged playback route into the new transport.**

New programs should use `hq.playFile()` or prepare/play/release.

Do not remove it during this documentation-only checkpoint.

## Technical source categories

### Standard CC:T speaker audio

Preserve native CC:T behavior and native backpressure.

### HQ raw/feed PCM

`speakPCM` is open-ended producer-fed audio. It has bounded backpressure and separate `hqspeaker_audio_empty` pacing. It does not truthfully have finite duration, arbitrary seek, or natural EOF.

### Finite media

Finite files have a known server-owned timeline and may truthfully support duration, position, pause/resume, seek, loop, volume, and natural EOF.

Core final finite formats:

- MP3 / MPEG Layer III
- common WAV

Wanted later but separately gated:

- native `.flac`

Not final finite requirements:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- exotic/compressed/telephony WAV variants
- >2-channel finite input

One physical speaker renders one mono positional source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

### Live network audio

Internet MP3/HLS/TS is later work. Live sources are open-ended and must not pretend to have finite duration/seek history.

## Settled finite architecture

These are not open design questions:

- successful finite play starts the canonical server clock immediately, even with zero listeners;
- the server owns generation, state, duration, position, pause/resume, seek, loop, volume, and EOF;
- client renderer readiness/failure never becomes canonical playback authority;
- finite media is a reusable server `MediaAsset`, separate from playback and physical speakers;
- final finite transfer is client-pulled in bounded encoded byte ranges;
- there is no persistent client song cache, `.part` library, completed client media library, sparse-file cache, LRU database, or cross-restart download resume;
- clients keep only bounded temporary encoded/decoded RAM for active playback;
- seek and late join fetch bounded encoded data around a server-selected anchor/current position;
- a slow client may go silent/refill/rejoin and never slows or rewinds the canonical server clock;
- do not compare server and client `System.nanoTime()` values across JVMs;
- multispeaker synchronization must not wait for an expected global member/tap count;
- each audible physical speaker ultimately keeps its own positional renderer.

## M1E boundary

Current `HQFiniteMediaServer` uses semantic states `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`. There is no canonical client-buffering `LOADING` state.

Client finite telemetry is only:

- READY — request a fresh authoritative state snapshot;
- ERROR — diagnostic only.

Client decoder/render failure must never rewrite canonical server playback.

The current complete-file `.part/.media` client bridge and `FileFiniteAudioStream` remain temporary architecture. The 2026-09-12 runtime investigation proved the MP3 bridge is not a trustworthy duration/seek oracle. Keep that defect documented, but do not spend M1F scope repairing it solely for audibility.

The final M1E manual acceptance script exists but was intentionally not run after finalization. Do not call the milestone runtime-verified.

## M1F boundary — clean break, not started

When explicitly started, M1F should make a clean break for the modern prepared finite path.

In user terms:

```text
server owns the whole song
    -> client asks for a small piece near the current playback point
    -> old pieces are discarded as playback moves
    -> seek asks for another piece rather than downloading everything in between
```

M1F owns:

- client -> server bounded range requests carrying source/generation/asset/offset/length;
- server -> client bounded range data carrying source/generation/asset/offset/bytes;
- active playback/generation/asset/player/dimension/relevance/range validation;
- bounded outstanding requests/bytes and rate protection where needed;
- bounded server IO executor/queue;
- no large asset reads on the server tick;
- safe retained asset lifetime while async reads are in flight;
- stale completion discard after replacement/leave/disconnect;
- bounded temporary client encoded RAM/window only;
- arbitrary encoded offsets for seek/rejoin;
- codec-appropriate server-selected seek/stream anchors;
- no modern prepared-path `.part/.media` client song files;
- removal of the obsolete modern direct-staged `audioPlayStaged()` route.

M1F acceptance does not require audible finite playback or PCM decoding. A codec-agnostic fake/test consumer is sufficient. Its encoded-data contract must distinguish data available, data not arrived yet, true asset EOF, and cancelled/stale state so M1G can consume it progressively without transport redesign.

### M1F shutdown requirement

Background asset reads introduced by M1F must be stopped/drained/cancelled before the shared server media store closes/deletes assets during shutdown.

This is a correctness requirement, not a user-facing product choice.

## M1G boundary

M1G owns:

- progressive MP3/common-WAV decoding/conversion;
- bounded PCM queues;
- temporary starvation vs real EOF;
- MP3 earlier-anchor pre-roll/bit-reservoir handling;
- WAV conversion/downmix;
- decoder cancellation;
- final positional Minecraft/OpenAL finite rendering and audibility;
- final prepared/local MP3/common-WAV format narrowing.

Do not move these into M1F merely to preserve temporary audibility.

## Parked cleanup

Unless a direct dependency forces it, do not spend M1F on unrelated cleanup such as legacy byte APIs, old finite decoder polishing, OGG/AIFF/AU historical removal, HLS/TS/live fixes, custom HQ block cleanup, gain/category cleanup, multispeaker redesign, SPR integration, diagnostics cleanup, or license/provenance work.

See `docs/FUTURE-CLEANUP.md`.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior merely for architectural cleanliness.
- Preserve useful compatible behavior, but do not preserve a proven bug.
- Prefer exact runtime/source evidence over plausible assumptions.
- Keep server semantic state separate from client transfer/decoder/renderer state.
- Never invent duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- Avoid polishing concepts explicitly scheduled for deletion.
- Keep frozen SPR V7.1 acoustics unchanged unless explicitly retuning them.
- Do not call a Minecraft runtime script PASS unless it was actually executed successfully.
- Before changing implementation, if a recheck reveals a real design/correctness issue that changes agreed behavior, ask the project owner first.
- Handle ordinary low-level implementation details without reopening settled product decisions.
- Do not use `git add .`.

## Evidence order

When claims conflict, trust them in this order:

1. successful Minecraft runtime evidence on the exact target stack;
2. exact current source code;
3. exact current CI/build/package evidence;
4. `docs/HANDOFF-2026-09-13-PRE-M1F.md` for current sequencing/decisions;
5. `docs/LUA-API.md` for intended user-facing API;
6. `docs/VERIFIED-FACTS.md`;
7. `docs/CURRENT-STATE.md`;
8. compatibility/design docs;
9. `docs/ROADMAP.md`;
10. milestone historical docs;
11. old prototype/P0 material.

Recommendations and unresolved choices must not be written into `VERIFIED-FACTS.md` as facts.