# Current state

## Current checkpoint

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

Current branch:

`codex/m1f-finalization`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, package verification, and artifact upload.

Baseline 21.1.247 M1F JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

Focused Minecraft M1F transport acceptance is not recorded. M1F audibility is not an M1F requirement.

Read `M1F-FINALIZATION-2026-09-13.md` for the exact evidence and acceptance matrix.

## Current finite architecture

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> server-selected encoded anchor
    -> bounded client-requested encoded ranges (M1F)
    -> bounded sliding client encoded RAM
    -> progressive decode/PCM/rendering (M1G)
```

## M1E status

M1E remains complete at source/test/CI level at code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Its final focused Minecraft acceptance script was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

Server authority remains:

- PLAYING / PAUSED / ENDED / ERROR;
- canonical position/duration;
- pause/resume/seek/loop/volume/natural EOF;
- terminal server ERROR freeze;
- client READY as state refresh only;
- client ERROR as diagnostic only;
- best-effort per-recipient projection;
- retry-safe MediaAsset lifetime.

## M1F status

M1F is complete at source/test/CI/package level.

Current behavior/proof includes:

- protocol v5 range request/data;
- authoritative STATE-selected encoded anchors;
- BEGIN alone cannot demand byte zero;
- source/asset/generation identity validation;
- bounded range and asset-offset validation;
- current same-dimension/range relevance checks;
- bounded per-player outstanding work;
- actual encoded reads on dedicated background IO workers;
- in-flight MediaAsset retain and retry-safe release;
- stale generation/asset/player/relevance completion discard;
- shutdown cancellation/accounting cleanup;
- retryable range-service shutdown without early store close;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE distinction;
- arbitrary non-zero anchors;
- sliding consume/discard which preserves useful unread prefetch;
- bounded refill past more than one complete client window;
- component proof moving exact bytes from real `MediaAssetStore` reads into the client range window;
- no modern whole-song `.part/.media` client cache;
- no modern finite CHUNK/END whole-file packets;
- project-prototype `audioPlayStaged()` removed.

Current tuning values are 128 KiB max range, 512 KiB client encoded window, 4 outstanding requests/player, 512 KiB outstanding bytes/player, 2 IO workers, and queue size 64. These are implementation values, not frozen public API.

## M1G status

M1G has not started and is now the next implementation milestone.

M1G owns:

- progressive MP3 decode from the bounded M1F window;
- temporary starvation != decoder EOF;
- Layer III reservoir pre-roll;
- common WAV layout/time-to-byte metadata and conversion;
- stereo-to-mono downmix and >2-channel rejection;
- bounded mono PCM;
- decoder cancellation/replacement;
- actual mono positional Minecraft/OpenAL rendering.

Do not repair the obsolete complete-file JavaSound/mp3spi bridge as the new engine.

## M1H boundary

Full late-entry / proactive leave cleanup / return-rejoin / dimension-resource reload / underrun rejoin remains M1H. M1F validates relevance for each range request/completion but does not implement the full discovery/recovery lifecycle.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / no recorded PASS
M1F focused Minecraft transport acceptance: not recorded
M1F audible playback: not required
M1G: not started
```

Green CI is not runtime proof.

## Current read order

1. `M1F-FINALIZATION-2026-09-13.md`
2. `M1E-FINAL-HARDENING-2026-09-13.md`
3. `CURRENT-STATE.md`
4. `KNOWN-ISSUES.md`
5. `TESTING.md`
6. `VERIFIED-FACTS.md`
7. `M1F-IMPLEMENTATION-2026-09-13.md`
8. `M1E-FINITE-STREAMING-DESIGN.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source/CI
