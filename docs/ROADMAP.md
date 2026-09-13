# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-13

M1E and M1F are complete at source/test/CI/package level.

M1F final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

M1G is now **in progress** on:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed both NeoForge targets including tests, package verification, and artifact upload.

## Foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` stay delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Server analysis proves finite format/duration/coarse seek facts without whole-track PCM decode. Historical analyzer breadth does not define final support.

## M1E — server-authoritative finite timeline

**Complete at source/test/CI level.**

Server owns finite semantic truth: generation, PLAYING/PAUSED/ENDED/ERROR, position/duration, pause/resume, seek, loop, volume, natural EOF, and server errors.

Final focused Minecraft M1E acceptance was explicitly skipped/unrecorded.

## M1F — demand-driven finite encoded transport

**Complete at source/test/CI/package and deterministic/component level.**

Final candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Implemented/finalized:

- protocol-v5 bounded range request/data at the M1F checkpoint;
- server-selected encoded anchors;
- first demand waits for authoritative STATE;
- source/asset/generation/bounds/relevance validation;
- bounded background reads and outstanding work;
- in-flight MediaAsset lifetime/retry safety;
- stale completion discard;
- retry-safe shutdown before store close;
- arbitrary re-anchor;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- forward sliding consume/discard preserving unread prefetch;
- bounded refill across files much larger than one client window;
- no modern complete-song client file;
- no modern CHUNK/END whole-file transport;
- no `audioPlayStaged()` prototype route.

Focused real-Minecraft M1F transport acceptance remains unrecorded. M1F audibility was not required.

## M1G — core progressive finite engine: MP3 + common WAV

**In progress.**

Locked decisions:

- A1 — Minecraft `AudioStream` / `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1 — coarse conservative MP3 pre-roll from an earlier existing seek point.

See `M1G-DESIGN-DECISIONS-2026-09-13.md`.

Goal:

```text
M1F bounded sliding encoded window
    -> cancelable starvation-aware decoder input
    -> progressive decoder/converter worker
    -> bounded mono S16 PCM queue at source sample rate
    -> Minecraft AudioStream / positional renderer
```

### M1G start checkpoint — complete

At `fc99ec093528f1a8d6a975fab52c270e498dbb04` / CI `34773448121`, both targets prove the current foundation compiles/tests/packages:

- normalized `WavLayout`;
- classic common-WAV + narrow WAVEX analyzer;
- modern prepared/local MP3/common-WAV gate;
- decoder-facing MP3/WAV-only descriptor;
- exact WAV frame anchor mapping;
- E1 MP3 pre-roll anchor selection;
- starvation-aware `FiniteEncodedInputStream` whose `NEED_DATA` waits only on a decoder worker and is not EOF;
- fixed-capacity `FinitePcmQueue` with decoder backpressure and nonblocking renderer states;
- cancellation/wakeup and lost-wakeup protection;
- RIFF declared-container and complete-frame validation.

Newly prepared assets already use the modern MP3/common-WAV analyzer. These primitives are not yet integrated into the live modern client decode/render lifecycle, so this checkpoint is not audible.

### Next M1G slices

1. modern finite descriptor wire update and codec-aware server STATE anchors;
2. integrate decoder-epoch lifecycle and encoded-input signaling into `HQFiniteMediaClient`;
3. progressive common-WAV conversion into the bounded PCM queue;
4. progressive JLayer MP3 decode with E1 pre-roll and pre-target discard;
5. A1 Minecraft `AudioStream`/`SoundManager` positional renderer;
6. pause/resume/seek/loop/volume/stop and stale-epoch lifecycle integration;
7. deterministic/component completion;
8. focused real-Minecraft audible acceptance.

Non-negotiable requirements remain:

- encoded and decoded memory bounded independently of duration;
- `NEED_DATA` never becomes decoder EOF;
- semantic seek restarts local decoder state even if the encoded anchor byte is unchanged;
- no Minecraft/audio-thread network/disk/codec blocking;
- cancellation/replacement safety;
- one physical speaker remains one mono positional source;
- server M1E timeline remains canonical.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- proactive leave cleanup;
- return/rejoin current server time;
- dimension/world/resource reload recovery;
- robust underrun rejoin;
- final VS2 moving-speaker listener lifecycle.

M1G supplies a safe local engine; M1H supplies the full listener discovery/recovery policy.

## M1I — gated native FLAC

Optional. Only advertise native FLAC after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, package, and runtime proof. No Ogg-FLAC.

## M1J — multispeaker shared clocks

Correctness first: shared server sync clocks without expected-global-member barriers, while each physical speaker keeps its own mono positional renderer.

## M1K — active-session sharing optimization

After M1J correctness, share identical active encoded/decode work where safe without persistent client caching or collapsing physical renderers.

## M1L — legacy finite API migration/removal

Migrate worthwhile compatibility frontends to the new asset/transport/decoder engine; remove obsolete whole-file/whole-PCM finite state and old format promises.

## M1M — HQ RAW finalization

Keep bounded RAW producer semantics and separate `hqspeaker_audio_empty`; no fake finite timeline.

## M1N — SoundEngine/OpenAL integration cleanup

Correct category/gain, reload lifecycle, stale channel cleanup, attenuation/VS2 movement, one mono positional source per physical speaker.

## M1O/P/Q — hardening, package verification, consolidated runtime acceptance

Stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. Do not force finite semantics onto live sources.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, and packaging.
