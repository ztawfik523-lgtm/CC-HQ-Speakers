# CC:HQ Speakers — M1G start handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI:

`34773448121`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Artifact ZIP digests from that run:

- 21.1.247 artifact `10322129018` — SHA-256 `dc9fe86f88278faa259d821ddd0ba6e57baa1456cae10c27e5d5b93470ec8e85`;
- 21.1.248 artifact `10323026559` — SHA-256 `89923941f23217360682f7f70c86b3872e634dc6b9923fd2d243312baabc3935`.

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

## M1G green start implementation

### Format/layout/anchor foundation

- `WavLayout` — normalized common-WAV representation/rate/channels/block alignment/data bounds;
- `CommonWavAnalyzer` — bounded server parser for classic PCM/float and narrow WAVEX PCM/float;
- parser respects the declared RIFF boundary and rejects data chunks containing partial audio frames;
- `ModernFiniteMediaAnalyzer` — prepared/local acceptance gate narrowed to MP3 + common WAV;
- `FiniteDecodeDescriptor` — decoder-facing MP3/WAV-only contract;
- `FiniteDecodeAnchorSelector` — exact WAV frame anchor mapping and E1 MP3 pre-roll selection;
- `MediaMetadata` may carry normalized WAV layout while preserving the historical constructor for inherited paths;
- `HQMediaStaging.prepareAsset(...)` now uses the modern analyzer, so newly prepared assets are actually gated by the M1G format contract.

### Decoder boundary primitives

- `FiniteEncodedInputStream` is a decoder-worker-only blocking view over `FiniteRangeWindow`;
- DATA_AVAILABLE returns exact bytes;
- NEED_DATA waits/refills and never becomes normal EOF;
- TRUE_ASSET_EOF alone returns `-1`;
- cancel/stale state throws/wakes the decoder epoch;
- progressive consumption advances the M1F window to expose new bounded tail demand;
- the wait path re-probes while holding its signal monitor, preventing a lost range-arrival notification between NEED_DATA detection and `wait()`.

### Bounded PCM primitive

- `FinitePcmQueue` is a fixed-capacity mono-S16 ring;
- decoder writes may wait only for bounded queue capacity;
- renderer-facing reads never wait;
- empty live queue is STARVED, not EOF;
- local physical EOF, cancellation and PCM data are distinct states;
- cancellation discards stale PCM and wakes blocked producers;
- S16 sample alignment is enforced.

Deterministic tests cover all of the above, including starvation/refill, cancel wakeup, true EOF, range-window advancement, PCM producer backpressure, renderer starvation, EOF/cancel distinction, WAV/WAVEX validation, RIFF bounds and codec-aware anchors.

The modern client remains silent because these primitives are **not yet integrated** into an active progressive decoder/renderer session.

## Locked implementation semantics

### Renderer

Use the normal Minecraft `AudioStream` / `SoundManager` route. Renderer-facing reads must never wait on network ranges or codec work. The HQ PCM queue, not Minecraft's maximum read request, controls practical ahead-of-time buffering. Seek/replacement/stop discards the old renderer epoch rather than trying to preserve stale queued PCM.

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

1. carry the MP3/common-WAV descriptor over the modern finite wire and make server STATE use `FiniteDecodeAnchorSelector`;
2. integrate decoder-epoch creation/cancellation and range-arrival signaling into `HQFiniteMediaClient`;
3. progressive common-WAV conversion into `FinitePcmQueue`;
4. progressive JLayer MP3 decode/pre-roll/discard;
5. A1 positional Minecraft renderer;
6. controls/stale-epoch lifecycle integration;
7. deterministic/component completion and then focused Minecraft audible acceptance.

## M1H boundary

M1H still owns complete late-entry discovery, proactive leave cleanup, leave/return rejoin policy, dimension/resource-reload recovery, robust underrun rejoin, and final VS2 moving-listener lifecycle.

## Read order

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
