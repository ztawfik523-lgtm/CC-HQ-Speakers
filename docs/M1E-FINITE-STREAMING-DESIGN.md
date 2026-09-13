# M1E+ finite streaming design contract

This is the implementation contract for finite media from M1F onward.

Read `HANDOFF-2026-09-13-PRE-M1F.md`, `LUA-API.md`, and `CURRENT-STATE.md` first for the current checkpoint and user-facing API.

## Goal in player terms

Large local files should behave like this:

```text
ComputerCraft has a file
    -> server stores one authoritative encoded copy
    -> speaker playback starts on the server timeline
    -> each relevant client asks for only the small encoded pieces it currently needs
    -> the client keeps bounded temporary RAM
    -> later M1G decodes those pieces into positional sound
```

A 500 MiB song must not imply 500 MiB of client RAM/disk or a full download before useful work begins.

## Current checkpoint

M1E server-authority source/tests/CI are finalized and re-reviewed.

The final manual M1E Minecraft acceptance script was prepared but not run. The project owner chose to skip that manual test. Therefore M1E has no recorded final runtime PASS, but that skipped run is no longer treated as a required sequencing gate before future M1F work.

M1F implementation has **not started** at the current documentation checkpoint.

## Final finite product scope

Committed core targets:

- MP3 / MPEG Layer III
- common WAV

Separately gated later:

- normal native FLAC

Not final core targets:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- unusual/compressed/telephony WAV variants
- >2-channel finite input

One physical speaker ultimately renders one mono positional source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

## Server-authoritative semantics inherited from M1E

A finite playback exists independently of listeners.

On successful prepared play:

1. validate/retain the server asset;
2. create a new playback generation;
3. set canonical position to 0;
4. enter PLAYING;
5. start the server clock immediately;
6. client readiness/failure remains non-authoritative.

The server owns source/speaker identity, generation, asset ID, duration, position, semantic state, loop, volume, and later sync-clock membership.

For non-looping playback, reaching duration enters ENDED. Looping wraps. Exact-duration seek ends when non-looping and wraps to zero when looping.

Client READY only asks for a current state snapshot. Client ERROR is diagnostic only.

## Modern Lua/API path

Recommended programs use:

```text
hq.playFile
hq.prepareFile
hq.preparedInfo
hq.playPrepared
hq.releasePrepared
```

with the capability-oriented `audio*` finite controls.

The temporary writable mount is import plumbing, not the playback model.

### `audioPlayStaged()` removal decision

`audioPlayStaged()` was introduced by this project during the staged/local-file prototype. It is not original HQ Speakers compatibility.

When M1F implementation begins, remove this direct-staged playback command rather than maintaining a second modern transport path.

New programs use `hq.playFile()` or prepare/play/release.

## M1F clean-break transport

M1F replaces the modern prepared path's old whole-file push/client-file bridge.

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

Exact class names are not frozen.

The modern prepared path should stop depending on:

- fixed recipients captured only at play start;
- server tick-thread whole-file reads;
- push of the entire encoded asset;
- client `.part`/`.media` song files;
- complete local file before useful client work;
- `FileFiniteAudioStream` as the modern transport consumer;
- `audioPlayStaged()` as a direct temporary-file playback route.

Legacy classes may remain elsewhere until their scheduled migration milestones.

## Client-pulled behavior

The server owns the complete file. The client asks for bounded encoded ranges near what it currently needs.

The next request naturally provides pacing; no extra TCP-like ACK layer is required.

Generation changes on playback replacement, not ordinary pause/resume/seek/volume/loop changes. Seek changes demand/window relevance rather than changing the underlying asset bytes.

A stale pre-seek response may simply be discarded when it no longer intersects the current demand window.

## Server validation

Before scheduling a read, validate on the server thread:

- active finite playback exists;
- source/generation match;
- asset matches;
- player is connected;
- dimension/relevance are valid for the current milestone rules;
- offset/length are valid and bounded;
- outstanding request count/bytes remain within limits;
- playback still owns the asset.

Then retain safe asset ownership and perform file IO on a bounded background executor/queue.

Before sending completed work, re-check current playback/generation/player relevance. Discard stale work instead of sending it.

No large asset read belongs on the server tick.

## Shutdown/lifetime rule

Background M1F reads must not outlive the shared media store.

During server shutdown/unload cleanup, stop/drain/cancel the range IO work and release in-flight asset references before the server asset store closes/removes its files.

This lifecycle ordering must be covered by deterministic tests.

## Packet sizing

Response packets remain bounded. The old 256 KiB chunk size is a reasonable starting point, not a semantic constant.

Keep the cap easy to tune and benchmark practical sizes with Minecraft packet compression before final performance tuning.

## No persistent client song cache

The final path does not build:

- `.part` song files;
- completed client media files;
- an LRU music cache;
- a cache database;
- sparse persistent range files;
- cross-restart download resume.

Client memory contains only active needs:

- bounded encoded bytes;
- short codec-specific seek/pre-roll context later;
- bounded decoded PCM later.

If old data is needed again, the client asks the server again.

## Client encoded-window contract

M1F must expose enough semantics for M1G without transport redesign.

The encoded-data layer must distinguish conceptually:

```text
DATA_AVAILABLE
NEED_DATA / NOT_ARRIVED_YET
TRUE_ASSET_EOF
CANCELLED_OR_STALE
```

Exact names are not frozen.

Temporary network starvation must never be represented as permanent EOF.

A deterministic fake/test consumer is sufficient for M1F.

Example proof:

```text
request a non-zero-offset range
-> verify exact bytes
-> consume/discard it
-> jump to a distant offset
-> verify obsolete bytes are gone
-> verify client memory stays within its configured bound
```

## Seek-anchor rule

Clients do not need the complete server seek index.

For initial playback, seek, or later rejoin, the server may provide a codec/layout anchor at or before the desired canonical time:

```text
asset ID
generation/source identity
anchor media time
anchor encoded byte offset
minimal codec/layout facts needed later
```

M1F only needs to make requesting from that anchor possible.

MP3 bit-reservoir reconstruction and silent decode/discard pre-roll are M1G.

## M1F acceptance boundary

M1F PASS means the transport is correct and bounded. It does **not** mean the song is audible.

M1F tests must prove at least:

- request offset/length bounds;
- source/generation/asset validation;
- relevance/dimension checks for the milestone;
- bounded outstanding request count/bytes;
- safe in-flight asset lifetime;
- stale completion discard after replacement/leave/disconnect;
- cancellation/accounting/ref cleanup;
- shutdown ordering with background reads;
- no large tick-thread asset reads;
- bounded response packet size;
- exact returned bytes;
- arbitrary encoded offsets;
- bounded client encoded RAM independent of full asset size;
- no modern client `.part/.media` requirement;
- availability vs missing-data vs EOF vs stale distinction;
- `audioPlayStaged()` no longer exists as a modern direct-staged path.

## M1G MP3 contract

M1G uses the shipped JLayer family unless another decoder is proven better.

Critical rule:

```text
next encoded byte not received yet
    -> wait/refill on decoder worker
    -> NOT permanent EOF
```

Layer III seek/rejoin starts earlier than the audible target because frame decoding may depend on previous main-data reservoir state. M1G decodes/discards pre-roll before audible output.

## M1G WAV contract

For common uncompressed WAV, server metadata needs enough layout for direct time-to-byte mapping: audio data offset/length, sample encoding, bits/sample, sample rate, channels, and encoded frame size.

Supported final target:

- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float;
- mono/stereo only.

Stereo downmix uses widened arithmetic to avoid overflow.

## FLAC contract

Native FLAC remains a separate M1I extension. Do not advertise it until analyzer, metadata, progressive decode, seek/rejoin, malformed-input/checksum behavior, bounded memory/cancellation, package behavior, and Minecraft runtime playback are proven.

Do not add Ogg-FLAC.

## Decoder/render threading

### Client packet/main thread

Accept/validate bounded range data and schedule state changes. Never block waiting for network/decoder progress.

### Decoder worker

M1G performs MP3/WAV work, waits for temporarily missing encoded bytes, performs seek pre-roll/discard, and produces bounded PCM.

### Sound/render thread

Consumes already-ready PCM only. Never waits for network, disk, or decoder refill.

## Underrun and late entry

If a client cannot keep up, the server timeline continues. Local rendering may go silent/refill and later rejoin current server time.

A late listener should request data near current server time rather than downloading from the beginning.

Dynamic new-listener discovery/rejoin correctness is M1H, but M1F transport must already support arbitrary range offsets and current-state anchoring.

## Resource limits

Protect expensive resources rather than arbitrary Lua-program counts:

- max range request length;
- max outstanding requests/bytes per player;
- per-player rate limit if needed;
- bounded server IO pool/queue;
- existing server asset/staging quotas;
- bounded client encoded/PCM buffers.

Do not add a low static finite-session-per-computer cap without profiling evidence.

## Milestone mapping

- **M1E:** server authority/source tests/CI finalized; final manual Minecraft PASS skipped/unrecorded.
- **M1F:** client-requested encoded range transport + off-thread server IO + bounded client encoded window; remove `audioPlayStaged()`; no audible requirement.
- **M1G:** progressive MP3 + common WAV + bounded PCM + actual audible positional output.
- **M1H:** dynamic relevance, late join, leave/re-enter, underrun/rejoin hardening.
- **M1I:** optional/gated native FLAC.
- **M1J:** functional multispeaker shared clocks and correct physical renderers.
- **M1K:** active-session transfer/decode fan-out optimization.

Then legacy finite migration, RAW finalization, OpenAL cleanup, lifecycle/performance hardening, package verification, and consolidated Minecraft acceptance complete M1.