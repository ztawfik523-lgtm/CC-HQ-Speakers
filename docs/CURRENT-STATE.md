# Current state

## Current checkpoint

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport remain complete at the **source/test/CI/package** level.

M1G is actively implemented on:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green integrated M1G **source** checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

Source CI:

`34778546164`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Artifacts:

- 21.1.247 artifact `10324148909` — ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482` — ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

The source checkpoint was followed only by documentation reconciliation. Pre-handoff documentation head `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both NeoForge 21.1.247 and 21.1.248, including build/tests/package verification/artifact upload.

Green CI is not Minecraft runtime proof. Focused M1G audible Minecraft acceptance is still unrecorded.

## Current finite architecture

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> codec-aware server STATE anchor
    -> bounded client-requested encoded ranges (M1F)
    -> bounded sliding encoded RAM
    -> starvation-aware decoder-worker InputStream
    -> progressive common-WAV or JLayer MP3 decoder
    -> bounded mono signed-16 PCM queue at source sample rate
    -> nonblocking Minecraft AudioStream
    -> one positional BLOCKS SoundManager source per physical speaker
```

## Locked M1G decisions

These choices are already resolved and should not be reopened without new substantive evidence:

- **A1:** normal Minecraft `AudioStream` / `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout carried to clients;
- **C1:** preserve source sample rate;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- **E1:** coarse conservative MP3 pre-roll using an earlier real seek point.

Output representation is mono signed 16-bit PCM. One physical speaker remains one mono positional source.

## M1G implementation now integrated

Current source includes:

- protocol v6 modern finite BEGIN descriptor carrying MP3/common-WAV decode facts;
- normalized `WavLayout` and narrow common-WAV server analysis;
- modern prepared/local acceptance narrowed to MP3 + supported common WAV;
- exact WAV frame anchors and E1 MP3 pre-roll anchors in server STATE;
- starvation-aware `FiniteEncodedInputStream` over the M1F sliding window;
- bounded `FinitePcmQueue` with decoder backpressure and nonblocking consumer reads;
- progressive U8/S16/S24/S32/F32 common-WAV conversion with mono/stereo -> mono S16;
- progressive finite MP3 decode through the packaged JLayer dependency;
- MP3 pre-target discard after earlier-anchor decode;
- decoder epoch cancellation/replacement in `HQFiniteMediaClient`;
- range arrivals wake the active decoder input;
- bounded prebuffer and catch-up discard toward the projected authoritative server time before renderer start;
- `FinitePcmAudioStream`, returning bounded PCM or short silence on temporary starvation instead of turning underrun into EOF;
- `FiniteSpeakerSound` through Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation;
- local pause/resume and volume projection through the existing channel-control path;
- seek/replacement/stop discards old decoder, PCM and renderer state;
- closing the Minecraft PCM stream cancels its producer queue so a decoder cannot remain blocked forever on abandoned PCM.

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared engine.

## NEXT CHAT STOP CONDITION — ask the owner first

Before making further M1G source changes, the next chat must ask the owner to choose the remaining **loop-wrap rejoin architecture**.

The server clock already owns canonical looping. The client intentionally does not locally modulo server time. After one local decode reaches physical asset EOF during a looping server session, the client needs a defined way to get a fresh canonical position/anchor and create a new decode epoch.

### L1 — client EOF refresh

At local EOF while `looping == true`, request fresh authoritative STATE and restart from the server-selected current anchor.

Pros: simplest; strongest fit with M1E server authority; no client-owned loop clock; no server wrap counter.

Cons: roundtrip + prebuffer can cause a loop-boundary gap, especially for short loops.

### L2 — server wrap STATE

Server detects canonical loop-wrap crossings and proactively projects fresh STATE at each wrap.

Pros: server remains explicit loop authority; restart can be projected at the wrap; potentially tighter loop boundary.

Cons: adds wrap tracking/fanout to server tick semantics; needs careful behavior for very short media, missed wraps, and projection failures.

### L3 — client local modulo/restart

Client predicts wraps from duration + last server snapshot, restarts locally, then reconciles later.

Pros: lowest boundary latency and no per-wrap roundtrip.

Cons: weakens the chosen server-authoritative model; client drift/reconciliation becomes correctness state; more client timing machinery.

**Do not choose or implement L1/L2/L3 silently. Ask the owner first.**

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / no recorded PASS
M1F focused Minecraft transport acceptance: not recorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G documentation checkpoint before final handoff updates: green at 7ec70d4674b237f055d450e1290a652f7c23b65d
M1G loop-wrap rejoin: unresolved owner choice
M1G audible Minecraft PASS: not recorded
```

## M1H boundary

Full late-entry discovery, proactive leave cleanup, leave/return rejoin, dimension/resource-reload recovery, robust general underrun rejoin, and final VS2 moving-listener lifecycle remain M1H.

## Current read order

1. `HANDOFF-2026-09-13-M1G-START.md`
2. `M1G-DESIGN-DECISIONS-2026-09-13.md`
3. `CURRENT-STATE.md`
4. `TESTING.md`
5. `KNOWN-ISSUES.md`
6. `VERIFIED-FACTS.md`
7. `M1F-FINALIZATION-2026-09-13.md`
8. `ROADMAP.md`
9. `LUA-API.md`
10. exact current source and CI

If another meaningful architecture/correctness tradeoff appears, present the options and ask the owner before selecting it.