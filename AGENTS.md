# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G integrated progressive decode/render is in progress.**

Active branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green integrated M1G source checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

Source CI:

`34778546164` — NeoForge 21.1.247 and 21.1.248 both passed build/tests/package verification/artifact upload.

Documentation checkpoint before this handoff refresh:

`7ec70d4674b237f055d450e1290a652f7c23b65d`

Docs-head CI:

`34780519972` — both target jobs passed build/tests/package verification/artifact upload.

Final M1F source/test candidate remains `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`; final M1F CI is `34763362365`.

Green CI is not Minecraft runtime proof.

## NEXT CHAT STOP CONDITION

Before making more M1G source changes, **ask the owner to choose the loop-wrap rejoin architecture**:

- **L1 — client EOF refresh:** when local decoder/PCM reaches physical EOF while server looping is still true, request fresh authoritative STATE and create a new decode epoch from the server-selected anchor.
- **L2 — server wrap STATE:** server detects canonical loop-wrap crossings and proactively projects fresh STATE at each wrap.
- **L3 — client local modulo/restart:** client predicts wraps from duration + last server snapshot and restarts locally, reconciling later.

Do not silently select one. Explain the pros/cons from `docs/HANDOFF-2026-09-13-M1G-START.md`, ask the owner, and wait for the choice before implementing loop-wrap behavior.

The earlier A1/B1/C1/D1/E1 choices are already locked and must not be reopened without new substantive correctness evidence.

## Read before continuing M1G

1. `docs/HANDOFF-2026-09-13-M1G-START.md`
2. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
3. `docs/CURRENT-STATE.md`
4. `docs/TESTING.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1F-FINALIZATION-2026-09-13.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. exact current source and CI

Historical milestone/handoff documents do not override current records.

## Locked M1G architecture

Do **not** reopen these without new substantive evidence:

- **A1:** custom Minecraft `AudioStream` through normal `SoundManager` / `Channel` positional sound;
- **B1:** server analyzer normalizes common-WAV layout and carries it to the client;
- **C1:** preserve source sample rate while output representation becomes mono signed 16-bit PCM;
- **D1:** narrow PCM/IEEE-float `WAVE_FORMAT_EXTENSIBLE` support with `validBits == containerBits`;
- **E1:** coarse conservative MP3 pre-roll from an earlier analyzed seek point rather than fine reservoir-specific seek metadata.

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

## M1E authority

Server owns finite generation/state/time/control/EOF. Client READY refreshes state; client ERROR is diagnostic. Client/render failures are not canonical playback authority.

Final M1E code: `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final focused Minecraft M1E acceptance was explicitly skipped. Do not claim it passed.

## M1F finalized transport

Preserve:

- no modern whole-file push or complete-song client file;
- first byte demand waits for authoritative STATE;
- source/asset/generation/range/relevance validation;
- current 128 KiB max range / 512 KiB client encoded window as tuning values;
- bounded off-thread reads/outstanding work;
- in-flight asset retain/retry-safe release;
- stale completion discard;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- arbitrary re-anchor;
- forward sliding/discard preserving unread prefetch;
- range shutdown before store cleanup.

Do not restore CHUNK/END or `.part/.media` whole-file transport.

## Current integrated M1G source

The modern prepared path now includes:

- protocol v6 BEGIN with MP3/common-WAV `FiniteDecodeDescriptor`;
- normalized `WavLayout` + narrow classic/WAVEX common-WAV analysis;
- modern prepared/local gate narrowed to MP3 + supported common WAV;
- codec-aware server STATE anchors: exact WAV frame anchors and E1 MP3 pre-roll anchors;
- starvation-aware `FiniteEncodedInputStream` over the M1F range window;
- bounded `FinitePcmQueue` with decoder backpressure and nonblocking renderer reads;
- progressive U8/S16/S24/S32/F32 WAV conversion to mono S16 at source rate;
- progressive JLayer MP3 decoding with earlier-anchor silent pre-roll and pre-target discard;
- local decode epochs in `HQFiniteMediaClient`, cancelled on seek/replacement/stop;
- range arrivals wake active decoder input;
- bounded prebuffer/catch-up toward projected authoritative server time before renderer start;
- `FinitePcmAudioStream`, which returns bounded PCM or short silence during temporary starvation rather than fake EOF;
- `FiniteSpeakerSound` through normal Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation;
- pause/resume and volume projection through the existing Minecraft channel-control path;
- stream close cancels the producer PCM queue so abandoned decoder workers cannot remain blocked.

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## M1G correctness rules

- server M1E state remains canonical;
- client decoder/render failures are local diagnostics;
- physical decoder EOF does not own canonical ENDED/loop semantics;
- `NEED_DATA` must never become decoder EOF;
- decoder/network/disk waits never occur on the Minecraft/audio thread;
- encoded and decoded memory remain bounded independently of media duration;
- semantic seek creates a new decoder epoch even if selected encoded anchor byte is unchanged;
- seek/replacement/stop discards old encoded wait/decoder/PCM/renderer state;
- one physical speaker remains one mono positional source.

## Remaining M1G after owner chooses L1/L2/L3

1. implement and test the chosen loop-wrap rejoin policy;
2. re-audit the integrated timing/cancellation path after loop support;
3. complete deterministic/component lifecycle acceptance;
4. run focused real-Minecraft audible acceptance for MP3/common WAV, seek/pause/resume/loop/stop, starvation/refill, bounded memory, positional attenuation, and standard CC:T compatibility.

Do not pull full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/VS2 lifecycle into M1G; that remains M1H.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. current handoff/current-state/design-decision records;
4. exact current CI/package evidence;
5. verified-facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. historical milestone/handoff docs.

Green CI is not Minecraft runtime proof.