# CC:HQ Speakers — M1G start handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

## Status

M1G has started. Do not describe it as prepared/not-started anymore.

The owner decision gates are resolved and recorded in `M1G-DESIGN-DECISIONS-2026-09-13.md`:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1 — coarse conservative MP3 pre-roll from an earlier existing seek point.

No further owner choice is required for normal implementation/tuning details. If a new meaningful architecture tradeoff appears, stop and ask before choosing it.

## M1E / M1F evidence remains unchanged

M1E source/test/CI is complete; its final focused Minecraft acceptance was explicitly skipped/unrecorded.

M1F source/test/CI/package + deterministic/component acceptance is complete at:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Focused real-Minecraft M1F transport acceptance remains unrecorded.

## M1G first implementation slice

The first slice establishes the exact media contract before progressive decode/rendering:

- `WavLayout` — normalized common-WAV representation/rate/channels/block alignment/data bounds;
- `CommonWavAnalyzer` — bounded server parser for classic PCM/float and narrow WAVEX PCM/float;
- `ModernFiniteMediaAnalyzer` — prepared/local acceptance gate narrowed to MP3 + common WAV;
- `FiniteDecodeDescriptor` — decoder-facing MP3/WAV-only contract;
- `FiniteDecodeAnchorSelector` — exact WAV frame anchor mapping and E1 MP3 pre-roll selection;
- `MediaMetadata` may now carry normalized WAV layout while keeping the historical constructor for inherited paths;
- `HQMediaStaging.prepareAsset(...)` now uses the modern analyzer, so newly prepared assets are actually gated by the M1G format contract;
- deterministic tests cover WAV/WAVEX normalization, format narrowing, and anchor selection.

The modern client is still transport-only and silent. No progressive decoder, PCM queue, or new renderer has been wired yet.

## Locked implementation semantics

### Renderer

Use the normal Minecraft `AudioStream` / `SoundManager` route. Renderer-facing reads must never wait on network ranges or codec work. The HQ PCM queue, not Minecraft's maximum read request, controls practical ahead-of-time buffering. Seek/replacement/stop should discard the old renderer epoch rather than trying to preserve stale queued PCM.

### PCM format

Normalize finite output to mono signed 16-bit PCM while preserving each source file's sample rate.

### WAV

Core target:

- mono/stereo only;
- U8 PCM;
- S16/S24/S32 PCM;
- F32;
- little-endian RIFF/WAVE;
- narrow WAVEX using PCM/IEEE-float GUIDs and `validBits == containerBits`;
- stereo safely downmixed to mono.

Reject surround, companded/compressed/telephony formats, float64, unusual widths, and differing valid/container widths.

### MP3

Use the exact packaged JLayer family. For seek/rejoin, choose a conservative earlier existing seek point using approximately a one-second safety margin, decode silently forward, and discard output until the audible target/current server time. Do not add fine reservoir-aware seek metadata in M1G.

Temporary M1F `NEED_DATA` must never become JLayer EOF. Decoder waiting may happen only on a decoder worker. Semantic seek always creates a new local decoder epoch even if the encoded anchor byte is unchanged.

## Next implementation slices

1. carry the MP3/common-WAV descriptor over the modern finite wire and make server STATE use codec-aware anchors;
2. add a cancelable starvation-aware encoded-input bridge over `FiniteRangeWindow`;
3. add bounded PCM queue/backpressure and a nonblocking renderer-facing consumer;
4. progressive common-WAV conversion;
5. progressive JLayer MP3 decode/pre-roll/discard;
6. A1 positional Minecraft renderer;
7. controls/stale-epoch lifecycle integration;
8. deterministic/component acceptance and then focused Minecraft audible acceptance.

## M1H boundary

M1H still owns complete late-entry discovery, proactive leave cleanup, leave/return rejoin policy, dimension/resource-reload recovery, robust underrun rejoin, and final VS2 moving-listener lifecycle.

## Read order

1. `M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1G-START.md`
3. `CURRENT-STATE.md`
4. `PRE-M1G-PREPARATION.md` for historical tradeoff analysis only
5. `M1F-FINALIZATION-2026-09-13.md`
6. `TESTING.md`
7. `VERIFIED-FACTS.md`
8. `ROADMAP.md`
9. `LUA-API.md`
10. exact current source and current CI
