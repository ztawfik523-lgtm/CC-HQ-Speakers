# CC:HQ Speakers — M1G continuation handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

## Exact checkpoint

Current green integrated M1G **source** checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

Source CI:

`34778546164`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Artifacts:

- 21.1.247 artifact `10324148909` — ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482` — ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

Only documentation reconciliation followed that source commit before this handoff refresh. Documentation head `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both NeoForge targets, including build/tests/package verification/artifact upload.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance is **not recorded**.

## Status

M1E source/test/CI is complete; its final focused Minecraft acceptance was explicitly skipped/unrecorded.

M1F source/test/CI/package + deterministic/component acceptance is complete at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`. Focused real-Minecraft M1F transport acceptance remains unrecorded.

M1G is actively implemented. Modern prepared finite playback now has an integrated progressive decode/render path in source. Do not describe the modern client as transport-only or silent based on older M1G-start documents.

## Locked owner decisions — do not reopen casually

`M1G-DESIGN-DECISIONS-2026-09-13.md` remains authoritative for:

- **A1** — Minecraft `AudioStream` / normal `SoundManager` renderer;
- **B1** — server-normalized common-WAV layout;
- **C1** — preserve source sample rate;
- **D1** — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- **E1** — coarse conservative MP3 pre-roll from an earlier existing seek point.

Output is mono signed 16-bit PCM at source sample rate. One physical speaker remains one mono positional source.

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

### Server / wire

- newly prepared/local assets are gated to MP3 or the supported common-WAV subset;
- protocol v6 BEGIN carries the modern `FiniteDecodeDescriptor` instead of historical `MP3/OGG/AUDIO_FILE` ambiguity;
- WAV layout includes normalized representation, sample rate, channels, block alignment, and exact data bounds;
- STATE uses `FiniteDecodeAnchorSelector`;
- WAV anchors are exact frame-aligned byte/time positions;
- MP3 anchors use E1 conservative pre-roll from an earlier real analyzed seek point.

### Client decoder lifecycle

- one local decode epoch owns encoded input, bounded PCM, decoder task, and renderer state;
- seek/replacement/stop cancels old epoch and stale PCM;
- range arrivals wake a waiting encoded input;
- missing encoded bytes wait only on a decoder worker and never become decoder EOF;
- stale/cancelled input aborts old decoder work;
- decoder failures remain client diagnostics and do not become canonical server ERROR.

### WAV

- progressive U8/S16/S24/S32/F32 input;
- mono/stereo only;
- stereo downmixes to mono;
- output is signed mono S16 at the original source sample rate;
- no whole-track decoded PCM retention.

### MP3

- progressive frame decoding uses the packaged JLayer family;
- analyzed sample rate/channel count are checked against decoded frames;
- earlier-anchor pre-roll is decoded silently;
- pre-target PCM is discarded before output;
- output is bounded mono S16 at source rate.

### Renderer

- `FinitePcmAudioStream` reads the bounded PCM queue without blocking on network/disk/codec work;
- temporary PCM starvation emits short bounded silence rather than terminal EOF;
- renderer start waits for bounded prebuffer or local decoder EOF;
- before renderer start, queued PCM is discarded toward the projected authoritative server time so decode/prebuffer delay does not become permanent audible lag;
- `FiniteSpeakerSound` is one positional `SoundSource.BLOCKS` linear-attenuation source;
- pause/resume reaches the Minecraft channel path;
- volume updates refresh BLOCKS-channel gain;
- SoundEngine stream close cancels the producer PCM queue so abandoned decoders cannot remain blocked forever.

The inherited complete-file JavaSound/mp3spi finite bridge is **not** the modern prepared engine.

# NEXT CHAT: FIRST ACTION MUST BE THE ARCHITECTURE QUESTION

Before changing more M1G source, **ask the owner to choose the loop-wrap rejoin architecture**. Do not infer a choice from earlier A1/B1/C1/D1/E1 decisions.

The server already owns canonical looping. The client deliberately does not locally modulo server time. When the local decoder reaches physical asset EOF while the server session is still looping, a fresh decoder epoch needs a canonical position/anchor.

## L1 — client EOF refresh

At local EOF while `looping == true`, request fresh authoritative STATE and restart from the server-selected current anchor.

**Pros:** simplest; strongest fit with M1E server authority; no client-owned loop clock; no server wrap counter/state machine.

**Cons:** roundtrip + prebuffer can create a loop-boundary gap, especially for short loops.

## L2 — server wrap STATE

Server detects canonical loop-wrap crossings and proactively projects fresh STATE at each wrap.

**Pros:** server remains explicit loop authority; clients get authoritative restart projection at the wrap; potentially tighter boundary than waiting for EOF refresh.

**Cons:** adds wrap tracking/fanout to server tick semantics; needs careful handling of very short media, missed wraps, and projection failures.

## L3 — client local modulo/restart

Client predicts wraps from duration + last authoritative snapshot and restarts locally, reconciling later.

**Pros:** lowest loop-boundary latency; no per-wrap server packet or roundtrip requirement.

**Cons:** weakens the server-authoritative model; drift/reconciliation becomes correctness state; adds client timing machinery.

**STOP CONDITION:** do not implement L1, L2, or L3 until the owner answers this choice in the new chat.

After the owner chooses, recheck the exact current branch/source and then continue M1G from that choice. If another meaningful architecture/correctness tradeoff appears, present options and ask before choosing. Normal implementation/tuning details can be handled directly.

## Remaining M1G work after loop policy choice

- implement and test the chosen loop-wrap rejoin semantics;
- re-audit integrated decoder/renderer cancellation and timing paths after the loop change;
- finish deterministic/component acceptance for the final M1G lifecycle;
- run focused Minecraft audible acceptance for MP3/common WAV, seek/pause/resume/loop/stop, bounded memory, starvation/refill, positional attenuation, and standard CC:T compatibility;
- do not pull full late-entry/leave-return/dimension/reload/general-underrun/VS2 lifecycle into M1G; that remains M1H.

## Evidence boundary

```text
M1E source/test/CI: complete
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: complete
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G docs checkpoint before final handoff refresh: green at 7ec70d4674b237f055d450e1290a652f7c23b65d
M1G loop-wrap architecture: OWNER CHOICE REQUIRED
M1G audible Minecraft acceptance: unrecorded
```

## M1H boundary

M1H still owns full late-entry discovery, proactive out-of-range cleanup, leave/return rejoin, dimension/world/resource-reload recovery, robust general underrun recovery, and final VS2 moving-listener lifecycle.

## Read order for the next chat

1. `HANDOFF-2026-09-13-M1G-START.md` — this handoff, including the required L1/L2/L3 question;
2. `M1G-DESIGN-DECISIONS-2026-09-13.md` — locked A1/B1/C1/D1/E1 choices;
3. `CURRENT-STATE.md`;
4. `TESTING.md`;
5. `KNOWN-ISSUES.md`;
6. `VERIFIED-FACTS.md`;
7. `M1F-FINALIZATION-2026-09-13.md`;
8. `ROADMAP.md`;
9. `LUA-API.md`;
10. exact current source and CI.