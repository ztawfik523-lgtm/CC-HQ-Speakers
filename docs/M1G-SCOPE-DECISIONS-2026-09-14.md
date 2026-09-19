# M1G scope decisions — 2026-09-14

Updated with the 2026-09-17 recheck/audit conclusions. Postscript updated 2026-09-19.

This document records the owner's narrowed M1G direction after rechecking the progressive finite player against current source, Minecraft 1.21.1 sound behavior, CC:T 1.120.0 speaker behavior, and planned Sound Physics Remastered compatibility.

It is a current design-decision record. Historical milestone documents remain historical. Exact current source still wins for implementation status.

M1G is now implemented at final source checkpoint `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542` using protocol **v7**. The decisions below are the implemented M1G contract; historical option language is retained only for rationale.

## 1. Decoder/re-anchor semantics: explicit revision

M1G will use an explicit server-authoritative decoder/re-anchor revision rather than inferring restart intent from whether a codec anchor changed.

The intended contract is:

- a new media playback uses a new generation;
- ordinary STATE reconciliation, pause/resume, volume changes, and ordinary loop-state snapshots do not by themselves restart a healthy decoder;
- a semantic seek increments the decoder/re-anchor revision and STATE is sufficient to tell the client that fresh codec state is required;
- a client which has no usable local decoder may create/recreate one from authoritative STATE without requiring the server revision itself to change;
- local cancellation invalidates the old worker identity before waking/cancelling that worker.

This is intentionally a protocol revision rather than a patch which continues to depend on CONTROL SEEK ordering.

### Open implementation simplification

Because the server already emits authoritative STATE after pause/resume/seek/volume/loop changes, the v7 implementation should compare two shapes before coding:

1. keep CONTROL packets as optional low-latency hints while STATE remains the sole correctness authority;
2. remove redundant finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL messages and let STATE be the sole server-to-client transition authority, retaining only terminal/lifecycle packets which still serve a distinct purpose.

The second shape is a larger immediate protocol cleanup but may reduce code, ordering states, and tests overall. Do not preserve duplicate control paths merely for compatibility with unreleased internal v6.

### Rechecked anchor fact

`FiniteDecodeAnchorSelector.Anchor` contains exactly two fields: encoded byte offset and anchor time in seconds. STATE already carries both. There are no hidden frame-index/skip-frame/skip-sample fields being computed and discarded, so v7 does **not** need to invent fields to recover nonexistent lost metadata.

## 2. Fixed M1G listening/delivery radius

M1G will **not** implement volume-dependent network relevance or dynamic audible range.

The core M1G rule is:

- the HQ finite source has one fixed maximum delivery/listening radius;
- within that radius, distance attenuation makes sound quieter with distance;
- HQ `volume` changes gain/loudness, not the core delivery radius;
- no M1H late-entry/leave machinery is pulled into M1G merely to support volume-based radius changes;
- future Sound Physics Remastered compatibility owns deliberate extension/adaptation of source range and matching server transport relevance.

The current implementation already uses a fixed 32-block server relevance radius. The owner selected the fixed-radius policy and did not request another numeric radius, so 32 blocks remains the current M1G value unless changed explicitly.

### Renderer consequence

Minecraft/CC:T normally allow volume above 1 to enlarge linear attenuation distance. That behavior does not match the selected HQ finite contract.

M1G should therefore make the modern finite channel's attenuation distance follow the fixed HQ radius rather than `max(volume, 1) * attenuationDistance`. Volume updates should refresh gain without silently enlarging the HQ radius.

Do not build an SPR plugin abstraction in M1G solely for future compatibility. Keep the fixed-radius policy localized so SPR compatibility can replace/extend it later.

## 3. Global volume zero: hibernate transport/rendering, not time

Setting HQ playback volume to exactly zero does not pause the canonical server timeline.

For a globally zero-volume finite session:

- keep the server playback/session clock alive;
- clients keep enough session metadata to accept later authoritative STATE;
- stop/cancel the local decoder and renderer;
- stop requesting encoded media ranges while HQ volume remains zero;
- when volume becomes non-zero again, recreate local decoder/render state from the current authoritative position/anchor and continue from current server time.

This is targeted volume-zero hibernation, not a reason to implement general dynamic listener membership in M1G.

A player's personal Minecraft MASTER/BLOCKS slider is different: the server cannot know it and it must not change server transport policy. `canStartSilent()` may still be useful for that client-local case and for avoiding a one-way renderer-start latch when Minecraft considers the sound locally inaudible.

## 4. Looping: ordinary replay, no gapless scope

Looping in M1G means ordinary “play the same media again” behavior.

The project does **not** currently require:

- sample-gapless loop boundaries;
- LAME/Xing encoder-delay/padding trimming;
- loop-head prefetch specifically to hide every boundary;
- a permanent OpenAL/Minecraft source across loop iterations;
- special SPR loop-continuity work.

At local physical EOF, if the authoritative session still says `looping=true`, the client may start a fresh local decoder/render iteration from the beginning. A normal restart gap is acceptable.

This is the simple form of the earlier L4 discussion: server `looping=true` authorizes local replay, but M1G does not build a gapless/continuous-source subsystem around it.

Seek/replacement/stop still supersede stale local loop restart through normal generation/revision checks. General severe-starvation rejoin remains M1H unless a concrete M1G correctness bug requires a narrower fix.

Do not reopen L1/L2/L3 or L4a/L4b unless the owner explicitly changes the loop requirement.

## 5. Rechecked repository-audit conclusions

The September 16 full-repository audit was challenged against exact source. Its useful surviving findings should influence sequencing, but not expand M1G indiscriminately.

### KI-062 — shared composite monitor + blocking DNS

Dynamic legacy stream dispatch enters synchronized `HQSpeakerCompositePeripheral.callMethod(...)` and can perform synchronous `InetAddress.getAllByName(...)` while holding that monitor.

The server tick's `tickOwnership()` is synchronized on the same composite. `cleanup()` is also synchronized and is reached through provider `forget`, `forgetLevel`, and `clearAll` during removal, Level unload, and server stop.

Therefore a DNS-parked ComputerCraft thread can block both server-tick ownership work and lifecycle cleanup waiting for the composite monitor.

`audioPrepareStaged(...)` is not itself synchronized on that monitor; do not broaden the claim to all media operations.

This is a real cross-cutting safety problem, but fixing it should remain narrow: move/block external DNS/I/O outside the ownership monitor or otherwise remove server-thread dependence on that monitor. Do not turn it into an M3 live-stream rewrite.

### KI-063 — replacement-before-admission

RAW/prepared replacement can stop/transfer current HQ ownership before all failure/admission conditions for the new source are known. A rejected or failed replacement should not destroy valid current playback unless that destructive behavior is explicitly intended.

### KI-054 / KI-064 — storage/shutdown hardening

- shutdown can lose completed-file deletion retry state;
- an earlier `FiniteRangeReadService.close()` failure can prevent `MediaAssetStore.close()` entirely, leaving the root lock and stopped-server registry entry alive in the JVM;
- `MediaAssetStore.writeExact()` can spin indefinitely on repeated zero-byte reads;
- import has no fallback when `ATOMIC_MOVE` is unsupported.

These are real, but they are separate from decoder protocol design.

### Retracted audit claims

Do not use the following as current design evidence:

- there are no richer MP3 anchor fields beyond `(offset, seconds)`;
- STATE does not carry live x/y/z coordinates;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no fabricated composite back-reference;
- `HQFiniteMediaServer.tick()` does not itself perform the per-player projection work attributed to it in the first audit draft;
- inherited HTTP stream paths do close their streams;
- release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## 6. M1H / VS2 movement clarification

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry x/y/z. `FiniteSpeakerSound.updatePosition(...)` exists but is not called by the current modern finite client after renderer creation.

M1H therefore still owns moving-source lifecycle, but a new wire position-update mechanism is **not automatically required**.

Two later approaches remain valid:

1. mirror the inherited client-side VS2 `tickPosition` pattern and recompute ship-transformed world position from BEGIN block coordinates;
2. add an explicit authoritative position-update mechanism if later lifecycle/network requirements justify it.

This is a real design tradeoff and remains open for M1H rather than M1G.

## 7. M1G closeout

The narrowed M1G source work is complete.

Implemented:

1. explicit server-authoritative decoder/re-anchor revision closing KI-053/KI-056/KI-057;
2. fixed 32-block renderer attenuation contract closing KI-058;
3. renderer-start recovery, client-local silent-start support, and global-volume-zero hibernation closing KI-060;
4. ordinary local replay for looping closing KI-051;
5. deterministic cancellation/revision, real-JLayer-MP3, and renderer-read policy coverage closing KI-055 at component level;
6. whole-owner staging cleanup closing KI-061;
7. both NeoForge targets green/package-verified at CI `35297026277`.

Focused M1G audible/core Minecraft acceptance was subsequently recorded PASS on NeoForge 21.1.247 on 2026-09-19, resolving KI-046. NeoForge 21.1.248 remains CI/package verified rather than manually runtime-verified.

At M1G closeout, KI-062, KI-063, KI-054, and KI-064 remained post-M1G cross-cutting hardening rather than being folded into M1G. They were subsequently resolved on `codex/post-m1g-hardening` at source checkpoint `3d30ce4564de749f32171666df65de739b08ad77`, latest full verification CI `35406595856`.

## Explicitly deferred

The following are not M1G requirements unless a later concrete bug proves otherwise:

- dynamic volume-aware listener radius;
- general late-entry/out-of-range/re-enter lifecycle;
- SPR acoustic/range integration;
- gapless MP3 metadata handling;
- continuous-source loop engineering;
- generalized long-underrun current-time rejoin;
- native FLAC;
- inherited HLS/TS repair;
- legacy multispeaker helper repair;
- separate `hqspeaker:hq_speaker` product completion;
- broad CI/repository/release cleanup.
