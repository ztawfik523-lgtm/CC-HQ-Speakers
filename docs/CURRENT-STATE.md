# Current state

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, package verification, and artifact upload. Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

Repository/source audits on 2026-09-14 reconciled stale documentation and found additional client-sync, renderer and storage-lifecycle issues. No implementation change was made by those audits.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance remains unrecorded.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Locked M1G decisions

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer.
- B1: server-normalized common-WAV layout.
- C1: preserve source sample rate.
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support.
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Integrated source

M1G source includes the MP3/common-WAV prepared gate, protocol-v6 decode descriptor, exact WAV/E1 MP3 anchors, starvation-aware `FiniteEncodedInputStream`, bounded `FinitePcmQueue`, progressive WAV conversion, progressive JLayer MP3 decoding, decoder-epoch cancellation, bounded prebuffer/catch-up, `FinitePcmAudioStream`, and positional `FiniteSpeakerSound` through `SoundSource.BLOCKS`.

Pause/resume/volume use the Minecraft channel-control path. Seek/replacement/stop discard old decoder/PCM/renderer state. Temporary PCM starvation becomes bounded silence rather than fake EOF.

## Current correctness/evidence blockers

The deepest current issue is not just KI-053 by itself: decoder re-anchor intent is not represented explicitly enough.

- **KI-053:** an ordinary same-anchor STATE can reset an already-slid encoded window without restarting the decoder, potentially putting the live cursor outside the reset window.
- **KI-056:** `CONTROL SEEK` cancels the current decoder before invalidating its epoch token, so the expected cancellation can race into `decoderFailed()` and kill a valid session before replacement STATE arrives.
- **KI-057:** STATE currently acts as both a timeline snapshot and an implicit re-anchor command. A changed time-derived anchor can restart healthy playback after ordinary controls (especially exact WAV anchors), while a same-anchor semantic seek still depends on the preceding CONTROL packet to communicate restart intent. This should be solved as one coherent snapshot-vs-reanchor/decoder-revision design rather than by patching only KI-053.
- **KI-058:** live modern-finite volume changes update gain state but not the live channel attenuation distance. CC:T 1.120.0 has an explicit `linearAttenuation(...)` workaround for the same Minecraft behavior.
- **KI-059:** the server uses a fixed 32-block finite relevance radius although supported volume reaches 3 and normal 16-block attenuation semantics can make volume 3 audible to about 48 blocks. Future SPR compatibility also means transport relevance should not be permanently hard-coded to vanilla distance.
- **KI-060:** renderer startup is latched before `SoundManager` proves the sound actually started. Minecraft supports `SoundInstance.canStartSilent()` for long-lived silent sounds, but with volume-aware listener lifecycle a globally zero-volume finite session can instead keep only canonical server time alive and suspend client transport/decode until unmuted.
- **KI-055:** there is still no real-MP3 progressive JLayer fixture test across range progression/starvation and no focused `FinitePcmAudioStreamTest`; historical Lua scripts are not modern prepared-path proof.
- **KI-061:** every speaker gets a random persistent ComputerCraft staging save-directory, but staging cleanup does not clear leftover files. Low-level/interrupted staging can therefore accumulate unreachable files across speaker recreation/restarts.
- **KI-054:** shutdown deletion failure loses completed-file retry bookkeeping and can skip `ServerMediaAssets` registry removal. This is lower-frequency shutdown hardening.

See `KNOWN-ISSUES.md` and `TESTING.md`. These findings are documented only; source is still at the green integrated checkpoint above.

## Owner direction after tradeoff review

These are design directions selected/provisionally selected by the owner after the deeper audit; source has not yet been changed.

### Decoder snapshot/re-anchor model

The owner prefers the **explicit decode/reanchor revision** approach if it indeed costs less over the life of the project even though it is a larger immediate patch.

The audit supports that direction: an explicit server-authoritative revision removes the current dependency on the combination/order of CONTROL SEEK and STATE, makes same-anchor seek self-describing, lets ordinary pause/resume/volume snapshots preserve healthy decoder state, and gives loop/rejoin work one common restart primitive. The alternative minimal-v6 repair remains possible, but retains more special-case ordering and future maintenance risk.

### Volume-aware listener relevance

The owner prefers **dynamic volume-aware relevance** rather than a fixed radius, and wants the delivery envelope configurable with future Sound Physics Remastered compatibility in mind.

The intended separation should be:

- vanilla-style audible distance remains based on the sound/volume contract;
- server transport relevance follows a volume-aware base distance;
- a configurable delivery-distance multiplier/cap provides safety headroom for server policy and future acoustic mods instead of baking a permanent 48-block ceiling into the protocol;
- the required enter/leave/re-enter subset of M1H therefore moves forward into this work.

SPR compatibility should later be able to influence this relevance policy without changing the core finite protocol.

### Volume zero

Canonical playback time should continue at volume zero.

A literal always-running silent renderer is valid if `FiniteSpeakerSound` is allowed to start silent, but if the server stops sending encoded ranges the decoder cannot actually keep consuming in sync. Because dynamic relevance already requires explicit listener membership/rejoin, the cleaner optimization is to treat global finite volume zero as **transport/render hibernation**: keep the authoritative server session/clock alive, send no media ranges while nobody can hear it, and on unmute re-admit listeners with authoritative current STATE/reanchor and resume from current server time.

This preserves the owner's desired semantics while avoiding a permanently silent Minecraft/OpenAL/SPR source and wasted decode/network work. Client-local BLOCKS/MASTER slider zero is different because the server cannot know that setting; it should not alter server transport policy.

## Remaining loop-wrap choice

The original three choices are still valid, but the tradeoff review identified a fourth middle-ground design:

- **L1 — client EOF refresh:** physical EOF asks the server for fresh authoritative STATE/reanchor. Strong authority and simple state; boundary pays roundtrip + prebuffer latency.
- **L2 — server wrap projection:** server detects/tracks wraps and pushes a new reanchor. Server remains explicit authority, but wrap tracking/fanout and very short-loop handling become server responsibilities; a projection sent only at/after wrap can still arrive too late for a seamless boundary.
- **L3 — client clock prediction:** client predicts the wrap from duration/snapshot and restarts locally. Lowest boundary latency, but introduces a second timing model and drift/reconciliation correctness state.
- **L4 — server-authorized local EOF rollover with authoritative fallback:** the server's authoritative `looping=true` is the permission to roll over, but the client does not predict wall-clock wrap time. At actual decoded physical EOF it starts the next local decode cycle immediately, ideally with bounded loop-head prebuffer, while authoritative revision/STATE is still used for seek, loop-disable, rejoin and recovery. If the client is materially behind because of starvation, it falls back to a fresh authoritative reanchor instead of blindly starting at zero. This avoids a mandatory roundtrip on healthy loop boundaries without inventing a separate client clock, but it needs a loop-aware decoder/PCM rollover path and careful stale-state cancellation.

L4 is now a real candidate and should be weighed against L1-L3 before implementation.

Separately, perfectly sample-gapless MP3 looping is not guaranteed by transport/restart architecture alone: MP3 encoders can add leading delay and trailing padding. True gapless MP3 requires reading/using suitable encoder gapless metadata (for example LAME/Xing delay/padding when present) or accepting that some MP3 files can contain a small encoded gap. WAV does not have that codec-padding problem.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F focused Minecraft transport acceptance: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G live volume/range/start correctness: open KI-058/KI-059/KI-060
M1G staging lifecycle cleanup: open KI-061
M1G real-MP3 progressive integration coverage: incomplete
M1G focused FinitePcmAudioStream coverage: incomplete
M1G loop-wrap rejoin: L1/L2/L3/L4 owner choice still open
M1G audible Minecraft PASS: unrecorded
```

Full late-entry/leave-return/dimension-reload/general-underrun/final-VS2 lifecycle remains M1H except for the listener membership subset intentionally pulled forward for dynamic volume-aware relevance.

## Read order

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `HANDOFF-2026-09-13-M1G-START.md`
6. `M1G-DESIGN-DECISIONS-2026-09-13.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `M1F-FINALIZATION-2026-09-13.md`
10. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
