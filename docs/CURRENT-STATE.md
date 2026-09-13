# Current state

## Current checkpoint

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

M1G has **started** on:

`codex/m1g-progressive-finite-decode`

M1G preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed NeoForge 21.1.247 and 21.1.248 including tests, packaged-mod verification, and artifact upload.

The chosen M1G architecture is recorded in `M1G-DESIGN-DECISIONS-2026-09-13.md`.

Final M1F source/test candidate remains `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`; final M1F CI remains `34763362365` with both targets green.

Focused real-Minecraft M1F transport acceptance remains unrecorded. Green CI is not runtime proof.

## Current finite architecture

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> server-selected encoded anchor
    -> bounded client-requested encoded ranges (M1F)
    -> bounded sliding client encoded RAM
    -> starvation-aware decoder-worker InputStream (implemented, not integrated)
    -> progressive decoder/converter worker (next M1G integration)
    -> bounded mono signed-16 PCM queue (implemented, not integrated)
    -> Minecraft AudioStream / SoundManager positional renderer (next M1G integration)
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

The owner selected after repeated source/web pressure-testing:

- **A1:** normal Minecraft `AudioStream` / `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout carried to clients;
- **C1:** preserve source sample rate;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- **E1:** coarse conservative MP3 pre-roll using an earlier real seek point.

Important refinements:

- output representation normalizes to mono signed 16-bit PCM while preserving sample rate;
- renderer-facing reads never wait on network/codec work;
- seek/replacement/stop discards the old local decoder/PCM/renderer epoch;
- semantic seek restarts codec state even if the selected encoded anchor byte is unchanged;
- temporary M1F starvation is never decoder EOF;
- client decoder EOF is not canonical server EOF.

See `M1G-DESIGN-DECISIONS-2026-09-13.md`.

## M1G implementation — green start checkpoint

Implemented and tested at `fc99ec093528f1a8d6a975fab52c270e498dbb04`:

- `WavLayout`: normalized common-WAV representation/rate/channels/block-align/data-range facts;
- `CommonWavAnalyzer`: bounded parser for classic common PCM/float WAV plus the chosen narrow WAVEX subset;
- parser respects the declared RIFF container boundary and rejects partial audio frames;
- `ModernFiniteMediaAnalyzer`: modern prepared/local acceptance gate narrowed to MP3 + common WAV;
- `FiniteDecodeDescriptor`: decoder-facing MP3/WAV-only contract;
- `FiniteDecodeAnchorSelector`: exact WAV frame anchors plus E1 MP3 pre-roll anchor selection;
- `MediaMetadata` can carry normalized WAV layout while retaining the historical constructor for inherited paths;
- `HQMediaStaging.prepareAsset(...)` now routes newly prepared assets through the modern MP3/common-WAV gate;
- `FiniteEncodedInputStream`: decoder-worker-only blocking view over `FiniteRangeWindow`; `NEED_DATA` waits, true asset EOF alone returns `-1`, cancel wakes waiters, progressive consumption advances the M1F window;
- the encoded-input wait path rechecks while holding its signal monitor so a range arrival cannot be lost between probe and wait;
- `FinitePcmQueue`: fixed-capacity mono-S16 ring; producer backpressure may block the decoder worker, while renderer reads are nonblocking and distinguish DATA / STARVED / EOF / CANCELLED;
- cancellation discards stale PCM and wakes a blocked producer.

Deterministic tests cover WAV/WAVEX normalization/rejection/bounds, descriptor narrowing, codec-aware anchors, starvation/refill/cancel/true-EOF input behavior, progressive range-window advancement, PCM backpressure, nonblocking starvation, EOF, cancellation, and S16 alignment.

This is a real production start because newly prepared assets already use the M1G acceptance gate. The new encoded-input/PCM primitives are not yet wired into `HQFiniteMediaClient`, so the modern prepared path is still silent.

## Next M1G implementation order

1. carry the MP3/common-WAV descriptor over the modern finite wire and make server STATE use `FiniteDecodeAnchorSelector`;
2. integrate decoder-epoch lifecycle, range-arrival signaling, and encoded-input creation/cancellation into `HQFiniteMediaClient`;
3. progressive common-WAV conversion into `FinitePcmQueue`;
4. progressive JLayer MP3 decode with E1 pre-roll and pre-target discard;
5. A1 Minecraft `AudioStream` / `SoundManager` positional renderer;
6. pause/resume/seek/loop/volume/stop and stale-epoch integration;
7. deterministic/component completion and focused Minecraft audible acceptance.

## M1H boundary

Full late-entry, proactive leave cleanup, return/rejoin, dimension/resource reload recovery, robust underrun rejoin, and final VS2 listener lifecycle remain M1H.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / no recorded PASS
M1F focused Minecraft transport acceptance: not recorded
M1G implementation: started / in progress
M1G current source checkpoint: PASS on both CI targets
M1G audible runtime PASS: not recorded
```

## Current read order

1. `M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1G-START.md`
3. `CURRENT-STATE.md`
4. `TESTING.md`
5. `KNOWN-ISSUES.md`
6. `PRE-M1G-PREPARATION.md` for historical tradeoff analysis only
7. `M1F-FINALIZATION-2026-09-13.md`
8. `VERIFIED-FACTS.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source and current CI

If a new correctness/design choice appears during implementation, stop and ask the owner before selecting among meaningful tradeoffs.
