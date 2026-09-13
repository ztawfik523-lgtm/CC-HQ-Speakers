# M1F — demand-driven finite transport implementation

Date: 2026-09-13

## Final status

M1F is complete at the **source/test/CI/package** level.

Final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Branch:

`codex/m1f-finalization`

Final CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

The exact completion matrix and artifact hashes are in `M1F-FINALIZATION-2026-09-13.md`.

Focused real-Minecraft M1F transport acceptance is not recorded. M1F audibility is not an M1F requirement.

## Player-facing transport model

```text
server owns the complete MediaAsset
    -> client receives current authoritative server playback state
    -> server chooses an encoded anchor at/before current playback time
    -> client asks for bounded encoded byte ranges from that anchor
    -> server reads those ranges on bounded background IO workers
    -> client keeps a bounded sliding encoded RAM window
```

A large asset no longer implies a same-sized client download or complete client song file.

M1G owns progressive decoding, PCM buffering, and positional rendering.

## Clean break from modern whole-file transport

Current modern prepared path has:

- no `HQFiniteMediaChunkPacket`;
- no `HQFiniteMediaEndPacket`;
- no complete-file server push;
- no client `.part` accumulation;
- no completed client `.media` requirement;
- no `FileFiniteAudioStream` use from `HQFiniteMediaClient`;
- no `audioPlayStaged()` command/direct staged playback path.

Supported local-file workflow remains `hq.playFile()` or prepare/play/release. Temporary staging is import plumbing only.

## Protocol v5

Client request:

```text
source + asset + generation + offset + length
```

Server response:

```text
source + asset + generation + offset + encoded bytes
```

Authoritative STATE additionally carries `anchorOffset` + `anchorTime`.

Current tuning values:

- max range: 128 KiB;
- client encoded window: 512 KiB;
- max outstanding requests/player: 4;
- max outstanding bytes/player: 512 KiB;
- server IO workers: 2;
- server IO queue: 64.

These are implementation values rather than frozen public guarantees.

## Server request path

`FiniteRangeValidation` centralizes pure request/wire/relevance rules.

A request must match:

- current source;
- current asset;
- current generation;
- valid bounded offset/length;
- currently relevant player in the same dimension.

`FiniteRangeReadService` then applies per-player outstanding limits, retains the asset, and runs the exact seekable read on a dedicated bounded executor.

Before response send, `HQFiniteMediaServer` rechecks the current generation/asset, resolves the current player by UUID, and rechecks relevance. Stale replacement, disconnect, removal, wrong-dimension, or out-of-range completion is discarded.

Server-side asset/read failure may enter canonical server ERROR. Client transport/render failure remains diagnostic and never owns canonical playback.

## Asset lifetime and shutdown

Each accepted async range owns a temporary MediaAsset retain independent of prepared/playback ownership.

Completion or queued cancellation transfers release through `MediaAssetReleaseQueue`, so a failed final deletion has a retry owner.

`FiniteRangeReadService.close()`:

- stops new admission;
- `shutdownNow()` cancels queued RangeTasks;
- queued tasks release their lease/accounting;
- waits for running workers;
- does not report closed until outstanding accounting is empty;
- can be called again after a timeout/interruption.

`ServerMediaAssets.closeServer()` closes/drains range IO before release retry/store close. Speaker/peripheral cleanup occurs before shared media shutdown.

## Client range window

`FiniteRangeWindow` now provides the intended progressive producer/consumer boundary.

A new window is unanchored. BEGIN therefore cannot create range demand by itself. Authoritative STATE calls `reset(anchorOffset)` and unlocks demand.

The window supports:

- arbitrary non-zero reset/re-anchor;
- exact bounded requests/responses;
- timeout/retry;
- stale/unsolicited response rejection;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- `advanceTo(newStart)` forward consumption;
- consumed-prefix discard;
- preservation of unread overlapping bytes;
- preservation only of whole still-useful in-flight requests;
- new tail demand after sliding;
- in-place overlap shifting to avoid one-full-window allocation on every small advance;
- hard memory bound independent of full track length.

Seek/re-anchor uses reset because the immutable encoded asset may jump to an unrelated location. Normal progressive decode will use `advanceTo`.

## Deterministic/component proof

Final M1F tests include:

- `FiniteRangeValidationTest`;
- `FiniteRangeReadServiceTest`;
- `FiniteRangeWindowTest`;
- `FiniteRangeTransportTest`.

The integrated fake consumer imports a 2 MiB MediaAsset, requests a non-zero region, performs actual async exact server reads, feeds the bounded client window, advances/refills beyond one full window, jumps to a distant offset, rejects the obsolete region, verifies exact bytes, and remains bounded.

See `TESTING.md` and `M1F-FINALIZATION-2026-09-13.md` for the detailed matrix.

## Seeking

Canonical seek remains server-owned:

```text
seek(T)
-> server clock becomes T
-> STATE selects encoded anchor at/before T
-> client resets transport demand to that anchor
-> M1G later decodes/pre-rolls to audible target
```

Generation does not change for ordinary seek because the encoded asset remains immutable.

MP3 pre-roll/reservoir reconstruction and final common-WAV layout/time-to-byte mapping remain M1G.

## M1H boundary

M1F validates current relevance for range admission/completion. Full proactive listener discovery/lifecycle remains M1H:

- late entrants;
- proactive leave cleanup;
- return/rejoin;
- dimension/world/reload recovery;
- underrun recovery;
- VS2 moving-speaker listener lifecycle.

## Evidence boundary

```text
M1F source implementation: COMPLETE
M1F deterministic/component acceptance: COMPLETE
M1F .247/.248 CI/package: PASS
M1F focused Minecraft transport runtime: NOT RECORDED
M1F audible playback: NOT REQUIRED
```

Next milestone: M1G progressive MP3/common-WAV decode + bounded mono PCM + actual positional renderer.
