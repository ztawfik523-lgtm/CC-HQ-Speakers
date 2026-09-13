# Current state

## Current checkpoint

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

M1G has now **started** on:

`codex/m1g-progressive-finite-decode`

M1G preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

The chosen M1G architecture is recorded in `M1G-DESIGN-DECISIONS-2026-09-13.md`.

Final M1F source/test candidate remains:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI remains `34763362365`, with NeoForge 21.1.247 and 21.1.248 green. M1F documentation-head verification and the requested rerun also passed both targets.

Focused real-Minecraft M1F transport acceptance remains unrecorded. Green CI is not runtime proof.

## Current finite architecture

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> server-selected encoded anchor
    -> bounded client-requested encoded ranges (M1F)
    -> bounded sliding client encoded RAM
    -> progressive decoder/converter worker (M1G, in progress)
    -> bounded mono signed-16 PCM at source sample rate
    -> Minecraft AudioStream / SoundManager positional renderer
```

## M1E evidence boundary

M1E source/test/CI is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

Its final focused Minecraft acceptance was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

Server remains canonical owner of finite PLAYING/PAUSED/ENDED/ERROR, time, seek, loop, volume, and natural EOF.

## M1F evidence boundary

M1F source/test/CI/package and deterministic/component acceptance are complete.

Current modern transport has:

- protocol v5 range request/data at the finalized M1F checkpoint;
- authoritative STATE-selected encoded anchors;
- first demand gated on STATE;
- 128 KiB maximum range;
- 512 KiB client encoded window;
- bounded per-player outstanding work;
- bounded background server reads;
- in-flight MediaAsset lifetime/retry safety;
- stale request/completion discard;
- arbitrary re-anchor;
- forward sliding/discard preserving unread overlap;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- no modern complete-song `.part/.media` client cache;
- no modern finite CHUNK/END whole-file packets;
- no `audioPlayStaged()` route.

Focused real-Minecraft M1F transport acceptance is not recorded. M1F audibility was not required.

## M1G decisions are resolved

The earlier A/B/C decision gates are no longer open. The owner selected, after repeated source/web pressure-testing:

- **A1:** normal Minecraft `AudioStream` / `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout carried to clients;
- **C1:** preserve source sample rate;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- **E1:** coarse conservative MP3 pre-roll using an earlier real seek point.

Important refinements:

- output representation will normalize to mono signed 16-bit PCM while preserving sample rate;
- renderer-facing reads must never wait on network/codec work;
- seek/replacement/stop discards the old local decoder/PCM/renderer epoch;
- semantic seek restarts codec state even if the selected encoded anchor byte is unchanged;
- temporary M1F starvation is never decoder EOF;
- client decoder EOF is not canonical server EOF.

See `M1G-DESIGN-DECISIONS-2026-09-13.md` for the locked rationale and boundaries.

## M1G implementation started

The first M1G slice is the format/layout/anchor foundation.

Implemented on the M1G branch so far:

- `WavLayout`: normalized common-WAV representation/rate/channels/block-align/data-range facts;
- `CommonWavAnalyzer`: bounded server parser for the chosen classic PCM/float + narrow WAVEX subset;
- `ModernFiniteMediaAnalyzer`: modern prepared/local acceptance gate narrowed to MP3 + common WAV;
- `FiniteDecodeDescriptor`: decoder-facing MP3/WAV-only contract;
- `FiniteDecodeAnchorSelector`: exact WAV frame anchors plus E1 MP3 pre-roll anchor selection;
- `MediaMetadata` can now carry normalized WAV layout while keeping the historical constructor for inherited analyzer paths;
- `HQMediaStaging.prepareAsset(...)` now routes newly prepared assets through the modern MP3/common-WAV gate;
- deterministic tests were added for common WAV/WAVEX normalization, decoder format narrowing, and codec-aware anchors.

This is a real production start, not only documentation, because newly prepared assets now use the M1G acceptance gate. The modern client is still silent at this checkpoint; progressive decode, bounded PCM, and renderer integration are the next implementation slices.

## Current M1G implementation order

1. finish/verify the normalized format/layout/anchor foundation;
2. carry the MP3/common-WAV descriptor over the modern finite wire and use codec-aware anchors in server STATE;
3. add a cancelable starvation-aware encoded-input bridge over M1F;
4. add a bounded PCM queue with backpressure and nonblocking renderer reads;
5. add progressive common-WAV conversion;
6. add progressive JLayer MP3 decode with E1 pre-roll/discard;
7. add the A1 Minecraft positional renderer;
8. integrate pause/resume/seek/loop/volume/stop and stale-epoch cancellation;
9. complete deterministic/component acceptance and focused Minecraft audible acceptance.

## M1H boundary

Full late-entry, proactive leave cleanup, return/rejoin, dimension/resource reload recovery, robust underrun rejoin, and final VS2 listener lifecycle remain M1H.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / no recorded PASS
M1F focused Minecraft transport acceptance: not recorded
M1G implementation: started, not source-complete
M1G audible runtime PASS: not recorded
```

## Current read order

1. `M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `HANDOFF-2026-09-13-PRE-M1G.md` (historical preparation context; its owner-choice gate is superseded)
3. `PRE-M1G-PREPARATION.md` (historical tradeoff analysis; decisions are now resolved)
4. `M1F-FINALIZATION-2026-09-13.md`
5. `CURRENT-STATE.md`
6. `KNOWN-ISSUES.md`
7. `TESTING.md`
8. `VERIFIED-FACTS.md`
9. `ROADMAP.md`
10. `M1E-FINITE-STREAMING-DESIGN.md`
11. `LUA-API.md`
12. exact current source and current CI

If a new correctness/design choice appears during implementation, stop and ask the owner before selecting among meaningful tradeoffs.
