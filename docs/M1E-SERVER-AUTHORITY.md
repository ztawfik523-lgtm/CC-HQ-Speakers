# M1E — server-authoritative finite playback

## Status

M1E source implementation is complete on `codex/m1e-server-authoritative-finite`. Minecraft runtime acceptance is still pending until `scripts/m1e_server_authority_test.lua` is executed successfully on the target stack.

This milestone deliberately leaves the old whole-file transfer in place as a temporary bridge. M1F replaces transport; M1G replaces complete-file client decoding.

## What M1E changed

Before M1E, the staged finite prototype treated renderer readiness/status as playback authority: the server had `LOADING`, `successfulRenderers`, `observed`, a 15-second no-renderer timeout, and client STARTED/SEEKED/ENDED reports could rewrite the server clock or EOF.

M1E removes that model.

A successful finite start now:

```text
retain/open server asset
    -> create generation/session
    -> set known duration
    -> state = PLAYING
    -> start canonical server clock immediately
    -> notify temporary current recipients
```

No client is required for semantic playback to begin.

## Server semantic states

`HQFiniteMediaServer` now uses only:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no server `LOADING` state for client buffering.

`ERROR` is reserved for a server-side playback/transfer failure in the temporary bridge. Client decoder/render failures are diagnostic and do not globally fail playback.

## Natural EOF

`FinitePlaybackClock` now exposes deterministic non-looping `reachedEnd(now)` behavior.

When a PLAYING non-looping session reaches duration:

1. finish/clamp the canonical clock at duration;
2. set state `ENDED`;
3. close/cancel the temporary old transfer first;
4. release the playback asset reference;
5. save terminal status;
6. send authoritative state / queue the ComputerCraft state event.

The close-before-release ordering prevents a short song from releasing/deleting an asset while the transitional transfer still has work against it.

Looping sessions wrap and never naturally enter `ENDED`.

## Exact-end seek

- non-looping `audioSeek(duration)` -> immediate `ENDED`;
- looping `audioSeek(duration)` -> position wraps to `0` and remains active.

## Protocol v4

M1E bumps the HQ Speaker network protocol from `3` to `4`.

### BEGIN/setup

The existing transitional `HQFiniteMediaBeginPacket` still carries immutable/setup information required by the old transfer/renderer:

- source/media/generation;
- format;
- initial volume;
- world/block speaker position;
- total encoded bytes;
- initial loop/pause flags.

### STATE

New `HQFiniteMediaStatePacket` carries mutable authoritative semantic truth:

- source/media/generation;
- `PLAYING` / `PAUSED` / `ENDED` / `ERROR`;
- canonical position;
- duration;
- volume;
- looping;
- optional error detail.

The packet intentionally does not duplicate immutable format/size/position setup from BEGIN.

### Client status

`HQFiniteMediaStatusPacket.Transition` is now only:

- `READY`
- `ERROR`

READY means the temporary full-file client has constructed its decoder and wants fresh canonical server state. ERROR is diagnostic telemetry only.

The prototype renderer-authority transitions `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, and `ENDED` are removed.

## Temporary client bridge

M1E still uses `hqspeaker-cache/*.part/.media` and `FileFiniteAudioStream` because replacing transfer/decoder is not this milestone.

The important semantic correction is:

```text
old behavior:
full transfer completes -> client starts at 0 -> client tells server STARTED

M1E behavior:
full transfer completes -> client reports READY
                      -> server sends fresh STATE
                      -> client seeks/starts at current server position
```

If the server is paused when READY arrives, the client prepares at the authoritative paused position without starting sound. If the server already ended/errored, the STATE packet destroys the stale local bridge instead of starting it.

The client's local `FinitePlaybackClock` remains only a renderer projection for local restart/resource behavior. It is not canonical server truth.

## Intentionally deferred to M1F/M1G

M1E does **not** claim to solve:

- fixed recipient capture;
- dynamic late-range entry;
- server push of the whole encoded file;
- server file reads during `tick()`;
- client disk `.part/.media` writes;
- complete-file-before-decoder requirement;
- bounded progressive MP3/WAV decoding.

M1F removes the transport problems. M1G replaces the complete-file decoder path.

## Tests

Pure/unit:

- `FinitePlaybackClockTest.nonLoopingClockReportsNaturalEndAtDuration`
- `FinitePlaybackClockTest.loopingClockNeverReportsNaturalEnd`
- retained exact-end seek, pause/resume, and loop-rebase tests.

Minecraft runtime contract:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It verifies:

- `playPrepared` is immediately server `playing`;
- server position advances without waiting for renderer readiness;
- pause freezes canonical position;
- resume advances it again;
- non-looping exact-duration seek ends immediately;
- looping exact-duration seek wraps near zero and stays playing.

The runtime script has not yet been executed in Minecraft, so do not report M1E runtime PASS.

## Next milestone

M1F replaces the bridge with client-requested bounded encoded ranges, off-thread server reads, safe async asset lifetime, relevance/generation validation, bounded outstanding work, and no client disk song cache.
