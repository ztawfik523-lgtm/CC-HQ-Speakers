# M1E+ finite streaming design contract

This is the implementation contract for finite media after frozen M1D.

Historical M1D behavior remains documented in `M1D-MEDIA-ANALYSIS.md` and `VERIFIED-FACTS.md`. This file defines the **replacement architecture**.

## Goal

Play large finite files through the normal ComputerCraft speaker without downloading the whole file to the client first and without keeping a persistent client music cache.

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

Not final targets:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- compressed/telephony/exotic WAV variants which substantially complicate the progressive converter

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

## Server-authoritative semantics

A finite playback exists independently of listeners.

On successful `playPrepared` / `playFile`:

1. validate and retain the server asset;
2. increment playback generation;
3. set canonical position to 0;
4. set state `PLAYING`;
5. start the server clock immediately;
6. send current state to currently relevant clients, if any.

Canonical server state owns:

- source/speaker identity;
- generation;
- asset ID;
- format/encoded size;
- duration;
- position;
- `PLAYING` / `PAUSED` / `ENDED` / `ERROR`;
- loop;
- volume;
- later sync-clock membership.

There is **no server `LOADING` state** for client buffering.

Client READY/STARTED/decoder/render state is not semantic playback state.

## EOF and asset lifetime

Before returning status or applying a control, update/finalize semantic time.

For non-looping playback:

```text
position >= duration
    -> clamp to duration
    -> state = ENDED
    -> stop canonical clock
    -> close/cancel any temporary old transfer still using the asset
    -> release playback asset reference
```

The transfer must not outlive the only asset reference accidentally, especially while the old M1E bridge still uses an open file channel.

For looping playback, position wraps modulo duration and does not naturally enter `ENDED`.

`seek(duration)`:

- non-looping -> immediate `ENDED`;
- looping -> wrap to 0.

## Client status reports

M1E removes renderer authority.

These reports must not mutate canonical server state:

- STARTED
- PAUSED
- RESUMED
- SEEKED
- ENDED

During the temporary old whole-file bridge, READY may remain only as a request/trigger for a fresh state snapshot. READY never starts the clock.

Client ERROR may remain diagnostic. It never ends playback for other listeners.

Delete obsolete status transitions once the bridge no longer needs them.

## Server -> client state snapshot

M1E needs a real semantic snapshot packet rather than trying to infer current truth from BEGIN plus renderer reports.

Conceptual fields:

```text
FiniteStateSnapshot
    sourceId
    generation
    assetId
    format
    encodedSizeBytes
    durationSeconds
    positionSeconds
    state
    looping
    volume
    speaker world/block position
```

Do **not** send `System.nanoTime()` expecting the client to subtract it from its own `nanoTime()`: the JVM clock origins are unrelated.

Send snapshots on at least:

- play/replacement;
- pause/resume;
- seek;
- loop change;
- volume change if volume lives in the snapshot;
- stop/end where useful;
- newly relevant listener;
- temporary READY bridge.

Periodic correction is optional later and should be driven by measured drift, not guessed latency math.

## Temporary M1E bridge

M1E may keep the old full-file transfer briefly to prove server authority.

Required behavior:

```text
server starts at 0 immediately
    -> old transfer runs
    -> client eventually READY
    -> server sends fresh current snapshot
    -> client starts near CURRENT server position
```

If transfer took 8 seconds, audible output starts around the then-current timeline, not 0:00.

If playback ended/stopped/replaced before readiness, stale generation cannot start.

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

Exact packet names may differ.

No TCP-style ACK layer is required. Minecraft's connection is reliable; client demand naturally provides pacing. Keep the number/bytes of outstanding requests bounded.

Generation changes on playback replacement, not on pause/resume/seek/volume/loop. A seek does not make the underlying asset bytes stale.

A stale pre-seek response can be discarded because its encoded range is no longer part of the client's current demand window.

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

Before sending completed IO, hop/re-check:

- active playback/generation;
- player connection/dimension/relevance.

Discard stale work instead of sending it.

No large asset read belongs on the server tick.

## Packet sizing

The existing 256 KiB chunk cap is a valid starting point, not a semantic constant.

Benchmark typical MP3/WAV/FLAC traffic at 64/128/256 KiB with Minecraft packet compression enabled. Already-compressed MP3/FLAC may waste CPU if Minecraft recompresses large payloads for little gain.

Keep packet size easy to tune.

## No client disk cache

Do not build:

- `.part` song files;
- completed client media files;
- LRU music cache;
- cache database;
- sparse range files;
- persistent block files;
- cross-restart download resume.

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

This keeps format analysis/seek indexing authoritative on the server while keeping transport generic byte ranges.

## MP3 contract

Use the shipped JLayer family unless another Java decoder is proven better.

Critical rule:

```text
next encoded byte not received yet
    -> wait/refill on decoder worker
    -> NOT InputStream EOF
```

Temporary network starvation must not be exposed to JLayer as permanent EOF.

### MP3 seek/rejoin

M1D seek points are real scanned MP3 frame offsets.

Layer III uses a bit reservoir, so starting exactly at the audible target frame can decode incorrectly for initial frames. Server chooses an earlier frame anchor; client decodes/discards pre-roll and only exposes PCM around the current canonical position.

The pre-roll policy may start conservative and tighten after tests.

## WAV contract

M1G narrows WAV support to the implemented common formats.

Server metadata needs internal PCM layout such as:

- audio-data offset;
- audio-data length;
- sample encoding;
- bits/sample;
- sample rate;
- channel count;
- encoded frame size.

For uncompressed supported WAV, time -> encoded byte is direct arithmetic. Client requests only the needed frames and converts/downmixs them to mono signed PCM.

Stereo averaging must widen arithmetic before division to avoid overflow.

## FLAC contract

FLAC is a **separate gated M1I extension**, not a blocker for the MP3/WAV engine.

Before advertising native `.flac`, prove:

- byte identification and STREAMINFO metadata;
- duration/total samples;
- mono/stereo validation;
- bounded progressive decoding from server ranges;
- seek/rejoin anchors;
- malformed-input/checksum behavior;
- bounded memory/cancellation;
- packaging on both NeoForge targets;
- Minecraft runtime playback.

Do not add Ogg-FLAC.

If this cannot be done cleanly, leave FLAC unadvertised.

## Decoder/render threading

### Client packet/main thread

- validate generation/source;
- copy/enqueue bounded range data into the in-memory stream buffer;
- schedule semantic renderer changes;
- never block on decoder/network progress.

### Decoder worker

- MP3/FLAC decode;
- WAV conversion/downmix;
- wait for requested-but-not-yet-arrived encoded bytes;
- perform seek pre-roll/discard;
- produce bounded PCM.

### Sound/render thread

Consume already-ready PCM only. Never wait for network, disk, or decoder work.

## Seek behavior

Lua seek changes server truth immediately.

Client:

1. receives new state/current position;
2. drops obsolete encoded demand/PCM;
3. receives/derives the server-selected seek anchor;
4. requests bounded encoded data;
5. pre-rolls/buffers;
6. obtains a fresh current position if catch-up time matters;
7. starts audible output near the then-current server timeline.

The server never pauses while a client catches up.

## Late listener behavior

If server playback is already at 2:00:

- do not transfer 0:00 -> 2:00;
- send current state/seek anchor;
- request bytes near the anchor;
- pre-roll/buffer;
- join current time.

## Underrun behavior

If a client cannot keep up:

- canonical server playback continues;
- local renderer may become silent;
- client refills/re-anchors to current position;
- resume near current time.

Choose stop/recreate vs short bounded silence based on audible runtime tests; semantic rules do not change.

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

One server asset may back many playbacks.

M1J later adds shared server sync clocks and one physical renderer per audible block. M1K may then share active encoded/decode work for identical timelines.

No expected-group-size barrier and no persistent client cache are required.

## Explicitly removed architecture

Do not reintroduce:

- wait-for-READY server start;
- server `LOADING` based on client readiness;
- historical fixed recipient ownership;
- successful-renderer/anchor-client authority;
- renderer-defined canonical EOF;
- 15-second no-renderer failure;
- full client file before first audio;
- persistent client song cache/LRU;
- OGG/AIFF/AU requirements;
- surround/multichannel finite rendering from one block;
- whole decoded track in RAM.

## Milestone mapping

- **M1E:** server-authoritative state/EOF + real state snapshot packet; old transfer only as bridge.
- **M1F:** client-requested encoded range transport + off-thread server IO + seek-anchor/stream descriptors.
- **M1G:** progressive MP3 + common WAV + bounded RAM/mono output + final format narrowing.
- **M1H:** dynamic relevance, late join, leave/re-enter, underrun/rejoin and stale-generation hardening.
- **M1I:** optional/gated native FLAC extension.
- **M1J:** functional multispeaker shared clocks and correct positional sources.
- **M1K:** active-session transfer/decode fan-out optimization.

Then legacy finite migration, RAW finalization, OpenAL cleanup, lifecycle/performance hardening, CI/package verification, and consolidated Minecraft acceptance complete M1.
