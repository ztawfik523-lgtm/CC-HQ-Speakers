# M1G scope decisions — 2026-09-14

This document records the owner's narrowed M1G direction after rechecking the progressive finite player against the current source, Minecraft 1.21.1 sound behavior, CC:T 1.120.0 speaker behavior, and planned Sound Physics Remastered compatibility.

It is a current design decision record. Historical milestone documents remain historical.

## 1. Decoder/re-anchor semantics: explicit revision

M1G will use an explicit server-authoritative decoder/re-anchor revision rather than inferring restart intent from whether a codec anchor changed.

The intended contract is:

- a new media playback uses a new generation;
- ordinary STATE reconciliation, pause/resume, volume changes, and ordinary loop-state snapshots do not by themselves restart a healthy decoder;
- a semantic seek increments the decoder/re-anchor revision and STATE is sufficient to tell the client that fresh codec state is required;
- a client which has no usable local decoder may create/recreate one from the authoritative STATE without requiring the server revision itself to change;
- local cancellation must invalidate the old worker identity before waking/cancelling that worker.

This is intentionally a protocol revision rather than a patch which continues to depend on CONTROL SEEK ordering.

### Open implementation simplification

Because the server already emits authoritative STATE after pause/resume/seek/volume/loop changes, the v7 implementation should explicitly compare two shapes before coding:

1. keep CONTROL packets as low-latency hints while STATE remains the sole correctness authority;
2. remove redundant finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL messages and let STATE be the sole server-to-client authority, retaining only whatever terminal/lifecycle packet is actually still necessary.

The second shape is a larger immediate protocol cleanup but may reduce code, ordering states, and tests overall. Do not preserve duplicate control paths merely for compatibility with an unreleased internal protocol.

## 2. Fixed M1G listening/delivery radius

M1G will **not** implement volume-dependent network relevance or dynamic audible range.

The core M1G rule is:

- the HQ finite source has one fixed maximum delivery/listening radius;
- within that radius, distance attenuation makes the sound quieter with distance;
- HQ `volume` changes gain/loudness, not the core delivery radius;
- no M1H late-entry/leave machinery is pulled into M1G merely to support volume-based radius changes;
- future Sound Physics Remastered compatibility owns any deliberate extension/adaptation of source range and the matching server transport relevance.

The current implementation already uses a fixed 32-block server relevance radius. The owner has selected the fixed-radius policy but has not separately requested a different numeric radius, so 32 blocks remains the conservative existing value unless changed explicitly.

### Renderer consequence

Minecraft/CC:T normally allow volume above 1 to enlarge linear attenuation distance. That behavior does not match this selected HQ contract.

M1G should therefore make the HQ finite channel's attenuation distance follow the fixed HQ radius rather than `max(volume, 1) * attenuationDistance`. Volume updates should refresh gain without silently enlarging the HQ radius.

`HQSoundChannelControl` already exposes the underlying Minecraft `Channel`, so the implementation can set the chosen fixed linear attenuation distance explicitly instead of copying CC:T's volume-scaled attenuation workaround.

Do not build an SPR abstraction/plugin API in M1G solely for this future work. Keep the fixed-radius policy localized so SPR compatibility can replace/extend it later.

## 3. Global volume zero: hibernate transport/rendering, not time

Setting the HQ playback volume to exactly zero does not pause the canonical server timeline.

For a globally zero-volume finite session:

- keep the server playback/session clock alive;
- clients keep enough session metadata to accept later authoritative STATE;
- stop/cancel the local decoder and renderer;
- stop requesting encoded media ranges while the HQ volume remains zero;
- when volume becomes non-zero again, recreate local decoder/render state from the current authoritative position/anchor and continue from current server time.

This is targeted volume-zero hibernation, not a reason to implement general dynamic listener membership in M1G.

A player's personal Minecraft MASTER/BLOCKS slider is different: the server cannot know it and it must not change server transport policy. `canStartSilent()` may still be useful for that client-local case and for avoiding a one-way renderer-start latch when Minecraft considers the sound locally inaudible.

## 4. Looping: ordinary replay, no gapless scope

Looping in M1G means ordinary "play the same media again" behavior.

The project does **not** currently require:

- sample-gapless loop boundaries;
- LAME/Xing encoder-delay/padding trimming;
- loop-head prefetch specifically to hide every boundary;
- a permanent OpenAL/Minecraft source across loop iterations;
- special SPR loop continuity work.

At local physical EOF, if the authoritative session still says looping, the client may start a fresh local decoder/render iteration from the beginning. A normal restart gap is acceptable.

This is the simple form of the previously discussed L4 direction: server `looping=true` authorizes local replay, but M1G does not build a gapless/continuous-source subsystem around it.

Seek/replacement/stop still supersede a stale local loop restart through the normal generation/revision checks. General severe-starvation rejoin remains M1H unless a concrete M1G correctness bug requires a narrower fix.

## 5. What remains in M1G

The narrowed M1G source work is:

1. replace anchor-change-as-intent with the explicit decoder/re-anchor revision and eliminate the KI-053/KI-056/KI-057 race/coupling cluster;
2. implement the selected fixed-radius renderer contract so volume changes gain without extending the core HQ range;
3. fix renderer-start robustness, including the locally-silent start case, while preserving global-volume-zero hibernation;
4. implement ordinary local replay for looping, accepting normal restart gaps;
5. add deterministic cancellation/seek tests, a real progressive MP3 fixture path, and focused `FinitePcmAudioStream` tests;
6. fix the staging cleanup leak (KI-061) and keep KI-054 as separate shutdown hardening unless priority changes;
7. run both NeoForge targets and focused Minecraft runtime acceptance.

## Explicitly deferred

The following are not M1G requirements unless a later concrete bug proves otherwise:

- dynamic volume-aware listener radius;
- general late-entry/out-of-range/re-enter lifecycle;
- SPR acoustic/range integration;
- gapless MP3 metadata handling;
- continuous-source loop engineering;
- generalized long-underrun current-time rejoin;
- native FLAC.
