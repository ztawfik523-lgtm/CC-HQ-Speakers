# M1E — server-authoritative finite playback

## Status

M1E server-authority source/tests/CI are finalized and re-reviewed.

Original semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Original CI:

`34658958488` — success on NeoForge 21.1.247 and 21.1.248.

Finalization code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

Finalization CI:

`34725651930` — success on both target NeoForge versions with tests/package verification.

The final manual Minecraft M1E acceptance script was prepared but not run. The project owner chose to skip that manual test.

Therefore:

- M1E has **no recorded final Minecraft-runtime PASS**;
- do not claim that it does;
- the missing manual PASS remains an evidence gap;
- by current project decision, that skipped manual test is no longer treated as a required sequencing gate before future M1F work.

M1F implementation has not started at the current documentation checkpoint.

## What M1E changed

Before M1E, the staged finite prototype treated renderer readiness/status as playback authority. The server could wait on client observation and client transitions could influence canonical state.

M1E removes that model.

A successful modern prepared finite start now behaves conceptually like:

```text
server has a prepared media asset
    -> create a new playback generation
    -> known duration is already server-side
    -> state = PLAYING
    -> server clock starts immediately
    -> nearby clients are only renderers/consumers, not the clock owner
```

No client is required for semantic playback to begin.

## Server semantic states

`HQFiniteMediaServer` uses only:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no server `LOADING` state for client buffering.

Client buffering/decoder readiness is not canonical playback state.

## Natural EOF

The server playback clock owns known-duration EOF.

For non-looping playback reaching duration:

1. clamp/finish canonical position at duration;
2. enter `ENDED`;
3. close/cancel the temporary old transfer;
4. release the playback asset reference;
5. save terminal status;
6. publish authoritative state.

Looping playback wraps and does not naturally enter ENDED.

## Exact-end seek

- non-looping `audioSeek(duration)` -> immediate ENDED at duration;
- looping `audioSeek(duration)` -> wraps to 0 and remains active.

## Protocol v4 authority split

The temporary modern prepared path still has old setup/transfer packets, but mutable semantic truth is carried by the authoritative STATE packet.

### BEGIN/setup

Temporary setup for the old bridge includes source/media/generation, format, speaker position, total encoded bytes, and initial playback settings.

### STATE

Authoritative mutable truth includes:

- source/media/generation;
- PLAYING / PAUSED / ENDED / ERROR;
- canonical position;
- duration;
- volume;
- looping;
- optional server error detail.

## Client finite status

Modern prepared finite client status is only:

- READY — asks for a fresh current server state;
- ERROR — diagnostic only.

The old renderer-authority transitions STARTED/PAUSED/RESUMED/SEEKED/ENDED are not part of the M1E modern authority contract.

A client decoder/render failure cannot globally end or rewind server playback.

## Temporary client bridge

M1E intentionally left transport/decoder replacement for later milestones.

Current temporary behavior still includes complete-file transfer and the old file-backed client decoder.

Important semantic correction:

```text
old prototype:
client becomes ready -> client behavior can define playback truth

M1E:
server timeline already exists -> client READY asks where the server is now
```

If the server is paused when the client becomes ready, current server state remains paused. If the server already ended/replaced the playback, stale client state cannot restart it as canonical playback.

## Runtime diagnostic evidence

The 2026-09-12 runtime diagnostic repeatedly reached:

```text
BEGIN
-> authoritative PLAYING STATE
-> complete temporary transfer
-> old decoder open
-> READY
-> fresh authoritative STATE near current server position
-> renderer submission
-> first PCM read returns no data
-> client diagnostic ERROR
```

The old client failure did not rewrite canonical server state. Server-side state/control continued through behavior consistent with pause/resume/end/loop testing.

That is supporting evidence for authority separation, but the final strengthened Minecraft script was never run after finalization, so there is no recorded final M1E PASS.

## Decoder/duration boundary

The temporary JavaSound/mp3spi bridge is not an authority source.

For the tested MP3:

- server analyzer: `161.304 s`;
- JavaSound/mp3spi: `322.584 s`;
- runtime operator: roughly 2:45.

Client decoder duration is therefore not canonical.

The temporary MP3 seek bridge also mixes incompatible seek/skip semantics, can report apparent success after incomplete positioning, and may seek the same restarted stream twice.

These are temporary bridge defects.

Do not repair them merely to keep M1F audible. M1G owns the replacement progressive decoder/audio path.

## User-facing Lua path

The intended finite programming workflow is documented in `LUA-API.md`.

Recommended helpers:

- `hq.playFile`
- `hq.prepareFile`
- `hq.preparedInfo`
- `hq.playPrepared`
- `hq.releasePrepared`

Finite controls remain capability-oriented (`audioStatus`, pause/resume/seek/volume/loop/stop).

`audioPlayStaged()` is this project's own old prototype API, not inherited HQ Speakers compatibility. It is approved for removal when M1F implementation starts.

## Deferred to M1F/M1G

M1E does **not** solve:

- fixed recipient capture;
- dynamic late-range entry;
- whole-file server push;
- server tick-driven transfer reads;
- client `.part/.media` files;
- complete-file-before-decoder requirement;
- correct progressive MP3/common-WAV decoding;
- final audible finite rendering.

M1F removes the transport problems. M1G replaces the decoder/render path.

## Current continuation

For current sequencing and API decisions, read:

1. `HANDOFF-2026-09-13-PRE-M1F.md`
2. `LUA-API.md`
3. `CURRENT-STATE.md`

This file is the M1E authority contract, not the current implementation handoff.