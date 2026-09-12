# M1E — server-authoritative finite playback

## Status

M1E server-authority source/test/CI semantics are implemented on `codex/m1e-server-authoritative-finite`.

Exact original semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Exact GitHub Actions run for that checkpoint:

`34658958488` — successful on NeoForge 21.1.247 and 21.1.248 with tests/package verification.

The branch later gained Java-only runtime diagnostics without intentionally changing the M1E authority architecture. The pre-preparation diagnostic head is:

`c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`

CI run `34686003774` is green on both target NeoForge versions for that diagnostic head.

Minecraft runtime acceptance is still pending until `scripts/m1e_server_authority_test.lua` is actually observed to print its success line on the target stack.

This milestone deliberately leaves the old whole-file transfer/decoder in place only as a temporary bridge. M1F replaces transport; M1G replaces complete-file client decoding.

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

`HQFiniteMediaServer` uses only:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no server `LOADING` state for client buffering.

`ERROR` is reserved for a server-side playback/transfer failure in the temporary bridge. Client decoder/render failures are diagnostic and do not globally fail playback.

## Natural EOF

`FinitePlaybackClock` exposes deterministic non-looping `reachedEnd(now)` behavior.

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

`HQFiniteMediaStatePacket` carries mutable authoritative semantic truth:

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

READY means the temporary full-file client has constructed its old decoder and wants fresh canonical server state. ERROR is diagnostic telemetry only.

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
                      -> client attempts to seek/start at current server position
```

If the server is paused when READY arrives, the client prepares at the authoritative paused position without starting sound. If the server already ended/errored, the STATE packet destroys the stale local bridge instead of starting it.

The client's local `FinitePlaybackClock` remains only a renderer projection for local restart/resource behavior. It is not canonical server truth.

## 2026-09-12 runtime diagnostic

The latest diagnostic run repeatedly reached this sequence:

```text
BEGIN
-> authoritative PLAYING STATE
-> complete old whole-file transfer
-> JavaSound/mp3spi decoder open
-> READY
-> fresh authoritative STATE near current server position
-> SoundManager submission
-> first PCM read returns no data
-> client diagnostic ERROR
```

The client decoder failure did **not** rewrite canonical server state. Server-side PLAYING/PAUSED/resume/end/loop-related state traffic continued independently, which is consistent with the authority separation M1E is designed to provide.

However, the captured logs do not include the ComputerCraft terminal line:

```text
M1E server-authority contract passed
```

Therefore this run is diagnostic evidence only. It does not complete M1E runtime acceptance.

Full diagnostic notes: `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`.

## Decoder/duration boundary

The temporary JavaSound/mp3spi bridge is not an authority source.

For the tested MP3:

- server analyzer reported `161.304 s`;
- JavaSound/mp3spi reported `322.584 s`;
- the runtime operator confirmed the source is roughly 2:45 (~165 s).

The MP3SPI duration is therefore clearly wrong and must remain diagnostic only. The server analyzer remains canonical for M1E timeline/EOF semantics.

The current `FileFiniteAudioStream` MP3 seek path is also known to misuse mp3spi skip semantics and may report a requested target after incomplete positioning. The renderer restart path performs a redundant second seek as well.

These are temporary bridge defects. They do **not** change the milestone split:

- do not polish/fix the decoder solely to finish M1E;
- do not require it to work correctly for M1F;
- keep its future needs in mind so M1F does not block M1G;
- M1G owns the replacement progressive decoder and audible path.

## Intentionally deferred to M1F/M1G

M1E does **not** claim to solve:

- fixed recipient capture;
- dynamic late-range entry;
- server push of the whole encoded file;
- server file reads during `tick()`;
- client disk `.part/.media` writes;
- complete-file-before-decoder requirement;
- correct progressive MP3/WAV decoding;
- audible finite playback through the final engine.

M1F removes the transport problems. M1G replaces the complete-file decoder path.

## Tests and evidence

Pure/unit:

- `FinitePlaybackClockTest.nonLoopingClockReportsNaturalEndAtDuration`
- `FinitePlaybackClockTest.loopingClockNeverReportsNaturalEnd`
- retained exact-end seek, pause/resume, and loop-rebase tests.

Source/test/package CI:

- semantic checkpoint `d0e66ab9135359627086c13647d5241ad778643f`
- run `34658958488`
- NeoForge 21.1.247: success
- NeoForge 21.1.248: success

Latest diagnostic-head CI:

- diagnostic head `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- run `34686003774`
- NeoForge 21.1.247: success
- NeoForge 21.1.248: success

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

The success line has not yet been captured, so do not report M1E runtime PASS.

## Preparation checkpoint

Implementation is intentionally paused before the next acceptance/implementation work.

Read `PRE-M1F-PREPARATION.md` before resuming. M1F is planned as a **clean break** from the modern whole-file prepared bridge, but M1F implementation has not started yet.
