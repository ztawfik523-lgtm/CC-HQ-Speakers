# CC:HQ Speakers — agent guide

## Start here

This repository improves the normal CC:Tweaked `speaker` peripheral into a high-quality programmable audio peripheral for the exact ATM10 target stack.

Before changing code, read in this order:

1. `docs/M1E-FINALIZATION-2026-09-13.md`
2. `docs/CURRENT-STATE.md`
3. `docs/VERIFIED-FACTS.md`
4. `docs/M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`
5. `docs/ARCHITECTURE.md`
6. `docs/M1E-SERVER-AUTHORITY.md`
7. `docs/M1E-FINITE-STREAMING-DESIGN.md`
8. `docs/ROADMAP.md`
9. `docs/KNOWN-ISSUES.md`
10. `docs/TESTING.md`
11. `docs/CC-T-COMPATIBILITY-CONTRACT.md` before changing standard speaker behavior
12. `docs/FUTURE-CLEANUP.md` before deleting or migrating old code
13. current branch source and current CI

`docs/PRE-M1F-PREPARATION.md`, `docs/NEXT-CHAT-HANDOFF.md`, and the dated older handoffs remain useful historical context, but the M1E finalization document plus exact current source/CI override stale checkpoint wording.

## Current checkpoint: finish M1E before M1F

M1E server-authority semantics are implemented and have been re-reviewed. The current work is the final M1E acceptance checkpoint.

Do not start M1F until the final focused Minecraft M1E script has actually reported PASS on the final candidate. Do not repair/rewrite the temporary decoder merely to make M1E audible; that remains M1G work.

The strengthened focused runtime contract is:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav> [result-file]
```

It now writes an auditable PASS/FAIL result file, defaulting to:

```text
m1e_server_authority_result.txt
```

Never call M1E Minecraft-runtime PASS unless the final candidate was actually run and the terminal/result file reports PASS.

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

- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`;
- diagnostic Java head used for the 2026-09-12 runtime investigation: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`;
- finalization code/test candidate before documentation-only follow-up: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`.

Do not describe every commit after `d0e66ab...` as documentation-only. Several Java commits added runtime diagnostics without intentionally changing server-authority semantics.

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

The current complete-file `.part/.media` client bridge and `FileFiniteAudioStream` remain temporary architecture. The 2026-09-12 runtime investigation proved the MP3 bridge is not a trustworthy duration/seek oracle. Keep that defect documented, but do not spend M1E/M1F scope repairing it solely for audibility.

## M1F boundary — clean break after M1E PASS

Once M1E runtime acceptance is recorded, M1F is the next implementation milestone and should make a clean break for the modern prepared finite path.

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
- no modern prepared-path `.part/.media` client song files.

M1F acceptance does not require audible finite playback or PCM decoding. A codec-agnostic fake/test consumer is sufficient. Its encoded-data contract must distinguish data available, data not arrived yet, true asset EOF, and cancelled/stale state so M1G can consume it progressively without transport redesign.

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
- Do not use `git add .`.

## Evidence order

When claims conflict, trust them in this order:

1. successful Minecraft runtime evidence on the exact target stack;
2. exact current source code;
3. exact current CI/build/package evidence;
4. `docs/M1E-FINALIZATION-2026-09-13.md` for the current checkpoint;
5. `docs/VERIFIED-FACTS.md`;
6. `docs/CURRENT-STATE.md`;
7. compatibility/design docs;
8. `docs/ROADMAP.md`;
9. milestone historical docs;
10. old prototype/P0 material.

Recommendations and unresolved choices must not be written into `VERIFIED-FACTS.md` as facts.
