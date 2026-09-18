# Current state

Updated: 2026-09-19

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E, M1F, and M1G are complete at source/test/CI/package level.

Final M1G **source** checkpoint:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

CI `35297026277` passed NeoForge 21.1.247 and 21.1.248, including build, deterministic tests, packaged-mod verification, and artifact upload.

Artifacts:

- NeoForge 21.1.247: artifact `10527498657`, SHA-256 `3b873edd94a924cf3922a75cdd059c2b3963500787bd479ec8b386600ef055b1`;
- NeoForge 21.1.248: artifact `10528645404`, SHA-256 `3b9304a509d37bf2ef1c0797d4449fa2cb6c8cc705678e63a68ed8c2ef72f8b1`.

The failed intermediate CI run `35287579710` was a test-source-set problem only: a direct JUnit test referenced Minecraft's client-only `AudioStream` interface. Main source compiled on both targets. The test was replaced with a pure renderer-read adapter shared by `FinitePcmAudioStream`, and the final checkpoint is green.

Green CI by itself is still not Minecraft runtime proof. A separate focused **M1G audible/core Minecraft runtime PASS was recorded on 2026-09-19** using NeoForge 21.1.247 in integrated singleplayer. The run exercised modern WAV/MP3 playback, pause/resume, seek/reanchor, natural EOF, ordinary looping, positional attenuation/fixed-range behavior, stop, prepared-asset lifetime, float32 WAV, and global-volume-zero hibernation/unmute. NeoForge 21.1.248 remains CI/package verified but was not manually runtime-tested in that session.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> protocol v7 descriptor + STATE decodeRevision + codec-aware anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> pure renderer-read policy
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Completed M1G contract

Locked media/renderer choices remain:

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer;
- B1: server-normalized common-WAV layout;
- C1: preserve source sample rate;
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1: conservative MP3 pre-roll from an earlier analyzed seek point;
- modern prepared formats: MP3 + supported common WAV only.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

### Decoder/re-anchor authority

Protocol v7 now carries an explicit server-authoritative `decodeRevision`.

- new media playback uses a new generation;
- semantic seek increments the revision;
- ordinary STATE, pause/resume, volume, and loop-state snapshots do not restart a healthy decoder;
- STATE alone is sufficient for seek/reanchor correctness;
- nonterminal PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL projection was removed;
- explicit STOP remains because stopping removes the server session;
- a client with no usable local decoder may rebuild from current authoritative STATE without changing the server revision;
- local worker identity is invalidated before cancellation can wake/report.

This closes KI-053, KI-056, and KI-057 at source/component-test level.

### Fixed 32-block core range

M1G intentionally keeps a fixed 32-block server delivery/listening radius.

The active Minecraft channel is explicitly assigned the same fixed attenuation distance. HQ volume changes gain/loudness but does not expand the core radius. Future Sound Physics Remastered work owns deliberate acoustic/range extension and matching transport relevance.

This closes KI-058; KI-059 remains the recorded product decision.

### Global volume zero and renderer lifecycle

Global HQ volume exactly zero keeps canonical server time running while local finite transport/rendering hibernates:

- decoder/renderer state is cancelled;
- client range demand stops;
- server drops new/completing finite range delivery while globally muted;
- non-zero volume rebuilds from current authoritative STATE/anchor.

Player-local MASTER/BLOCKS mute remains separate. `FiniteSpeakerSound.canStartSilent()` allows Minecraft to create the long-lived sound while locally inaudible.

Renderer start is no longer permanently latched before `SoundManager.play(...)` succeeds. The client observes actual activation/EOF and requests authoritative rejoin after failed or unexpectedly lost renderer activation.

This closes KI-060 at source/component level.

### Looping

M1G looping is ordinary replay, not gapless playback.

At physical local EOF, if authoritative state still says `looping=true`, the client starts a fresh local decoder/render iteration and catches up to the current canonical loop position. A normal restart gap is acceptable.

M1G intentionally does not implement LAME/Xing gapless padding trim, loop-head prefetch solely to hide boundaries, or permanent-source loop architecture.

This closes KI-051.

### Deterministic evidence and staging cleanup

M1G now includes:

- pure `FiniteDecodeCoordinator` tests for revision/stale-state/local-worker invalidation behavior;
- a real synthetic mono 44.1 kHz MP3 fixture decoded through packaged JLayer;
- MP3 coverage across initial starvation, repeated bounded range refill, and multiple `FiniteRangeWindow` slides;
- MP3 pre-target discard coverage;
- `FinitePcmReadAdapter` tests for DATA, starvation-as-silence, physical EOF, cancellation, and bounded reads;
- existing bounded PCM queue, WAV decode, range-window, state-machine, and transport tests;
- `HQMediaStaging` whole-owner cleanup of persistent staging leftovers with deterministic tests.

Direct JUnit loading of `FinitePcmAudioStream` is not used because the ordinary NeoForge test source set does not expose Minecraft's client-only `AudioStream` class. The renderer-read policy is extracted into the pure adapter actually used by `FinitePcmAudioStream`; the Minecraft adapter itself is compile/package verified on both targets.

This closes KI-055 and KI-061 at deterministic/source level.

## M1G closeout verdict

M1G is closed as the **core progressive finite engine engineering milestone**.

Resolved in the M1G closeout:

- KI-051 — ordinary replay;
- KI-053 — same-anchor STATE/window reset hazard;
- KI-055 — real MP3 + renderer-read deterministic coverage;
- KI-056 — cancellation/failure race;
- KI-057 — snapshot versus reanchor ambiguity;
- KI-058 — fixed attenuation contract;
- KI-060 — renderer start + global-zero hibernation;
- KI-061 — staging leftovers.

KI-052's deterministic/source portion is satisfied by the final component suite and re-audit. KI-046 is now also satisfied by the recorded focused live Minecraft/SoundManager/OpenAL run on NeoForge 21.1.247. This does not absorb later M1H recovery/resource-reload work or post-M1G KI-062/063/054/064.

## Open after M1G

These remain real but are **post-M1G** work:

- KI-062 — synchronized legacy stream dispatch can hold the composite monitor across blocking DNS;
- KI-063 — replacement-before-admission can destroy current valid playback;
- KI-054 — shutdown deletion retry/root-lock hardening;
- KI-064 — asset-import no-progress and non-atomic-move fallback;
- M1H — late entry, proactive leave, rejoin, resource/dimension recovery, general underrun recovery, and final VS2 movement lifecycle;
- M1I — gated native FLAC;
- M1J/K/L/N/O/P/Q — later synchronization, migration, sound-engine cleanup, hardening, package/release work;
- M2 — Sound Physics Remastered;
- M3 — live/open-ended stream rebuild;
- M4 — public release cleanup.

KI-062/063/054/064 were deliberately not folded into M1G merely because the broad audit found them. They are cross-cutting ownership/storage hardening and remain explicitly visible rather than silently marked resolved.

## Rechecked repository-audit corrections to preserve

- `FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`;
- modern finite STATE does not carry x/y/z; BEGIN carries initial world/block position;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- `HQFiniteMediaServer.tick()` does not itself iterate/project players every tick;
- inherited HTTP stream paths do close their streams;
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## M1H / VS2 movement boundary

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry live position updates. `FiniteSpeakerSound.updatePosition(...)` exists but M1G does not drive it after renderer creation.

M1H may either mirror the inherited client-side VS2 transform path from BEGIN block coordinates or add explicit authoritative position updates if later lifecycle requirements justify that. No M1G wire expansion is required merely to settle that future choice.

## Evidence boundaries

```text
M1E source/test/CI: PASS
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F source/test/CI/package/component: PASS
M1F focused Minecraft transport acceptance: unrecorded
M1G source/test/CI/package/component: PASS at fa679ffcb81a66fd99ab6be8e6d6b77895fbc542
M1G protocol: v7
M1G decoder snapshot/reanchor correctness: source/component PASS
M1G fixed-range volume/start correctness: source/component PASS
M1G real-MP3 progressive coverage: PASS
M1G renderer-read policy coverage: PASS
M1G staging lifecycle cleanup: PASS
M1G ordinary replay: implemented
M1G focused audible/core Minecraft PASS: recorded 2026-09-19 on NeoForge 21.1.247 integrated singleplayer (KI-046 resolved)
Cross-cutting synchronized-DNS/main-thread stall: open KI-062
Cross-source replacement-before-admission: open KI-063
Storage import progress/rename hardening: open KI-064
Shutdown lock/retry hardening: open KI-054
```

## Read order

1. `CURRENT-STATE.md`
2. `HANDOFF-2026-09-18-M1G-COMPLETE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1G-SCOPE-DECISIONS-2026-09-14.md`
7. `FUTURE-CLEANUP.md`
8. `ARCHITECTURE.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
