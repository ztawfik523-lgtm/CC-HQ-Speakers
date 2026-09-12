# M1E+ finite streaming design contract

This is the implementation contract for finite media after frozen M1D.

Historical M1D behavior remains documented in `M1D-MEDIA-ANALYSIS.md` and `VERIFIED-FACTS.md`. M1E server-authority behavior is documented in `M1E-SERVER-AUTHORITY.md`. This file defines the **replacement streaming architecture from M1F onward**.

## Goal

Play large finite files through the normal ComputerCraft speaker without downloading the whole file to the client first and without keeping a persistent client music cache in the final path.

```text
ComputerCraft file
    -> server MediaAsset
    -> authoritative server playback clock/state
    -> bounded client-requested encoded ranges
    -> temporary bounded client RAM
    -> progressive decoder/converter worker
    -> bounded mono PCM queue
    -> one positional Minecraft/OpenAL renderer
```

## Final format scope

Committed core targets:

- MP3 / MPEG Layer III
- common WAV

Gated extension:

- normal native FLAC (`.flac`), only after its exact analyzer/decoder/seek/package path is proven

Not final targets: OGG Vorbis, Ogg-FLAC, AIFF/AIF, AU/SND, and compressed/telephony/exotic WAV variants which substantially complicate the progressive converter.

Frozen M1D may continue to analyze old formats until M1G replaces the prepared/local decoder surface. Historical support does not create a permanent requirement.

## Channel/sample policy

One physical HQ speaker produces one **mono positional** source.

Accepted input:

- mono -> mono;
- stereo -> safe downmix to mono;
- >2 channels -> reject.

Core WAV target:

- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float.

The final WAV advertisement must match the converter actually implemented.

## M1E implementation checkpoint

M1E source/test/CI is complete at code-bearing head:

`d0e66ab9135359627086c13647d5241ad778643f`

GitHub Actions run `34658958488` passed NeoForge 21.1.247 and 21.1.248. Minecraft runtime acceptance remains pending.

Implemented M1E semantics:

- server state is `PLAYING`, `PAUSED`, `ENDED`, or `ERROR`;
- no server `LOADING` state exists for client buffering;
- successful finite play starts canonical time immediately;
- playback advances with zero listeners;
- renderer/observation authority and its 15-second timeout are removed;
- server duration/clock owns natural EOF;
- exact-duration seek semantics are server-owned;
- temporary transfer lifetime is closed before its playback asset reference is released at canonical EOF;
- protocol v4 includes an authoritative server -> client STATE packet;
- client -> server finite status is only READY plus diagnostic ERROR;
- the temporary whole-file client waits for fresh STATE after READY and starts at current server time rather than 0.

M1E intentionally does **not** replace transport or progressive decoding. Fixed recipients, whole-file server push, server-tick reads, client temp files, and complete-file decoding remain until M1F/M1G.

## Server-authoritative semantics

A finite playback exists independently of listeners.

On successful `playPrepared` / `playFile`:

1. validate and retain the server asset;
2. increment playback generation;
3. set canonical position to 0;
4. set state `PLAYING`;
5. start the server clock immediately;
6. send setup/state to currently selected transitional recipients, if any.

Canonical server state owns source/speaker identity, generation, asset ID, duration, position, semantic state, loop, volume, and later sync-clock membership.

Client transfer/decoder/render readiness is not semantic playback state.

## EOF and asset lifetime

Before returning status or applying controls, update/finalize semantic time.

For non-looping playback:

```text
position >= duration
    -> clamp to duration
    -> state = ENDED
    -> stop canonical clock
    -> close/cancel any temporary old transfer still using the asset
    -> release playback asset reference
```

For looping playback, position wraps modulo duration and does not naturally enter `ENDED`.

`seek(duration)`:

- non-looping -> immediate `ENDED`;
- looping -> wrap to 0.

## Client -> server finite status

Protocol v4 deliberately has only:

- `READY` — the temporary complete-file bridge can construct its decoder and requests a fresh authoritative state;
- `ERROR` — local diagnostic telemetry only.

The old `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, and `ENDED` renderer status transitions were removed in M1E. A client renderer cannot update canonical server position/state/EOF.

A client ERROR never ends playback for other listeners.

## Server -> client packet split

M1E keeps immutable setup separate from mutable semantic truth.

### BEGIN/setup

The existing `finite_begin` packet is still used by the temporary whole-file bridge. It carries setup needed before transfer/rendering:

```text
sourceId
mediaId
generation
format
initial volume
speaker world position
speaker block position
total encoded bytes
initial looping
initial paused flag
```

It is not the canonical moving clock.

### STATE

The `finite_state` packet carries authoritative mutable truth:

```text
sourceId
mediaId
generation
PLAYING | PAUSED | ENDED | ERROR
positionSeconds
durationSeconds
volume
looping
error detail
```

Format, encoded size, and speaker position do not need to be duplicated in every STATE because BEGIN/setup already carries them for the transitional renderer. M1F may introduce a different stream descriptor when the old BEGIN/chunk/end transport is replaced.

Do **not** send `System.nanoTime()` expecting the client to subtract it from its own `nanoTime()`: JVM clock origins are unrelated.

STATE is sent on play/setup and semantic/control changes, and READY explicitly requests a fresh snapshot. Dynamic new-listener state delivery belongs to M1H once listener relevance is no longer a fixed recipient set.

Periodic correction is optional later and should be driven by measured drift rather than guessed latency math.

## Temporary M1E bridge

M1E keeps the old full-file transfer only long enough to prove server authority:

```text
server starts at 0 immediately
    -> old transfer runs
    -> client eventually READY
    -> server sends fresh current STATE
    -> client starts near CURRENT server position
```

If transfer took 8 seconds, audible output starts around the then-current timeline, not 0:00. If playback ended/stopped/replaced before readiness, stale generation cannot start.

This disk-backed bridge is explicitly temporary and is deleted by M1F/M1G.

## M1F range protocol

Final finite transfer is demand-driven.

Conceptual request:

```text
FiniteRangeRequest
    sourceId
    generation
    assetId
    offset
    length
```

Conceptual response:

```text
FiniteRangeData
    sourceId
    generation
    assetId
    offset
    bytes
```

No TCP-style ACK layer is required. Minecraft's connection is reliable; client demand naturally provides pacing. Keep outstanding request count/bytes bounded.

Generation changes on playback replacement, not on pause/resume/seek/volume/loop. A seek does not make the underlying asset bytes stale; obsolete pre-seek responses are simply outside the current demand window and may be discarded.

## Server range validation/threading

On the server thread, before scheduling IO:

1. active finite playback exists;
2. generation matches;
3. asset matches;
4. player is connected, same dimension, currently relevant/in range;
5. offset/length are valid and bounded;
6. per-player outstanding/rate limits allow it;
7. playback still owns the asset.

Then retain a safe read reference and perform the file read on a bounded IO executor.

Before sending completed IO, re-check active playback/generation and player connection/dimension/relevance. Discard stale work instead of sending it.

No large asset read belongs on the server tick.

## Packet sizing

The existing 256 KiB chunk cap is a valid starting point, not a semantic constant. Benchmark typical encoded traffic at 64/128/256 KiB with Minecraft packet compression enabled and keep packet size easy to tune.

## No client disk cache

The final path does not build `.part` song files, completed client media files, an LRU music cache, cache database, sparse range files, persistent range block files, or cross-restart resume.

The server owns the authoritative finite file. If a client later needs an old portion again, it asks the server again.

Client memory contains only active needs:

- bounded encoded bytes;
- codec-specific short pre-roll/context;
- bounded decoded mono PCM.

A 500 MiB file must not imply 500 MiB client RAM or disk.

## Seek-anchor rule

Clients do **not** need the complete server seek index.

For initial playback, late join, or seek, the server may provide a codec-appropriate anchor:

```text
anchorTimeSeconds
anchorByteOffset
codec/layout facts as needed
```

The anchor is at/before the desired canonical position. The client requests forward from it and performs decoder-specific pre-roll/discard.

## MP3 contract

Use the shipped JLayer family unless another Java decoder is proven better.

Critical rule:

```text
next encoded byte not received yet
    -> wait/refill on decoder worker
    -> NOT InputStream EOF
```

M1D seek points are real scanned MP3 frame offsets. Layer III uses a bit reservoir, so starting exactly at the audible target frame can decode incorrectly for initial frames. Server chooses an earlier frame anchor; client decodes/discards pre-roll and only exposes PCM around the current canonical position.

## WAV contract

M1G narrows WAV support to the implemented common formats. Server metadata needs internal PCM layout such as audio-data offset/length, sample encoding, bits/sample, sample rate, channel count, and encoded frame size.

For uncompressed supported WAV, time -> encoded byte is direct arithmetic. Client requests only needed frames and converts/downmixes them to mono signed PCM. Stereo averaging must widen arithmetic before division to avoid overflow.

## FLAC contract

FLAC is a **separate gated M1I extension**, not a blocker for the MP3/WAV engine.

Before advertising native `.flac`, prove byte identification/STREAMINFO metadata, duration/total samples, mono/stereo validation, bounded progressive decoding from server ranges, seek/rejoin anchors, malformed-input/checksum behavior, bounded memory/cancellation, packaging on both NeoForge targets, and Minecraft runtime playback.

Do not add Ogg-FLAC. If this cannot be done cleanly, leave FLAC unadvertised.

## Decoder/render threading

### Client packet/main thread

Validate generation/source, enqueue/copy bounded range data into the in-memory stream buffer, and schedule semantic renderer changes. Never block on decoder/network progress.

### Decoder worker

Perform MP3/FLAC decode or WAV conversion/downmix, wait for requested-but-not-yet-arrived encoded bytes, perform seek pre-roll/discard, and produce bounded PCM.

### Sound/render thread

Consume already-ready PCM only. Never wait for network, disk, or decoder work.

## Seek behavior

Lua seek changes server truth immediately. Client drops obsolete demand/PCM, uses a server-selected codec anchor, requests bounded encoded data, pre-rolls/buffers, and begins audible output near the then-current server timeline. The server never pauses while a client catches up.

## Late listener behavior

If server playback is already at 2:00, do not transfer 0:00 -> 2:00. Send current state/seek anchor, request bytes near the anchor, pre-roll/buffer, and join current time.

## Underrun behavior

If a client cannot keep up, canonical server playback continues. Local rendering may become silent; the client refills/re-anchors and resumes near current time. Choose stop/recreate vs short bounded silence based on audible runtime tests; semantic rules do not change.

## Resource limits

Protect expensive resources rather than arbitrary Lua program counts:

- max range request length;
- max outstanding request count/bytes per Minecraft player;
- per-player byte rate limit if needed;
- bounded server IO pool/queue;
- existing server asset/staging quotas;
- bounded client encoded/PCM buffers.

Do not add a low static "finite sessions per ComputerCraft computer" cap without profiling evidence.

## Multispeaker direction

One server asset may back many playbacks. M1J later adds shared server sync clocks and one physical renderer per audible block. M1K may then share active encoded/decode work for identical timelines. No expected-group-size barrier and no persistent client cache are required.

## Explicitly removed architecture

Do not reintroduce wait-for-READY server start, server `LOADING` based on client readiness, successful-renderer/anchor-client authority, renderer-defined canonical EOF, 15-second no-renderer failure, renderer STARTED/SEEKED/etc. authority, full client file before first audio in the final path, persistent client song cache/LRU, OGG/AIFF/AU requirements, surround finite rendering from one block, or whole decoded track in RAM.

## Milestone mapping

- **M1E:** source/test/CI complete — server-authoritative state/EOF + real STATE packet; old transfer remains only as bridge; Minecraft runtime acceptance pending.
- **M1F:** client-requested encoded range transport + off-thread server IO + seek-anchor/stream descriptors.
- **M1G:** progressive MP3 + common WAV + bounded RAM/mono output + final format narrowing.
- **M1H:** dynamic relevance, late join, leave/re-enter, underrun/rejoin and stale-generation hardening.
- **M1I:** optional/gated native FLAC extension.
- **M1J:** functional multispeaker shared clocks and correct positional sources.
- **M1K:** active-session transfer/decode fan-out optimization.

Then legacy finite migration, RAW finalization, OpenAL cleanup, lifecycle/performance hardening, CI/package verification, and consolidated Minecraft acceptance complete M1.
