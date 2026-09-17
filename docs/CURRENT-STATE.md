# Current state

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, package verification, and artifact upload. Documentation-only commits after that checkpoint do not change the integrated source baseline.

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

## Locked M1G media decisions

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer.
- B1: server-normalized common-WAV layout.
- C1: preserve source sample rate.
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support.
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.
- modern prepared formats remain MP3 + supported common WAV only.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Integrated source

M1G source includes the MP3/common-WAV prepared gate, protocol-v6 decode descriptor, exact WAV/E1 MP3 anchors, starvation-aware `FiniteEncodedInputStream`, bounded `FinitePcmQueue`, progressive WAV conversion, progressive JLayer MP3 decoding, decoder-epoch cancellation, bounded prebuffer/catch-up, `FinitePcmAudioStream`, and positional `FiniteSpeakerSound` through `SoundSource.BLOCKS`.

Pause/resume/volume use the Minecraft channel-control path. Seek/replacement/stop discard old decoder/PCM/renderer state. Temporary PCM starvation becomes bounded silence rather than fake EOF.

## Current correctness/evidence blockers

The deepest modern-finite source issue is the KI-053/KI-056/KI-057 decoder restart/re-anchor cluster:

- **KI-053:** an ordinary same-anchor STATE can reset an already-slid encoded window without restarting the decoder, potentially making the live cursor stale.
- **KI-056:** expected SEEK cancellation can race into `decoderFailed()` before replacement state exists because the old worker token is not invalidated first.
- **KI-057:** STATE currently doubles as both timeline snapshot and implicit decoder-reanchor instruction; changed time-derived anchors can restart healthy playback, while same-anchor semantic seek still depends on the preceding CONTROL packet.

Other M1G/open storage items:

- **KI-058:** live modern-finite volume/channel attenuation behavior does not yet implement the selected fixed-range HQ contract.
- **KI-060:** renderer startup is latched before Minecraft proves the sound actually started; locally-silent start and retry behavior need hardening.
- **KI-055:** real progressive MP3 fixture coverage and focused `FinitePcmAudioStream` coverage are still missing.
- **KI-061:** per-speaker persistent staging mounts can leave unreachable files on disk.
- **KI-054:** shutdown cleanup has two failure shapes: completed-file deletion retry state can be lost, and an earlier `FiniteRangeReadService.close()` failure can prevent `MediaAssetStore.close()` entirely, leaving the root lock and stopped-server registry entry alive in the JVM.

The rechecked 2026-09-16 full-repository review also found three cross-cutting items which are not part of the decoder protocol but are too concrete to leave as vague future cleanup:

- **KI-062:** synchronized dynamic stream dispatch can hold the composite monitor across blocking DNS while the server tick needs the same monitor, allowing a slow lookup to stall the server main thread;
- **KI-063:** RAW/prepared replacement paths stop the current HQ source before all rejection/failure conditions are known, so a rejected replacement can destroy valid playback;
- **KI-064:** `MediaAssetStore.writeExact()` has an unbounded zero-read spin and the import rename has no fallback when `ATOMIC_MOVE` is unsupported.

Historical KI-059 described fixed 32-block relevance as conflicting with vanilla volume-3 reach. That conflict is now an intentional product choice rather than an M1G requirement: HQ finite playback will use a fixed core range, while future SPR compatibility owns any extended acoustic/delivery range.

See `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, and `M1G-SCOPE-DECISIONS-2026-09-14.md`.

## Owner-selected M1G direction

### 1. Explicit decoder/re-anchor revision

M1G will move away from using codec-anchor changes as decoder restart intent.

The intended v7-style rule is:

- new media playback => new generation;
- semantic seek => decoder/re-anchor revision increments;
- ordinary STATE, pause/resume, volume changes, and ordinary loop-state snapshots do not restart a healthy decoder;
- a client with no usable local decoder may rebuild from authoritative STATE without requiring the server revision itself to change;
- old decoder identity must be invalidated before cancellation can wake that worker.

STATE must be self-sufficient for seek/reanchor correctness. Do not retain correctness dependence on CONTROL SEEK ordering.

There is still one implementation-shape decision worth comparing before coding: keep finite CONTROL packets only as optional latency hints, or simplify protocol v7 further so STATE is the sole authority for pause/resume/seek/volume/loop changes. The latter is more churn now but can remove duplicate ordering states and tests.

### 2. Fixed M1G listening/delivery radius

M1G will **not** implement volume-dependent network relevance or dynamic audible range.

The selected contract is:

- one fixed maximum HQ finite radius;
- within that radius, normal positional attenuation makes sound quieter with distance;
- HQ `volume` changes loudness/gain, not the core HQ range;
- no M1H late-entry/leave/re-enter machinery is pulled forward merely for volume-based radius changes;
- future Sound Physics Remastered compatibility owns deliberate extended-range/acoustic behavior and the matching transport relevance.

The source already uses `SPEAKER_RADIUS = 32.0`. The owner selected the fixed-radius policy but has not separately requested another numeric radius, so 32 blocks remains the conservative current value unless explicitly changed.

Because Minecraft/CC:T normally scale attenuation distance when volume is above 1, the renderer must deliberately enforce the fixed HQ attenuation distance rather than copy CC:T's `max(volume, 1) * attenuationDistance` workaround. Volume updates should change gain while the HQ distance cap remains fixed.

Keep that policy localized. Do not build an SPR plugin abstraction in M1G solely for future compatibility.

### 3. Global volume zero

Canonical playback time continues when HQ volume is exactly zero.

The selected M1G behavior is targeted transport/render hibernation:

- keep the server session/clock alive;
- keep enough client session metadata for later authoritative STATE;
- stop/cancel the local decoder and renderer;
- stop requesting encoded media ranges while globally muted;
- when volume becomes non-zero again, rebuild from the current authoritative position/anchor and continue from current server time.

This does not require general dynamic listener membership.

A player's own Minecraft MASTER/BLOCKS slider is client-local and must not change server transport policy. `canStartSilent()` may still be useful for that case and for renderer-start robustness.

### 4. Looping is ordinary replay

Looping is deliberately simple in M1G. The project does not currently care about gapless boundaries.

At local physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration. A normal restart gap is acceptable.

Do **not** add M1G work for:

- sample-gapless loop boundaries;
- LAME/Xing delay/padding trimming;
- loop-head prefetch solely to hide the boundary;
- a permanent Minecraft/OpenAL source across loop iterations;
- SPR-specific continuity at loop boundaries.

Seek/replacement/stop still override stale local work through generation/revision checks. General severe-starvation rejoin remains M1H unless a concrete M1G correctness bug requires a narrower fix.

## Rechecked 2026-09-16 audit: what changed the plan

The broad audit was useful but is not authoritative by itself. Exact-source rechecking kept the concrete findings above and rejected/qualified several claims:

- `FiniteDecodeAnchorSelector.Anchor` is only `(offset, seconds)`; there are no richer frame/skip fields being computed and discarded by `HQFiniteMediaServer`.
- modern finite STATE does **not** carry world x/y/z. BEGIN carries the initial position. `FiniteSpeakerSound.updatePosition(...)` currently has no M1G call site, so moving-source/VS2 position remains a real M1H gap, but not for the reason the audit originally gave.
- `audioPrepareStaged(...)` is not synchronized on the composite monitor. The confirmed monitor/DNS freeze path is the synchronized dynamic stream dispatch, not every media operation.
- inherited HLS progression, legacy format-advertising mismatches, legacy `playNoteAll`/`playSoundAll` semantics, and the incomplete separate `hqspeaker:hq_speaker` product surface are real/largely confirmed but remain later cleanup unless the owner changes priority. They are recorded in `FUTURE-CLEANUP.md` rather than expanding M1G.

## Narrowed implementation order

There are two work streams with different scope:

**Cross-cutting safety/hardening:** KI-062 should be addressed before relying on legacy stream calls on a server because it can stall the main thread. KI-063 is a small ownership/admission correctness repair. KI-054 and KI-064 are storage/shutdown hardening and can be grouped when storage code is touched.

**M1G player completion:**

1. Fix KI-053/KI-056/KI-057 together with the explicit decoder/re-anchor revision.
2. Implement the fixed-radius renderer contract: fixed attenuation distance, volume as gain only, and robust live channel updates.
3. Implement global-volume-zero hibernation and fix renderer-start robustness/local silent-start handling.
4. Implement ordinary local replay for `looping=true`; accept normal loop gaps.
5. Add deterministic cancellation/seek coverage, a real progressive MP3 fixture path, and focused `FinitePcmAudioStream` tests.
6. Fix KI-061 staging cleanup; combine or sequence KI-054/KI-064 storage hardening based on source-change scope.
7. Run both NeoForge targets and focused Minecraft modern-prepared runtime acceptance.

This ordering does not silently choose whether protocol-v7 CONTROL hints survive. That remains the one meaningful implementation-shape choice before the decoder-revision source patch.

## Explicitly deferred

Unless a concrete correctness bug proves otherwise, M1G does not include:

- dynamic volume-aware listener radius;
- general late-entry/out-of-range/re-enter lifecycle;
- Sound Physics Remastered integration;
- gapless MP3 metadata handling;
- continuous-source loop engineering;
- generalized long-underrun current-time rejoin;
- native FLAC;
- inherited HLS/TS repair or legacy API migration;
- finishing the separate `hqspeaker:hq_speaker` block as a second product.

Those remain later milestones/compatibility work.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F focused Minecraft transport acceptance: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G fixed-range volume/start correctness: open KI-058/KI-060
M1G staging lifecycle cleanup: open KI-061
Cross-cutting synchronized-DNS/main-thread stall: open KI-062
Cross-source replacement-before-admission: open KI-063
Storage import progress/rename hardening: open KI-064
M1G real-MP3 progressive integration coverage: incomplete
M1G focused FinitePcmAudioStream coverage: incomplete
M1G ordinary replay loop implementation: not yet implemented
M1G audible Minecraft PASS: unrecorded
```

## Read order

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `FUTURE-CLEANUP.md`
7. `HANDOFF-2026-09-13-M1G-START.md`
8. `M1G-DESIGN-DECISIONS-2026-09-13.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
