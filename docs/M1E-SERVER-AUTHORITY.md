# M1E — server-authoritative finite playback

## Status

M1E server-authority semantics are implemented and have been re-reviewed on `codex/m1e-server-authoritative-finite`.

Important checkpoints:

- original semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`;
- 2026-09-12 diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`;
- finalization code/test candidate before documentation-only follow-up: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`.

The finalization pass did not change server-authority semantics. It strengthened deterministic coverage and the focused Minecraft acceptance script.

Minecraft runtime acceptance remains the final gate. Do not start M1F until the final candidate actually reports PASS through `scripts/m1e_server_authority_test.lua`.

This milestone deliberately leaves the old whole-file transfer/decoder in place only as a temporary bridge. M1F replaces transport; M1G replaces complete-file client decoding.

## What M1E changed

Before M1E, the staged finite prototype treated renderer readiness/status as playback authority: the server had `LOADING`, `successfulRenderers`, `observed`, a no-renderer timeout, and client STARTED/SEEKED/ENDED reports could rewrite the server clock or EOF.

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

No renderer handshake is required for semantic playback to begin.

## Server semantic states

`HQFiniteMediaServer` uses only:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no canonical server `LOADING` state for client buffering.

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

## Replay and stop

After terminal END, the prepared owner reference still exists unless explicitly released. A new `playPrepared` therefore creates a fresh finite generation against the same prepared asset.

`audioStop()` closes/releases the active playback reference, clears the active finite session, and returns `audioStatus()` to `idle`.

The final runtime acceptance script now checks both of these behaviors explicitly.

## Protocol v4

M1E uses protocol v4.

### BEGIN/setup

The transitional `HQFiniteMediaBeginPacket` carries immutable/setup information needed by the old transfer/renderer:

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

### Client status

`HQFiniteMediaStatusPacket.Transition` is only:

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

The client's local `FinitePlaybackClock` remains only a renderer projection. It is not canonical server truth.

## 2026-09-12 runtime diagnostic

The diagnostic logs repeatedly showed the expected server-side sequence through:

- initial PLAYING;
- pause;
- resume;
- exact-end non-looping ENDED at server duration;
- replay under a new generation;
- loop enable;
- exact-end looping wrap back near zero.

At the same time, the old MP3 decoder returned no PCM on its first read and the client sent diagnostic ERROR. That client failure did not rewrite canonical server state.

The logs did not preserve the ComputerCraft terminal PASS line, so this remains supporting evidence rather than the recorded final runtime PASS.

See `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`.

## Decoder/duration boundary

The temporary JavaSound/mp3spi bridge is not an authority source.

For the tested MP3:

- server analyzer: `161.304 s`;
- JavaSound/mp3spi: `322.584 s`;
- runtime operator: source is roughly 2:45 (~165 s).

The MP3SPI value is clearly wrong. The server analyzer remains canonical for M1E timeline/EOF semantics.

The current MP3 seek bridge also mixes incompatible decoded-byte and compressed-frame/byte skip semantics, may report a requested target after incomplete positioning, and is redundantly seeked during renderer restart.

These are temporary bridge defects. The milestone split remains:

- M1E: server authority;
- M1F: bounded client-pulled encoded transport;
- M1G: progressive MP3/common-WAV decode and audible renderer.

Do not repair the old decoder solely to finish M1E or preserve temporary audibility during M1F.

## Intentionally deferred

M1E does not claim to solve:

- fixed recipient capture;
- dynamic late-range entry;
- server push of the whole encoded file;
- server file reads during `tick()`;
- client disk `.part/.media` writes;
- complete-file-before-decoder requirement;
- correct progressive MP3/WAV decoding;
- final audible finite rendering.

M1F removes the transport problems. M1G replaces the decoder/render path.

## Tests and evidence

Pure/unit coverage now includes:

- `FinitePlaybackClockTest.startedClockAdvancesWithoutRendererHandshake`;
- `FinitePlaybackClockTest.nonLoopingClockReportsNaturalEndAtDuration`;
- `FinitePlaybackClockTest.loopingClockNeverReportsNaturalEnd`;
- exact-end non-looping seek;
- exact-end looping wrap;
- pause/resume;
- loop-disable rebase.

The final focused Minecraft contract is:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav> [result-file]
```

It verifies:

- `playPrepared` becomes server `playing` immediately;
- server position advances without a renderer handshake;
- pause freezes canonical position;
- resume advances again;
- non-looping exact-duration seek enters `ended` at duration;
- replay creates a newer generation for the same prepared asset;
- looping exact-duration seek wraps near zero and stays `playing`;
- `audioStop()` returns finite status to `idle`;
- prepared release succeeds.

The script writes an auditable result file, defaulting to:

```text
m1e_server_authority_result.txt
```

A successful result begins with `PASS`; a failure begins with `FAIL` and records the traceback.

Do not report M1E Minecraft-runtime PASS until the final candidate actually produces PASS.

## Gate to M1F

After M1E runtime PASS is recorded, M1F begins with the agreed clean break from the modern whole-file prepared bridge. M1F does not require audible playback and must not pull M1G decoder work forward merely to preserve this temporary bridge.
