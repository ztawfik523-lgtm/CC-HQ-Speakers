# M1G preparation — 2026-09-13

> **Historical preparation record. M1G and the separate post-M1G Option A hardening pass are now complete; M1H is current. The original decision gates are resolved.**
>
> This file preserves the reasoning used before implementation. It is not a current task list. Current authority is `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, and exact current source.

## Historical status

Preparation branch:

`codex/m1g-preparation`

Preparation base:

`8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365`, both target NeoForge versions green.

At this checkpoint M1G implementation had not started. That statement is now historical: progressive MP3/common-WAV decode, bounded PCM, and positional Minecraft rendering are integrated at source checkpoint `957832348eaa6e497282d923f2312c9c7d7c550f`.

Focused real-Minecraft M1F transport acceptance remains unrecorded. Focused audible M1G acceptance is also unrecorded.

## What M1G was intended to mean

The preparation target was:

```text
hq.playFile(...)
-> server-owned finite playback clock
-> bounded encoded ranges from M1F
-> client progressive decode
-> bounded mono PCM
-> one positional sound from that physical speaker
```

This target remains the architecture and is now substantially integrated. M1G must not bring back whole-song client files or whole-track decoded PCM.

## Historical source observations which led to M1G

At this checkpoint `HQFiniteMediaClient` was transport-only and provided the bounded `FiniteRangeWindow`, server-selected anchor/time, range pumping, and cancellation state. The decoder/PCM/renderer did not yet exist.

That is no longer true. The current source now includes:

- `FiniteEncodedInputStream`;
- progressive JLayer MP3 decode;
- progressive common-WAV conversion;
- bounded `FinitePcmQueue`;
- `FinitePcmAudioStream`;
- positional `FiniteSpeakerSound`.

The old complete-file `FileFiniteAudioStream` and retained-whole-track `FiniteAudioTrack` remain legacy evidence, not the modern prepared engine.

## Correctness constraints which remain valid

The preparation identified several requirements which still apply:

- no complete encoded file on the client;
- no whole decoded PCM track;
- encoded and decoded memory bounded independently of duration;
- decoder waits only on worker threads;
- renderer reads never block on network/disk/codec work;
- M1F `NEED_DATA` is not physical EOF;
- seek/replacement/stop cancels stale encoded waits, decoder output, PCM, and renderer state;
- MP3 seek/rejoin uses Layer III pre-roll;
- server playback remains canonical;
- one physical speaker remains one mono positional source.

Later audits refined the seek/cancellation model into KI-053/KI-056/KI-057 and the selected explicit server-authoritative decoder/re-anchor revision.

## Historical decision gates — now resolved

### A — renderer path

Historical alternatives:

- A1: Minecraft `AudioStream` / normal `SoundManager`;
- A2: direct Channel/OpenAL queued-buffer ownership.

**Selected: A1.**

### B — WAV layout ownership

Historical alternatives:

- B1: server-normalized WAV layout carried to client;
- B2: client progressively parses layout independently.

**Selected: B1.**

### C — finite sample-rate policy

Historical alternatives:

- C1: preserve source sample rate;
- C2: resample to 48 kHz.

**Selected: C1.**

Later decisions also locked:

- D1: narrow PCM/float WAVEX support;
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.

Do not ask the owner to choose A/B/C again.

## Later M1G scope decisions which supersede open questions here

After implementation/audit/rethink, the owner additionally selected:

- an explicit server-authoritative decoder/re-anchor revision, likely protocol v7; current source remains v6;
- fixed 32-block modern-finite core listening/delivery radius;
- HQ volume changes gain/loudness, not core range;
- global HQ volume zero keeps canonical server time running but hibernates local decoder/renderer/range work;
- looping is ordinary replay after local physical EOF while authoritative `looping=true`; a normal restart gap is acceptable;
- no gapless MP3/LAME padding work, loop-head prefetch solely to hide the boundary, permanent-source loop architecture, dynamic volume-aware range, or SPR acoustic/range integration in M1G.

The only remaining protocol-shape choice is whether finite CONTROL packets survive as optional latency hints or STATE becomes the sole transition authority.

## Current WAV/MP3 boundary

Modern prepared support is MP3 + supported common WAV only.

Common WAV target remains:

- mono/stereo;
- U8/S16/S24/S32/F32;
- classic RIFF/WAVE plus selected narrow PCM/float WAVEX;
- stereo downmix to mono;
- reject >2 channels and unusual/compressed/telephony variants.

MP3 uses packaged JLayer progressively. E1 pre-roll decodes from an earlier real seek point and discards pre-target PCM.

`FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`. There are no richer frame/skip fields being discarded.

## Current loop/range/volume boundary

Historical preparation treated loop/range behavior as future design work. Current M1G policy is settled:

- fixed 32-block core radius;
- distance attenuation inside that radius;
- volume changes gain, not range;
- global volume zero hibernates local work while server time continues;
- looping is normal replay, not gapless/continuous-source engineering.

Future Sound Physics Remastered compatibility owns intentional acoustic/range extension and matching transport relevance.

## M1H boundary

M1H still owns:

- late range entry;
- proactive leave cleanup;
- leave/return rejoin;
- dimension/resource reload recovery;
- robust general underrun rejoin;
- final moving-source/VS2 lifecycle.

Modern BEGIN already carries block coordinates. The legacy client already computes VS2 world position client-side from block coordinates. M1H may mirror that modernly or add explicit position updates; that tradeoff remains open and must not be chosen silently.

## Current evidence gaps

Use `TESTING.md` for exact details. Major remaining evidence/source work includes:

- KI-053/KI-056/KI-057 explicit revision implementation and cancellation tests;
- KI-058/KI-060 fixed attenuation / renderer-start / volume-zero behavior;
- KI-051 ordinary replay implementation;
- real-MP3 progressive JLayer integration test;
- focused `FinitePcmAudioStream` tests;
- KI-061 staging cleanup;
- focused audible Minecraft M1G acceptance.

Cross-cutting findings KI-062/KI-063/KI-054/KI-064 are also tracked in current docs.

## Current read order

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `NEXT-CHAT-HANDOFF.md`
7. `ARCHITECTURE.md`
8. `ROADMAP.md`
9. exact current source/CI

This preparation document is historical evidence only.
