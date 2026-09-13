# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G implementation started.**

Active branch:

`codex/m1g-progressive-finite-decode`

M1G preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed both NeoForge 21.1.247 and 21.1.248 including tests, package verification, and artifact upload.

Final M1F source/test candidate remains `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`; final M1F CI is `34763362365`.

Focused Minecraft M1F transport acceptance is not recorded. Never convert CI into a runtime PASS.

Read before continuing M1G:

1. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1G-START.md`
3. `docs/CURRENT-STATE.md`
4. `docs/TESTING.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/PRE-M1G-PREPARATION.md` for historical tradeoff analysis only
7. `docs/M1F-FINALIZATION-2026-09-13.md`
8. `docs/VERIFIED-FACTS.md`
9. `docs/ROADMAP.md`
10. `docs/LUA-API.md`
11. exact current source and CI

Historical milestone/handoff documents do not override these current records.

## M1G owner decisions — resolved

Do **not** reopen these without new substantive correctness evidence:

- **A1:** custom Minecraft `AudioStream` through normal `SoundManager` / `Channel` positional sound;
- **B1:** server analyzer normalizes common-WAV layout and carries it to the client;
- **C1:** preserve source sample rate while normalizing output representation to mono signed 16-bit PCM;
- **D1:** support a narrow PCM/IEEE-float `WAVE_FORMAT_EXTENSIBLE` subset with `validBits == containerBits`;
- **E1:** use coarse conservative MP3 pre-roll from an earlier existing seek point rather than fine reservoir-specific seek metadata.

If a new meaningful design/correctness tradeoff appears, present the options/tradeoffs and ask the owner before choosing. Handle normal implementation/tuning details directly.

## Product rule

This is a programmable ComputerCraft speaker peripheral.

Lua decides application meaning/policy: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc. Java models technical capabilities only.

Do not create permanent Java music/effect/notification lanes or infer application role from MP3/WAV/etc.

One physical speaker remains one mono positional source.

## Standard CC:T compatibility

The normal `computercraft:speaker` remains the product surface and exposed type `speaker`.

Preserve native/delegated:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW uses separate `hqspeaker_audio_empty` backpressure.

## Modern finite Lua/API

Recommended one-call path:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, path, { volume = 0.6 })
```

Reusable path:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

Modern finite controls remain:

```text
audioStatus
audioPause
audioResume
audioSeek
audioSetVolume
audioSetLooping
audioStop
```

`audioPlayStaged()` remains removed. Temporary staging is import plumbing only.

## M1E authority — closed unless new evidence appears

Server owns finite generation/state/time/control/EOF. Client READY refreshes state; client ERROR is diagnostic. Client/render failures are not canonical playback authority.

Final M1E code: `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final focused Minecraft acceptance was explicitly skipped. Do not claim it passed.

## M1F transport — finalized contract

Explain it first as:

```text
server owns complete MediaAsset
-> authoritative STATE selects encoded anchor
-> nearby client requests bounded encoded pieces
-> server reads pieces off-thread
-> client keeps bounded sliding encoded RAM
-> M1G consumes that window
```

Preserve:

- no modern whole-file push or complete-song client file;
- first demand waits for authoritative STATE;
- source/asset/generation/range/relevance validation;
- current 128 KiB max range / 512 KiB client window as tuning values;
- bounded off-thread reads/outstanding work;
- in-flight asset retain/retry-safe release;
- stale completion discard;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- arbitrary re-anchor;
- forward sliding/discard preserving unread prefetch;
- range shutdown before store cleanup.

Do not restore CHUNK/END or `.part/.media` whole-file transport.

## M1G current implementation

Green at source checkpoint `fc99ec093528f1a8d6a975fab52c270e498dbb04` / CI `34773448121`:

- `MediaMetadata` can carry normalized `WavLayout`;
- `CommonWavAnalyzer` parses chosen classic common PCM/float WAV + narrow WAVEX without decoding sample data;
- parser respects declared RIFF bounds and rejects partial audio frames;
- `ModernFiniteMediaAnalyzer` narrows newly prepared/local assets to MP3 + common WAV;
- `FiniteDecodeDescriptor` defines decoder-facing MP3/WAV-only metadata;
- `FiniteDecodeAnchorSelector` defines exact WAV frame anchors and E1 MP3 pre-roll anchors;
- `HQMediaStaging.prepareAsset(...)` uses the modern acceptance gate;
- `FiniteEncodedInputStream` provides a decoder-worker blocking view over the M1F range window where NEED_DATA waits/refills, true asset EOF alone returns `-1`, cancel wakes the worker, and progressive consumption advances the window;
- encoded-input waiting re-probes under the notification monitor to prevent a lost range-arrival wakeup;
- `FinitePcmQueue` is a fixed-capacity mono-S16 ring with decoder backpressure and nonblocking renderer DATA/STARVED/EOF/CANCELLED states;
- PCM cancellation clears stale data and wakes blocked producers;
- deterministic tests cover the above foundation and concurrency semantics.

The modern client is still silent because these primitives are not yet integrated into `HQFiniteMediaClient` decode/render lifecycle. Do not route it back through inherited whole-file/whole-track finite classes.

### Old finite classes are not the modern engine

- `FileFiniteAudioStream` requires a complete local file.
- `HQAudioStream` finite mode decodes a complete finite payload and retains the whole decoded track.
- `FiniteAudioTrack` stores track-length PCM.

They may remain for inherited/legacy code until later migration work, but M1G must not use them as the modern prepared architecture.

## M1G implementation rules

### Renderer — A1

Use Minecraft `AudioStream` / normal `SoundManager` positional rendering.

- renderer `read(...)` never waits on network/codec work;
- the bounded HQ PCM queue controls how far ahead decoded audio accumulates;
- temporary empty PCM is starvation/underrun, not terminal EOF;
- seek/replacement/stop discards the old local renderer epoch and stale queued PCM;
- stay on the Minecraft `Channel` path rather than owning a second raw OpenAL engine.

### WAV — B1/C1/D1

Modern prepared/local WAV is only:

- mono/stereo;
- U8 PCM;
- S16/S24/S32 PCM;
- F32;
- classic RIFF/WAVE or narrow `WAVE_FORMAT_EXTENSIBLE` PCM/float with equal valid/container width.

Reject >2 channels, A-law/mu-law, compressed/telephony WAV, float64, unusual widths, and differing valid/container widths.

Server supplies normalized physical layout. Decoder converts progressively to mono S16 while preserving the file sample rate.

### MP3 — C1/E1

Use the exact packaged JLayer family frame-by-frame.

`NEED_DATA` from M1F must wait/refill on a decoder worker and must never become `InputStream` EOF.

For seek/rejoin, use a conservative earlier existing seek point (initially about a one-second safety margin before target), decode silently forward, and discard pre-target PCM. Do not add fine reservoir-specific seek metadata in M1G.

A semantic seek always creates a new local decoder epoch even if the selected encoded anchor byte is unchanged.

### Authority

- server M1E state remains canonical;
- decoder/renderer failure is local diagnostic state;
- physical client decoder EOF does not own canonical ENDED/loop behavior;
- encoded and decoded memory remain bounded independently of file duration;
- Minecraft/audio thread never performs network, disk, or codec work.

## Next M1G slices

1. carry the MP3/common-WAV descriptor over the modern finite wire and make STATE use codec-aware anchors;
2. integrate `FiniteEncodedInputStream` / decoder-epoch lifecycle and range-arrival signaling into `HQFiniteMediaClient`;
3. progressive common-WAV conversion into `FinitePcmQueue`;
4. progressive JLayer MP3 decode/pre-roll/discard;
5. A1 positional renderer consuming `FinitePcmQueue` nonblockingly;
6. control/stale-epoch integration;
7. deterministic/component acceptance, then focused Minecraft audible acceptance.

## M1H remains separate

M1H owns complete late-entry/proactive-leave/return-rejoin/dimension/reload/robust-underrun/final-VS2 listener lifecycle. M1G should provide safe local cancellation/restart primitives for it.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. current M1G decision/handoff/current-state records;
4. exact current CI/package evidence;
5. verified-facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. historical milestone/handoff docs.

Green CI is not Minecraft runtime proof.
