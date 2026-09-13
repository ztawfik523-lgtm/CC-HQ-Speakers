# Finite streaming design — M1E authority + M1F transport + M1G decoder boundary

This document is the accepted finite-streaming contract after M1F implementation.

## User-facing behavior

A finite file should behave like this:

```text
ComputerCraft file
-> server-owned media asset
-> server-owned playback clock/state
-> relevant client requests only bounded encoded pieces it needs
-> client keeps bounded temporary RAM
-> decoder produces bounded mono PCM
-> positional speaker renderer
```

The server does not wait for a client renderer before time starts. A large song does not require the client to download/cache the whole file.

## M1E semantic authority — implemented

The server owns:

- generation;
- PLAYING / PAUSED / ENDED / ERROR;
- duration and position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

Client READY requests fresh server truth. Client ERROR is diagnostic only.

A slow/broken client never rewrites canonical playback time.

## M1F encoded transport — implemented

M1F source/test/CI checkpoint:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI `34731827907` passed both target NeoForge versions.

Modern transport uses protocol v5:

```text
client -> server:
source + generation + asset + offset + bounded length

server -> client:
source + generation + asset + offset + encoded bytes
```

STATE additionally carries the server-selected encoded anchor:

```text
anchorOffset + anchorTime
```

The client waits for fresh STATE before requesting its first bytes.

### Current bounded values

- response range: max 128 KiB;
- active encoded client window: 512 KiB;
- per-player in-flight requests: max 4;
- per-player in-flight bytes: max 512 KiB;
- server IO workers: 2;
- bounded server IO queue: 64.

These are implementation tuning values and may be benchmarked later.

## Server request validation

A range request is useful only for the current authoritative playback.

The server validates/revalidates:

- source;
- generation;
- asset identity;
- encoded offset/length bounds;
- player connection;
- same dimension/current relevance;
- per-player outstanding limits;
- current session again after asynchronous IO completes.

Stale results are discarded rather than sent.

## Off-thread server IO and asset lifetime

Large asset reads do not run in the Minecraft tick loop.

Before an accepted asynchronous read is queued, the range service takes a separate MediaAsset retain. That retain is released when the read completes or a queued task is cancelled.

Server shutdown must stop/drain range workers before closing/deleting the media store. This ordering is implemented in `ServerMediaAssets`.

## Client encoded window contract

The M1F/M1G boundary is a bounded in-memory encoded window.

It can report:

- `DATA_AVAILABLE`;
- `NEED_DATA`;
- `TRUE_ASSET_EOF`;
- `CANCELLED_OR_STALE`.

This distinction is mandatory. M1G must never pass a temporary `NEED_DATA` condition to an MP3 decoder as permanent EOF.

The window supports arbitrary encoded re-anchors and can discard obsolete history. It is not a persistent cache.

Unanswered/admission-dropped demand can expire and retry. A malformed response cannot wedge the requested span permanently.

## Seeking

Lua seek changes the canonical server position immediately.

Transport behavior is:

```text
seek(T)
-> server clock becomes T
-> STATE selects encoded anchor at/before current T
-> client resets/uses bounded demand around that anchor
-> M1G decoder later pre-rolls/decodes to the audible target
```

The client does not download bytes between the old and new positions just to seek.

### MP3

Frozen/current server analysis already records coarse MP3 encoded seek points.

M1F selects an anchor at/before current server time. M1G owns the exact Layer III pre-roll policy and may deliberately choose/use earlier context to rebuild reservoir state.

### WAV

Current analyzer stores the WAV audio-data start but not the final common-WAV direct PCM layout required for exact time-to-byte seeking.

M1G will extend server metadata with the needed PCM layout and drive the same M1F arbitrary-offset transport. No range-protocol redesign should be necessary.

## No persistent client song cache

The final architecture does not use:

- `.part` song accumulation;
- completed client `.media` song files;
- client LRU media library;
- persistent sparse cache;
- cross-restart partial-download resume.

M1F removed the modern `.part/.media` path.

## Removed prototype path

The project-specific `audioPlayStaged()` command and direct staged playback route are removed.

Staging is only:

```text
CC file -> temporary import -> MediaAsset
```

Public finite workflow remains `hq.playFile()` or prepare/play/release.

## M1G progressive decoder boundary — next, not implemented

M1G must consume M1F without redesigning transport.

Target:

```text
FiniteRangeWindow
-> decoder/converter worker
-> bounded mono PCM queue
-> positional Minecraft/OpenAL renderer
```

M1G owns:

- progressive MP3 decode;
- temporary starvation waiting/refill;
- MP3 reservoir pre-roll;
- common WAV layout/conversion;
- mono output/downmix;
- bounded PCM;
- decoder cancellation;
- actual audible rendering;
- final MP3/common-WAV format narrowing.

Do not repair the old JavaSound/mp3spi complete-file bridge instead.

## Dynamic listener boundary — M1H

M1F server requests/completions are relevance-checked, but complete dynamic listener lifecycle is intentionally later.

M1H owns:

- discovering late entrants;
- proactive leave-range cleanup/cancel;
- return/rejoin at current server time;
- dimension/world/reload recovery;
- underrun refill/rejoin;
- VS2 moving-speaker listener lifecycle.

Do not reintroduce a fixed historical recipient list as a substitute.

## Multispeaker boundary

Later synchronized speakers use shared server clocks without expected-global-member barriers.

Each audible physical speaker still gets its own mono positional renderer. Shared encoded/decode work may be optimized later only after functional multispeaker correctness.

## Non-goals

Do not add:

- music/effects/notification channels;
- Java playlist/album policy;
- persistent client media library;
- surround output from one physical speaker;
- automatic application-level priorities.

Lua remains the application-policy layer.
