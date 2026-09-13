# M1F finalization — 2026-09-13

## Result

M1F demand-driven finite encoded transport is complete at the **source/test/CI/package** level.

Final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Branch:

`codex/m1f-finalization`

Final GitHub Actions run:

`34763362365`

Both target jobs passed:

- NeoForge 21.1.247 — build/tests/package verification/artifact upload: PASS;
- NeoForge 21.1.248 — build/tests/package verification/artifact upload: PASS.

This is not a claimed real-Minecraft runtime PASS. Focused Minecraft M1F transport acceptance remains unrecorded.

## Exact 21.1.247 candidate

Artifact:

- artifact id: `10319592968`;
- artifact ZIP SHA-256: `b971f027cad9c22265e3080f17725859fa447ce71cd1d2d0a14f2d1710b1c131`;
- JAR: `hqspeaker-1.1.4-1.21.1-neoforge.jar`;
- JAR SHA-256: `2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`.

NeoForge 21.1.248 artifact:

- artifact id: `10319563040`;
- artifact ZIP SHA-256: `131a250297ec3570c1ed1e0c61cc1d6b7569dff8ecc86d80080cb1ad345ff062`.

## Player-facing behavior established by M1F

The modern prepared-file path is now:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative playback state
    -> server-selected encoded anchor
    -> nearby client requests only bounded encoded ranges it needs
    -> server reads those ranges on bounded background IO
    -> client keeps only a bounded sliding encoded RAM window
```

A large track no longer requires a same-sized client-side download or complete song file.

M1F intentionally does not decode/render the song. M1G owns progressive MP3/common-WAV decode, PCM buffering, and audible positional rendering.

## Final transport contract

Protocol v5 modern finite transport uses:

```text
client -> server:
source + asset + generation + offset + bounded length

server -> client:
source + asset + generation + offset + encoded bytes
```

Authoritative STATE also carries the server-selected encoded anchor.

Current implementation bounds are tuning values, not frozen public API:

- maximum range: 128 KiB;
- client encoded window: 512 KiB;
- maximum outstanding requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

## What the finalization pass fixed

### Sliding encoded window

`FiniteRangeWindow` now:

- starts unanchored, so BEGIN by itself cannot create byte-zero demand;
- activates only when authoritative STATE supplies an anchor;
- supports arbitrary reset/re-anchor for seek/rejoin;
- supports forward `advanceTo(...)` progression;
- discards consumed prefix bytes;
- preserves useful unread overlapping bytes;
- preserves only whole still-useful in-flight requests;
- rejects obsolete/stale responses;
- opens new bounded demand at the tail;
- shifts overlap in place instead of allocating another full window on every small advance;
- distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

### Shared request validation

`FiniteRangeValidation` is the pure shared rule for:

- wire range sanity;
- source/asset/generation identity;
- asset offset/length bounds;
- stale completion identity;
- same-dimension/current-range relevance.

Range request/data packets and the server request path use the same 128 KiB maximum.

### Server IO/lifetime/shutdown

`FiniteRangeReadService` retains the MediaAsset before accepted async work enters the executor. Completion/cancellation releases the temporary lease through the shared retry-safe release owner and drops per-player accounting.

The final tests also prove:

- production range completion runs on the dedicated `hqspeaker-range-io-*` worker rather than the submitting/server thread;
- queued shutdown cancellation releases lease/accounting;
- a failed in-flight final release transfers to `MediaAssetReleaseQueue` and later succeeds;
- shutdown timeout leaves the media store intact and `close()` can be retried;
- the normal server shutdown order still closes/drains range IO before closing the asset store.

## Deterministic/component acceptance matrix

| Requirement | Final evidence |
| --- | --- |
| exact arbitrary encoded ranges | `FiniteRangeReadServiceTest` |
| max packet/range bound | `FiniteRangeValidationTest` + packet delegation to shared rule |
| source/generation/asset identity | `FiniteRangeValidationTest` + server use of validator |
| asset offset/length bounds | `FiniteRangeValidationTest` + read-service bounds |
| same-dimension/current relevance rule | `FiniteRangeValidationTest` + server `isRelevant` use |
| stale replacement completion rejection | `FiniteRangeValidationTest` + server completion use |
| disconnected/out-of-range completion discard | current server player lookup + tested relevance rule |
| per-player outstanding limits | `FiniteRangeReadServiceTest` |
| in-flight MediaAsset lifetime | `FiniteRangeReadServiceTest` |
| cancellation accounting/ref cleanup | `FiniteRangeReadServiceTest` |
| release-failure retry ownership | `FiniteRangeReadServiceTest` |
| shutdown drain/retry safety | `FiniteRangeReadServiceTest` + `ServerMediaAssets` ordering |
| reads off server tick/submitting thread | `FiniteRangeReadServiceTest` |
| BEGIN cannot demand before STATE | unanchored `FiniteRangeWindowTest` + client `anchorReady` gate |
| arbitrary non-zero anchor | `FiniteRangeWindowTest` |
| seek/re-anchor drops obsolete demand/data | `FiniteRangeWindowTest` |
| missing network bytes != true EOF | `FiniteRangeWindowTest` |
| sliding consume/discard preserving prefetch | `FiniteRangeWindowTest` |
| progress/refill beyond one window while bounded | `FiniteRangeWindowTest` |
| integrated fake range-reader -> client-window flow | `FiniteRangeTransportTest` |
| no modern complete client song file | source review of `HQFiniteMediaClient` |
| no modern CHUNK/END whole-file packets | current source-tree review |
| direct `audioPlayStaged()` prototype removed | current composite/source review |

## Component proof

`FiniteRangeTransportTest` exercises the M1F core without Minecraft rendering:

```text
2 MiB server MediaAsset
-> non-zero range demand
-> actual async exact server reads
-> bounded client encoded window
-> advance/refill beyond one full window
-> distant re-anchor/jump
-> obsolete old region rejected
-> exact bytes preserved
-> RAM remains bounded
```

This is the fake-consumer proof requested by the original M1F design.

## Recheck result

After the final implementation/test pass, no new M1F architecture/correctness choice with meaningful tradeoffs was found.

The modern path still intentionally has:

- no whole-file server push;
- no complete-song client `.part/.media` cache;
- no Java playlist/application-role policy;
- no client authority over the server playback timeline;
- no M1G decoding/rendering work pulled into M1F;
- no M1H full listener discovery/rejoin lifecycle pulled into M1F.

## Evidence boundary

Record current status exactly as:

```text
M1F source implementation:                 COMPLETE
M1F deterministic/component acceptance:   COMPLETE
M1F NeoForge 21.1.247 CI/package:          PASS
M1F NeoForge 21.1.248 CI/package:          PASS
M1F focused Minecraft transport runtime:   NOT RECORDED
M1F audible playback:                      NOT AN M1F REQUIREMENT
```

The next implementation milestone is M1G progressive MP3/common-WAV decode and audible positional rendering. Full dynamic late-entry/leave-return/rejoin remains M1H.
