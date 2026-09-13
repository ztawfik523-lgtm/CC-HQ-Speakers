# CC:HQ Speakers — M1G active handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green integrated source checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

CI:

`34778546164`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Artifacts:

- 21.1.247 artifact `10324148909` — ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482` — ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

## Status

M1G is well beyond the original start checkpoint. Modern prepared finite playback now has an integrated progressive decode/render path in source.

Do not describe the modern client as transport-only or silent based on older M1G-start documentation.

Green CI is still not a recorded audible Minecraft runtime PASS.

## Locked owner decisions

`M1G-DESIGN-DECISIONS-2026-09-13.md` remains authoritative for:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1 — coarse conservative MP3 pre-roll from an earlier existing seek point.

If another meaningful architecture/correctness choice appears, ask the owner before selecting among real tradeoffs.

## Current modern finite pipeline

```text
server MediaAsset
-> canonical server playback clock/state
-> protocol v6 BEGIN decoder descriptor
-> codec-aware STATE anchor
-> M1F bounded encoded ranges
-> starvation-aware FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS category
```

## Implemented M1G behavior

### Server/wire

- new prepared/local assets are gated to MP3 or the supported common-WAV subset;
- protocol v6 BEGIN carries the modern `FiniteDecodeDescriptor` rather than historical `MP3/OGG/AUDIO_FILE` ambiguity;
- WAV layout includes normalized representation, sample rate, channels, block alignment and exact data bounds;
- STATE uses `FiniteDecodeAnchorSelector`;
- WAV anchors are exact frame-aligned byte/time positions;
- MP3 anchors use E1 conservative pre-roll from an earlier real analyzed seek point.

### Client decoder lifecycle

- one local decode epoch owns encoded input, bounded PCM, decoder task and renderer state;
- seek/replacement/stop cancels the old epoch and stale PCM;
- range arrivals signal a waiting encoded input;
- missing encoded bytes wait only on a decoder worker and never become decoder EOF;
- stale/cancelled input aborts the old decoder epoch;
- decoder failures remain client diagnostics and do not become canonical server ERROR.

### WAV

- progressive U8/S16/S24/S32/F32 input;
- mono/stereo only;
- stereo downmixes to mono;
- output is signed mono S16 at the original source sample rate;
- no whole-track decoded PCM retention.

### MP3

- progressive frame decoding uses the exact packaged JLayer family;
- analyzed sample rate/channel count are checked against decoded frames;
- earlier-anchor pre-roll is decoded silently;
- pre-target PCM is discarded before output;
- output is bounded mono S16 at source rate.

### Renderer

- `FinitePcmAudioStream` reads the bounded PCM queue without blocking on network/disk/codec work;
- temporary PCM starvation produces a short bounded silence buffer rather than terminal EOF;
- renderer start waits for bounded prebuffer or local decoder EOF;
- before renderer start, queued PCM is discarded forward toward the projected authoritative server time so decode/prebuffer delay does not become permanent audible lag;
- `FiniteSpeakerSound` is one positional BLOCKS-category linear-attenuation source;
- pause/resume reaches the Minecraft channel path;
- volume updates refresh the BLOCKS channel gain path;
- SoundEngine stream close cancels the producer PCM queue so abandoned decoders cannot stay blocked forever.

## Current unresolved M1G choice — loop-wrap rejoin

The server clock already owns canonical looping. The client intentionally does not locally modulo server time. A local decoder therefore needs a defined rejoin mechanism when physical asset EOF is reached while the server remains in a looping session.

Owner choice required:

### L1 — client EOF refresh

At local EOF while `looping == true`, ask the server for fresh STATE and create a new decode epoch from the server-selected current anchor.

Pros:

- simplest;
- strongest fit with server-authoritative M1E semantics;
- no client-owned loop clock;
- no server wrap counter/state machine.

Cons:

- roundtrip + prebuffer at each loop can create a boundary gap, especially for short loops.

### L2 — server wrap STATE

Server detects canonical wrap crossings and proactively sends fresh STATE at each wrap.

Pros:

- server remains explicit loop authority;
- clients get an authoritative restart before/at wrap;
- potentially tighter loop boundary than waiting for client EOF refresh.

Cons:

- adds wrap tracking and fanout to server tick semantics;
- needs careful behavior for very short media/missed wraps and projection failures.

### L3 — client local modulo/restart

Client predicts wrap from duration + last server snapshot and restarts locally, reconciling later.

Pros:

- lowest loop-boundary latency;
- no per-wrap server packet/roundtrip requirement.

Cons:

- weakens the chosen server-authoritative model;
- client drift/reconciliation becomes part of correctness;
- more timing state on the client.

Do not implement L1/L2/L3 until the owner chooses.

## Evidence boundary

```text
M1E source/test/CI: complete
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: complete
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G loop-wrap policy: unresolved
M1G audible Minecraft acceptance: unrecorded
```

## M1H boundary

M1H still owns full late-entry discovery, proactive out-of-range cleanup, leave/return rejoin, dimension/world/resource-reload recovery, robust general underrun recovery, and final VS2 moving-listener lifecycle.

## Read order

1. `M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1G-START.md`
3. `CURRENT-STATE.md`
4. `TESTING.md`
5. `KNOWN-ISSUES.md`
6. `M1F-FINALIZATION-2026-09-13.md`
7. `VERIFIED-FACTS.md`
8. `ROADMAP.md`
9. `LUA-API.md`
10. exact current source and current CI
