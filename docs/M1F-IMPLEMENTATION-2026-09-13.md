# M1F — demand-driven finite transport implementation

Date: 2026-09-13

This is the source/test/CI checkpoint for M1F.

## What changed in player terms

Before M1F, the modern prepared-file path sent the complete encoded file to each client, wrote it into a local `.part/.media` file, and only then handed that complete file to the temporary decoder.

M1F changes the transport model to:

```text
server owns the complete MediaAsset
    -> client receives current server playback state
    -> server chooses an encoded anchor at/before current playback time
    -> client asks for bounded encoded byte ranges from that anchor
    -> server reads those ranges on bounded background IO workers
    -> client keeps only a bounded encoded RAM window
```

A large asset no longer implies a same-sized client download or client disk file.

M1F intentionally does **not** make finite MP3/WAV audible. M1G owns progressive decoding, PCM buffering, and positional rendering.

## Exact checkpoint

Active branch:

`codex/m1f-demand-driven-finite`

Pre-M1F documentation base:

`bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`

Initial M1F transport commit:

`07fb22b7e127f74615cb2f381d04f21fd202e550`

After-review lifecycle/retry hardening:

`2de8398804eb40fbafd2ada79fd6ce188e109b34`

Final code/test head before documentation follow-up:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Final code-head GitHub Actions run:

`34731827907`

Both matrix jobs passed:

- NeoForge 21.1.247 — build/tests, package verification, artifact upload: success;
- NeoForge 21.1.248 — build/tests, package verification, artifact upload: success.

This is source/test/package evidence, not Minecraft runtime proof.

## Clean break from the modern whole-file bridge

The modern prepared path no longer uses the old whole-file transfer.

Removed from the modern M1F path:

- `HQFiniteMediaChunkPacket`;
- `HQFiniteMediaEndPacket`;
- server-tick whole-file chunk reads;
- client `.part` accumulation;
- completed client `.media` files;
- complete-file-before-decoder requirement;
- `FileFiniteAudioStream` usage from `HQFiniteMediaClient`.

`FileFiniteAudioStream` may still exist as old source code for later cleanup, but it is no longer the modern prepared-path transport/decoder bridge.

## Prototype direct-staging API removal

`audioPlayStaged()` has been removed from `HQSpeakerCompositePeripheral` and the corresponding direct staged playback path has been removed from `HQFiniteMediaServer`.

This is intentional. That command came from this project's own early staged-file prototype; it was not an inherited HQ Speakers compatibility API.

The supported finite local-file workflow remains:

```text
hq.playFile(...)
```

or explicitly:

```text
prepareFile -> playPrepared -> releasePrepared
```

Staging remains an import mechanism only.

## Protocol v5

The HQ protocol version is now `5`.

Modern finite transport uses:

- `HQFiniteMediaRangeRequestPacket` — client asks for `source + asset + generation + offset + length`;
- `HQFiniteMediaRangeDataPacket` — server replies with `source + asset + generation + offset + bytes`.

The authoritative STATE packet now also carries:

- `anchorOffset`;
- `anchorTime`.

The client does not need a copy of the server's whole seek index. The server chooses the current encoded anchor and the client requests forward from it.

The client waits for a fresh authoritative STATE before sending its first range request. BEGIN alone never causes an assumed byte-zero download.

## Server range reads

`FiniteRangeReadService` owns bounded off-thread range IO for one running server.

Current implementation bounds are:

- maximum range response: 128 KiB;
- maximum outstanding requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- bounded server IO queue: 64 jobs.

These are current safety/tuning values, not permanent product semantics.

Every accepted range read:

1. validates encoded bounds;
2. reserves per-player outstanding accounting;
3. retains the server MediaAsset before asynchronous IO is queued;
4. opens a seekable asset read off the Minecraft tick thread;
5. reads exactly the bounded requested range;
6. releases the in-flight asset retain and accounting;
7. returns completion to the server thread;
8. rechecks playback generation/asset/player/relevance before any packet is sent.

A completion from an obsolete generation or an irrelevant/disconnected player is discarded.

A server-side asset/read failure may put that playback into server ERROR because that is a failure of the authoritative source itself. Client decoder/render failure remains diagnostic only.

## Shutdown ordering

`ServerMediaAssets` now owns both the media store and its range-read service.

Shutdown order is:

```text
stop/cancel and drain range IO
    -> then close/delete the server MediaAsset store
```

Queued requests release their retained asset references when cancelled. Range-service close is retryable if worker shutdown cannot complete on the first attempt, matching the existing retryable media-store shutdown behavior.

## Bounded client encoded window

`FiniteRangeWindow` is the M1F/M1G boundary.

Current one-source capacity:

512 KiB encoded RAM.

It distinguishes:

- `DATA_AVAILABLE` — requested encoded bytes are present now;
- `NEED_DATA` — bytes are valid but have not arrived yet;
- `TRUE_ASSET_EOF` — requested position is at/past the real encoded asset end;
- `CANCELLED_OR_STALE` — the request/window no longer belongs to the current source position.

That distinction is required so M1G never mistakes temporary network starvation for real decoder EOF.

The window can re-anchor at arbitrary encoded offsets. Re-anchoring drops obsolete data and pending demand without retaining a full-history cache.

Unanswered/admission-dropped range requests time out and become requestable again. A malformed response cannot permanently wedge its requested region.

## Seek-anchor boundary

M1F transports generic encoded offsets; it does not implement codec decoding policy.

Current server analysis provides coarse MP3 seek points, so MP3 STATE can select an encoded point at/before current server time.

Current WAV analysis records the audio-data start rather than the final direct time-to-byte PCM layout. M1G owns the WAV layout metadata/converter and can drive the same arbitrary-offset M1F window without changing the range protocol.

Likewise, exact MP3 Layer III pre-roll policy remains M1G. M1F makes earlier encoded anchors/ranges possible; M1G decides how much earlier context must be decoded silently.

## Tests added

`FiniteRangeWindowTest` covers:

- bounded allocation independent of asset size;
- non-zero/arbitrary encoded offset demand;
- exact returned data;
- re-anchor dropping obsolete data;
- stale-response rejection;
- NEED_DATA versus TRUE_ASSET_EOF;
- request timeout/retry;
- malformed response recovery.

`FiniteRangeReadServiceTest` covers:

- exact arbitrary server range bytes;
- outstanding request/byte accounting;
- in-flight asset retention across release of the playback/prepared owner;
- final in-flight reference release;
- invalid bounds rejection;
- per-player outstanding limit rejection.

Both target NeoForge jobs pass those tests at `934e74b8...`.

## What M1F does not prove

M1F does not prove:

- audible MP3/WAV playback;
- progressive MP3 decode;
- WAV PCM conversion/downmix;
- MP3 reservoir pre-roll correctness;
- PCM queue bounds;
- OpenAL/SoundEngine renderer behavior;
- resource reload behavior;
- late-entry/rejoin lifecycle;
- final leave-range client cleanup;
- multispeaker synchronization.

Those belong to M1G/M1H and later milestones.

In particular, dynamic new-listener discovery and complete leave/re-enter lifecycle remain M1H. M1F already revalidates relevance before serving/sending ranges so a stale asynchronous completion cannot deliver obsolete data to a player who has left relevance.

## Evidence boundary

M1F may be described as **source/test/CI complete** at this checkpoint.

It must not be described as Minecraft-runtime verified unless a real target-stack runtime check is later performed.

M1E's final manual runtime PASS also remains unrecorded because the project owner explicitly chose to skip that test; M1F does not rewrite that history.
