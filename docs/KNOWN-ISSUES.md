# Known issues / product gaps

Updated: 2026-09-19

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277` on NeoForge 21.1.247 and 21.1.248. Post-M1G hardening checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`, CI `35406434097`, also green on both targets with package verification/artifacts.

Current owner scope is recorded in `M1G-SCOPE-DECISIONS-2026-09-14.md`. Historical option lists do not override it.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.** M1F source/test/CI/package/component work is complete.

### KI-046 — M1G audible/core Minecraft acceptance

**Resolved as focused M1G runtime evidence on 2026-09-19.** NeoForge 21.1.247 integrated singleplayer recorded audible modern WAV/MP3 playback, pause/resume, seek/reanchor, float32 WAV, natural EOF, ordinary looping, positional attenuation/fixed 32-block range behavior, prepared-asset lifetime, stop, and a dedicated loop-safe global-volume-zero hibernation/unmute PASS. No HQSpeaker WARN/ERROR lines were present during the focused mute/unmute run.

This is not a claim that NeoForge 21.1.248 was manually runtime-tested, nor does it subsume later KI-062/063/054/064 or M1H recovery/resource-reload work.

## Resolved by M1E/M1F

- KI-040: projection escaping canonical transitions — resolved by best-effort per-recipient projection.
- KI-041: `playPrepared()` ghost-session rollback — resolved.
- KI-042: failed final MediaAsset release losing retry ownership — resolved for active prepared/playback/range paths.
- KI-045: finite server ERROR advancing time — resolved; ERROR freezes position.
- KI-043: encoded window not progressively consumable — resolved by bounded forward sliding.
- KI-044: incomplete M1F deterministic matrix — resolved at component level.
- KI-026: modern prepared client requiring a complete song file — resolved.
- KI-027: finite asset reads on server tick — resolved by bounded range IO workers.
- KI-030: no demand-driven finite protocol — resolved.
- KI-031: missing encoded data indistinguishable from EOF — resolved.
- KI-038: `audioPlayStaged()` prototype route — removed.
- KI-039: modern CHUNK/END whole-file path — removed.

## M1G source-resolved items

M1G is closed at source/test/CI/package/component level, and the focused real-Minecraft audible/core proof is now recorded under resolved KI-046.

### KI-021 — modern finite format surface needed narrowing

**Resolved for modern prepared/local playback.** Prepared assets are gated to MP3 or supported common WAV.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Resolved in source.** Server anchors use E1 conservative earlier seek points and JLayer decodes forward/discards pre-target PCM. The 2026-09-19 focused runtime pass also recorded audible MP3 seek/reanchor behavior.

### KI-033 — historical WAV acceptance broader than converter target

**Resolved for modern prepared playback.** Common WAV is narrowed to U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or the selected narrow PCM/float WAVEX subset.

### KI-047 / KI-048 / KI-049 / KI-050 — client integration, WAV decoder, MP3 decoder, renderer

**Resolved in source.** The bounded progressive decode/render pipeline is integrated and package-verified.

### KI-051 — ordinary replay

**Resolved in source.** Physical local EOF restarts a fresh decoder/render iteration when authoritative state still says `looping=true`. A normal restart gap is intentionally acceptable.

### KI-052 — focused integrated M1G control/lifecycle proof

**Resolved at deterministic/source/component level.** Revision/cancellation ordering, real JLayer decode, bounded range-window progression, renderer-read states, staging cleanup, and the existing transport/PCM/WAV/state-machine suites cover the non-Minecraft portions.

The focused live Minecraft SoundManager/OpenAL path now has a recorded NeoForge 21.1.247 PASS under KI-046. Broader resource-reload, long-underrun, late-entry/rejoin, and moving-source lifecycle remain M1H rather than being folded into this evidence item.

### KI-053 — same-anchor STATE could reset a slid window

**Resolved.** Protocol v7 separates decoder restart intent from codec-anchor movement. Same-revision ordinary STATE preserves a healthy decoder/window.

### KI-055 — real-MP3 and renderer-adapter deterministic coverage

**Resolved.** A real synthetic MP3 fixture runs through packaged JLayer across initial starvation and multiple bounded range-window slides, including pre-target discard. Renderer-facing DATA/STARVED/EOF/CANCELLED policy is extracted into `FinitePcmReadAdapter`, which is used by `FinitePcmAudioStream` and is directly unit tested.

The direct Minecraft `AudioStream` interface itself is compile/package verified because NeoForge's ordinary JUnit source set does not expose that client-only class.

### KI-056 — expected cancellation could report as fatal decoder failure

**Resolved.** Local worker identity is invalidated before cancellation wakes/cancels the old decoder, so stale failure callbacks are rejected.

### KI-057 — STATE did not distinguish snapshot from decoder-reanchor intent

**Resolved by protocol v7.** STATE carries server-authoritative `decodeRevision`; semantic seek increments it. Ordinary STATE does not restart a healthy decoder. PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL projection was removed; STATE is the sole nonterminal transition authority.

### KI-058 — fixed-range attenuation contract

**Resolved in source.** The live modern finite channel explicitly uses the selected fixed 32-block attenuation distance while HQ volume changes gain.

### KI-059 — fixed-radius policy selected

**Product decision resolved.** Server delivery/listening radius remains 32 blocks for M1G. Dynamic volume-aware listener membership was not pulled forward.

### KI-060 — renderer startup / global-volume-zero behavior

**Resolved in source.** Renderer state is latched only after `SoundManager.play(...)` returns, later activation is observed, failed/lost activation can request authoritative rejoin, `canStartSilent()` supports client-local mute, and global HQ volume zero hibernates decoder/render/range work while canonical server time continues.

### KI-061 — per-speaker staging leftovers

**Resolved in source.** Whole-owner staging cleanup deletes all top-level persistent staging entries after computers are unmounted and owner references are released. One-computer detach does not clear the shared mount.

## Resolved post-M1G correctness / hardening

### KI-062 — blocking stream URL lookup held the ownership monitor

**Resolved at source/test/CI level.** Blocking stream DNS validation runs outside both the ownership monitor used by `tickOwnership()`/`cleanup()` and the separate command-order lock. After validation, the normal single-speaker stream commit briefly rejoins command ordering and ownership locking. This matters because `audioPlayPrepared` is a CC:T main-thread method: it can no longer wait behind blocked DNS. A mutation revision rejects a normal stream start superseded by a newer playback/control command during DNS, and a lifecycle epoch rejects a result which returns after detach/cleanup.

Inherited `speakStream` / HLS / TS helpers may still block their calling ComputerCraft thread during DNS; M3 owns redesigning live streams themselves. The server tick/cleanup lock coupling is removed.

### KI-063 — replacement destroyed valid playback before admission

**Resolved for the known RAW/prepared paths.**

Prepared finite playback now performs validation, asset retain, media-service acquisition, descriptor/session construction, and initial-status construction before current ownership is stopped. An uncommitted prepared-start token releases its retained asset.

RAW replacement now validates/converts the full sample table and volume before stopping the previous source. Fresh replacement then starts against a cleared queue/lifetime.

### KI-054 — shutdown retry/root-lock hardening

**Resolved at source/component level.**

- range workers begin shutdown during `ServerStoppingEvent`;
- final close waits/drains during `ServerStoppedEvent`;
- a failed final close keeps the old server-assets entry reachable instead of dropping ownership;
- a genuinely new server instance retries old closing entries before opening the media root;
- the same stopping server cannot resurrect a new media service;
- `MediaAssetStore.close()` keeps failed-deletion entries/quota bookkeeping and the root lock until a later retry actually succeeds.

### KI-064 — import no-progress / atomic-move fallback

**Resolved at source/component level.**

- repeated zero-byte source reads are bounded and fail deterministically instead of spinning forever;
- temporary zero reads are tolerated up to the bound;
- unsupported `ATOMIC_MOVE` falls back to a same-root non-atomic move before the asset is published;
- deterministic tests cover no-progress failure, eventual progress, move fallback, and retryable close bookkeeping.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter and moving-source lifecycle is not final

M1H owns proactive out-of-range cleanup, late-entry discovery, return/rejoin, dimension/world/resource-reload recovery, robust general underrun rejoin, and final VS2 movement lifecycle.

The fixed M1G radius intentionally does **not** pull dynamic volume-aware listener membership forward.

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry x/y/z. `FiniteSpeakerSound.updatePosition(...)` exists but the current modern client does not call it after renderer creation.

A new position packet is not automatically required: the inherited client already recomputes VS2 ship-transformed position from block coordinates each tick. M1H may mirror that client-side pattern using BEGIN block coordinates, or add explicit authoritative position updates if later requirements justify it. Do not silently choose now.

## Gated/later work

- Sound Physics Remastered compatibility owns future acoustic/range extension and matching transport relevance; do not prebuild it in M1G.
- Gapless MP3/LAME/Xing delay/padding handling is not an M1G requirement.
- Continuous-source loop engineering is not an M1G requirement.
- KI-034: native FLAC remains gated M1I work.
- KI-018: inherited expected-member multispeaker barrier can deadlock partial listeners — M1J.
- KI-011 / KI-012 / KI-023: inherited finite engine/APIs remain legacy — M1L.
- KI-013 through KI-019: inherited live/HLS/TS/OpenAL lifecycle/gain issues — M3/later cleanup. A refreshed-live-playlist `currentSegmentIndex` progression bug is confirmed.
- inherited capability lists advertise unsupported/broader formats; later migration must make them truthful.
- inherited `playNoteAll`/`playSoundAll` do not preserve requested note/sound semantics; later multispeaker work must repair/remove them.
- KI-022: separate `hqspeaker:hq_speaker` registration remains a release-cleanup/product decision.
- KI-025: top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0; resolve provenance before public release and do not silently relicense.
- CI docs-only path filtering/concurrency cancellation remains repository hygiene, not M1G correctness.

## Rechecked audit corrections

The repository audit is supporting evidence, not current authority. The following first-draft claims were retracted and must not reappear as facts:

- no extra MP3 anchor frame/skip fields exist beyond `(offset, seconds)`;
- STATE does not carry live world coordinates;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference; the Level-keyed WeakHashMap explicitly depends on deterministic lifecycle eviction because cached values reference their Level;
- `HQFiniteMediaServer.tick()` does not itself iterate/project players every tick;
- inherited HTTP streaming paths do close their streams;
- release retry ticking is `ServerMediaAssets.tickPendingReleases()`.

## Current working priority

The post-M1G Option A hardening pass is complete. The next active engineering milestone is **M1H listener/rejoin/movement lifecycle**.

Do not reopen KI-062/063/054/064 without a concrete regression. M1H now owns late entry, proactive leave, return/rejoin, dimension/world/resource-reload recovery, robust general underrun rejoin, and final VS2 movement lifecycle.

## Reminders

- M1E final Minecraft PASS: skipped/unrecorded.
- M1F focused Minecraft transport PASS: unrecorded.
- M1G source/test/CI/package/component: PASS at `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`.
- M1G focused audible/core Minecraft PASS: recorded 2026-09-19 on NeoForge 21.1.247; KI-046 resolved.
- KI-051/053/055/056/057/058/060/061 are resolved at source/component level.
- KI-054/062/063/064 are resolved by post-M1G hardening checkpoint `3d30ce4564de749f32171666df65de739b08ad77`.
- Current authority: `CURRENT-STATE.md`, `HANDOFF-2026-09-18-M1G-COMPLETE.md`, this file, `TESTING.md`, `VERIFIED-FACTS.md`, and exact source.


