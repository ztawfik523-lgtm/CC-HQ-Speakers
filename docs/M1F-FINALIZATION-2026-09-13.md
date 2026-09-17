# M1F finalization — 2026-09-13

> **Historical M1F completion record.** The transport milestone remains complete at source/test/CI/package/component level, but later audits found shutdown hardening gaps outside the original happy-path/component proof. Current KI-054/KI-064 status lives in `KNOWN-ISSUES.md` / `VERIFIED-FACTS.md`.
>
> Also, M1G is no longer “next, not started”: progressive MP3/common-WAV decode + positional rendering are integrated in current source. Use `CURRENT-STATE.md` for current continuation.

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

The modern prepared-file transport became:

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-authoritative playback state
-> server-selected encoded anchor
-> relevant client requests bounded encoded ranges
-> server reads bounded ranges on background IO
-> client keeps a bounded sliding encoded RAM window
```

A large track no longer requires a same-sized client-side download or complete song file.

M1F intentionally did not own decode/render. M1G now integrates progressive MP3/common-WAV decode, bounded PCM, and audible positional rendering.

## Transport contract

M1F protocol v5 introduced bounded range request/data and authoritative anchors. Current modern protocol is v6 because M1G later added the decode descriptor; the range contract itself remains the same shape.

Current implementation tuning inherited from M1F:

- maximum range: 128 KiB;
- client encoded window: 512 KiB;
- maximum outstanding requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are tuning values, not public API guarantees.

## Sliding encoded window

`FiniteRangeWindow`:

- starts unanchored so BEGIN alone cannot demand byte zero;
- activates when authoritative STATE installs an anchor;
- supports arbitrary reset/re-anchor;
- supports forward sliding/consumption;
- discards consumed prefix;
- preserves useful unread overlap/prefetch;
- rejects obsolete/stale responses;
- opens bounded tail demand/refill;
- distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE;
- keeps memory bounded independently of track duration.

These core M1F facts remain current.

## Shared request validation / async IO

M1F established pure validation for wire range sanity, source/asset/generation identity, bounds, stale completion identity, and relevance.

`FiniteRangeReadService` retains the MediaAsset around accepted asynchronous reads and uses bounded per-player/accounting limits. Completion/cancellation releases the temporary lease through the release owner.

The original component tests proved off-thread read execution, queued cancellation accounting, release retry ownership in active paths, and bounded fake-consumer transfer into the client window.

## Later shutdown correction — KI-054

The original M1F finalization wording was too broad if read as “all shutdown failure paths are solved.”

Two current gaps remain:

1. `MediaAssetStore.close()` clears completed-entry bookkeeping before deletion attempts, so a failed completed-file shutdown deletion cannot be retried from retained entry state;
2. `ServerMediaAssets.closeServer()` calls `FiniteRangeReadService.close()` **before** `MediaAssetStore.close()`. If range close throws, store close and registry removal are skipped, leaving the store root file lock and stopped-server/assets object alive in the JVM.

So the original test result “range service close can be retried” is a component fact, not proof that the current server-stop integration retries it or still reaches store close after failure.

Next-start orphan pruning does not fix a same-JVM lock that is still held.

## Later import correction — KI-064

M1F did not close all import-level hardening either:

- `MediaAssetStore.writeExact()` can spin indefinitely if a source repeatedly returns zero bytes;
- asset publication uses `ATOMIC_MOVE` without fallback when atomic move is unsupported.

These are storage/import hardening issues, not failures of bounded range transport.

## Deterministic/component acceptance from the milestone

M1F's original matrix remains valid for what those tests actually prove:

- exact arbitrary encoded ranges;
- range/bounds identity validation;
- relevance rule validation;
- stale replacement completion rejection;
- per-player outstanding limits;
- in-flight MediaAsset lifetime;
- cancellation accounting/ref cleanup;
- active-path release retry ownership;
- off-thread exact reads;
- authoritative anchor-before-demand;
- arbitrary re-anchor;
- missing bytes != true EOF;
- sliding consume/discard/refill under fixed memory;
- integrated fake range-reader -> client-window exact byte flow;
- no modern complete client song file;
- no modern CHUNK/END whole-file packets;
- removed direct `audioPlayStaged()` prototype.

Do not reinterpret this historical matrix as proof of KI-054/KI-064 resolution.

## Component proof

`FiniteRangeTransportTest` historically exercises:

```text
2 MiB server MediaAsset
-> non-zero range demand
-> async exact server reads
-> bounded client encoded window
-> advance/refill beyond one full window
-> distant re-anchor
-> obsolete old region rejected
-> exact bytes preserved
-> RAM remains bounded
```

This remains useful M1F component evidence.

## Architecture boundaries which remain valid

M1F deliberately has:

- no whole-file server push;
- no complete-song client `.part/.media` cache;
- no Java playlist/application-role policy;
- no client authority over server finite time;
- no full M1H listener discovery/rejoin lifecycle.

M1G decode/render is now integrated rather than “next.”

## Evidence boundary

Record M1F itself as:

```text
M1F source implementation:                 COMPLETE
M1F deterministic/component acceptance:   COMPLETE
M1F NeoForge 21.1.247 CI/package:          PASS
M1F NeoForge 21.1.248 CI/package:          PASS
M1F focused Minecraft transport runtime:   NOT RECORDED
M1F audible playback:                      NOT AN M1F REQUIREMENT
```

Separately record KI-054/KI-064 as later hardening findings rather than retroactively erasing the transport milestone evidence.

Current continuation is M1G correctness/evidence work documented in `CURRENT-STATE.md`, not the original “M1G next” line from this historical checkpoint.
