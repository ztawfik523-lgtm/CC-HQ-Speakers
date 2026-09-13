# M1F — demand-driven finite transport implementation

Date: 2026-09-13

## Reevaluation notice

This document originally recorded M1F as a source/test/CI checkpoint. The implementation facts and green CI below remain valid, but the **completion label is superseded** by `M1E-M1F-REEVALUATION-2026-09-13.md`.

Current status after reevaluation:

**M1F range architecture implemented and CI-green; acceptance/completeness reopened.**

No Java was changed during the reevaluation.

## What changed in player terms

Before M1F, the modern prepared-file path sent the complete encoded file to each client, wrote it into a local `.part/.media` file, and only then handed that complete file to the temporary decoder.

M1F changes the transport model to:

```text
server owns the complete MediaAsset
    -> client receives current server playback state
    -> server chooses an encoded anchor at/before current playback time
    -> client asks for bounded encoded byte ranges from that anchor
    -> server reads those ranges on bounded background IO workers
    -> client keeps a bounded encoded RAM window
```

A large asset no longer implies a same-sized client download or client disk file.

M1F intentionally does **not** make finite MP3/WAV audible. M1G owns progressive decoding, PCM buffering, and positional rendering.

## Exact implementation checkpoint

Active branch:

`codex/m1f-demand-driven-finite`

Pre-M1F documentation base:

`bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`

Initial M1F transport commit:

`07fb22b7e127f74615cb2f381d04f21fd202e550`

Lifecycle/retry hardening:

`2de8398804eb40fbafd2ada79fd6ce188e109b34`

Current Java/source head:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Code-head GitHub Actions run:

`34731827907`

Both matrix jobs passed:

- NeoForge 21.1.247 — build/tests, package verification, artifact upload: success;
- NeoForge 21.1.248 — build/tests, package verification, artifact upload: success.

This is valid source/test/package evidence. It is not a statement that every original M1F acceptance item has a deterministic test, and it is not Minecraft runtime proof.

## Clean break from the modern whole-file bridge

Still source-verified:

- `HQFiniteMediaChunkPacket` removed;
- `HQFiniteMediaEndPacket` removed;
- no server-tick whole-file chunk sender in the modern prepared path;
- no client `.part` accumulation;
- no completed client `.media` file requirement;
- no `FileFiniteAudioStream` use from modern `HQFiniteMediaClient`;
- `audioPlayStaged()` and direct-staged modern playback removed.

The supported local-file workflow remains `hq.playFile()` or prepare/play/release.

## Protocol v5

Current HQ protocol version is `5`.

Modern finite transport uses:

- `HQFiniteMediaRangeRequestPacket` — source + asset + generation + offset + length;
- `HQFiniteMediaRangeDataPacket` — source + asset + generation + offset + bytes.

STATE also carries:

- `anchorOffset`;
- `anchorTime`.

The client waits for authoritative STATE before issuing its first range request.

## Server range reads

`FiniteRangeReadService` owns bounded off-thread range IO for one server.

Current implementation bounds:

- maximum range response: 128 KiB;
- maximum outstanding requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- bounded server IO queue: 64 jobs.

Normal accepted range flow:

1. validate encoded bounds/admission;
2. reserve per-player outstanding accounting;
3. retain MediaAsset before asynchronous queueing;
4. open/read the bounded range on an IO worker;
5. release the in-flight retain/accounting;
6. return completion to the server thread;
7. recheck current session generation/asset/player/relevance before send.

A stale completion from another generation or an irrelevant/disconnected player is discarded by current source.

## Shutdown ordering

`ServerMediaAssets.closeServer()` calls range-service close before media-store close.

Current range-service close cancels queued work, waits for workers, and is structured so a later close call can retry after an initial timeout/interruption.

This is source-reviewed behavior. The reevaluation found that the original contract's requested deterministic shutdown-ordering proof has not yet been added as a dedicated test.

## Client encoded window

Current one-source encoded byte-array capacity is 512 KiB.

`FiniteRangeWindow` distinguishes:

- DATA_AVAILABLE;
- NEED_DATA;
- TRUE_ASSET_EOF;
- CANCELLED_OR_STALE.

It supports arbitrary `reset(anchorOffset)`, bounded request generation, exact response acceptance, timeout/retry, probing/copying, and cancellation.

### Reevaluation: progressive sliding boundary is incomplete

The original pre-M1F contract explicitly described old pieces being discarded as playback moves forward and asked for a fake/test consumer proving consume/discard progression.

Current `FiniteRangeWindow` has no method to advance/discard a consumed prefix while retaining useful unread prefetched bytes.

A future decoder can technically consume the complete current 512 KiB window and then call `reset(windowEnd)`, but this creates a hard refill boundary. If it re-windows earlier, useful unread bytes are discarded and must be requested again.

Therefore the range **protocol** is still suitable, but the client buffer API should not yet be called a finished progressive M1G boundary.

This can be strengthened without changing protocol v5.

## Shared reevaluation: exception/lifetime correctness

M1F inherits `HQFiniteMediaServer` from the M1E authority implementation.

The reevaluation found:

- per-player packet sends are not exception-isolated from authoritative operations;
- `playPrepared()` can leave the installed session inconsistent if a runtime exception occurs after session assignment but before successful return;
- playback asset ownership is marked released before `MediaAssetStore.release()` succeeds;
- a range task which encounters a failed final asset release reports the error but has no surviving retry owner after the task exits.

See `M1E-M1F-REEVALUATION-2026-09-13.md` for exact reasoning.

## Seek-anchor boundary

M1F transports generic encoded offsets; it does not implement decoder policy.

Current server analysis provides coarse MP3 seek points, so MP3 STATE can select an encoded point at/before current server time.

Current WAV analysis records the audio-data start rather than final direct PCM layout. M1G still owns exact common-WAV layout/time-to-byte mapping and can drive the same range protocol.

MP3 reservoir pre-roll also remains M1G.

## What current M1F tests prove

### `FiniteRangeWindowTest`

Covers:

- bounded byte-array allocation;
- non-zero encoded offset demand;
- exact returned data;
- re-anchor dropping old data/demand;
- stale-response rejection;
- NEED_DATA versus TRUE_ASSET_EOF;
- timeout/retry;
- malformed-response recovery.

### `FiniteRangeReadServiceTest`

Covers:

- exact arbitrary server range bytes;
- outstanding request/byte accounting;
- in-flight asset retention across release of the original owner;
- normal final in-flight release;
- invalid bounds rejection;
- configured outstanding-limit rejection.

## Reevaluation: what current tests do not yet prove

The original M1F contract said M1F tests must also prove source/generation/asset validation, relevance/dimension checks, stale completion after replacement/leave/disconnect, cancellation cleanup, shutdown ordering, no tick-thread asset reads, packet sizing, and consume/discard progression.

Current test tree does not contain dedicated `HQFiniteMediaServer` or `HQFiniteMediaClient` component tests for those integration behaviors.

Several items are supported by current source review, but source review is not the deterministic acceptance proof originally requested.

Accordingly, the earlier phrase `M1F source/test/CI complete` is no longer current.

## Runtime evidence boundary

No focused real Minecraft M1F transport acceptance has been recorded.

M1F must not be described as runtime-verified.

A future focused M1F runtime check would validate transport integration rather than audibility.

## What M1F still intentionally does not own

Not reclassified as M1F bugs:

- progressive MP3 decode;
- common WAV conversion/downmix;
- MP3 pre-roll;
- PCM queues;
- OpenAL/SoundEngine renderer behavior;
- full late-entry/leave-return listener lifecycle;
- final resource-reload/dimension recovery;
- multispeaker synchronization.

Those remain M1G/M1H and later work.

## Current status wording

Use:

**M1F range transport implemented and CI-green; acceptance/hardening reopened after reevaluation.**

Do not use:

**M1F fully source/test/CI complete.**

Do not start M1G until the owner chooses among the hardening/defer paths in `M1E-M1F-REEVALUATION-2026-09-13.md`.
