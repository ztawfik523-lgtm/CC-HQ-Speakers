# Known issues / product gaps

Updated: 2026-09-19

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277` on NeoForge 21.1.247 and 21.1.248. Both targets passed build, tests, packaged-mod verification, and artifact upload.

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

## Active cross-cutting correctness / hardening

### KI-062 — blocking stream URL lookup can hold a monitor required by server tick and cleanup

**Confirmed by exact-source recheck. No fix has been applied.**

`HQSpeakerCompositePeripheral.callMethod(...)` is synchronized. Dynamic `speakStream` / `speakHLS` / `speakTS` dispatch can reach synchronous `InetAddress.getAllByName(host)` while that monitor is held.

The server main thread calls synchronized `tickOwnership()` on the same composite every tick. Composite `cleanup()` is also synchronized and is reached by provider `forget`, `forgetLevel`, and `clearAll` during block removal, Level unload, and server stop.

A DNS-parked ComputerCraft thread can therefore make server tick ownership work or lifecycle cleanup wait on that composite monitor.

`audioPrepareStaged(...)` is **not** synchronized on this monitor; do not broaden the claim to all media operations.

Fix direction: preserve ownership ordering while moving/blocking DNS/I/O outside the shared monitor or otherwise eliminating server-thread dependence on it. Do not expand this into M3 stream redevelopment.

### KI-063 — replacement can destroy valid current playback before admission succeeds

**Confirmed. No fix has been applied.**

RAW replacement calls ownership transfer/stop before final capacity acceptance. Prepared replacement similarly transfers/stops current ownership before `finite.playPrepared(...)` has completed all rejection/failure paths.

A retryable `false` or failed start should leave the current valid source alive unless destructive replacement is explicitly intended.

### KI-054 — shutdown cleanup can lose retry state or leave the media-store root lock held

**Active shutdown correctness/hardening issue. No fix has been applied.**

Two failure shapes exist:

1. `MediaAssetStore.close()` clears completed-entry bookkeeping before deletion attempts, so failed deletion cannot be retried by another `close()`;
2. `ServerMediaAssets.closeServer()` calls `FiniteRangeReadService.close()` before `MediaAssetStore.close()`. If range close throws, store close and registry removal are never reached, so the root file lock and stopped-server/assets entry can remain alive in the JVM.

A later integrated-server/world start on the same root can then fail with “media asset store directory is already in use” while the old lock is still held. Next-start orphan pruning does not solve a still-held same-JVM lock.

### KI-064 — MediaAsset import has an unbounded zero-read spin and no non-atomic move fallback

**Confirmed storage hardening gap. No fix has been applied.**

`MediaAssetStore.writeExact(...)` immediately retries zero-byte reads forever with `Thread.onSpinWait()` and no bounded no-progress policy.

Import also uses `Files.move(..., ATOMIC_MOVE)` without falling back when the filesystem does not support atomic moves.

Both are local storage/import fixes with deterministic test opportunities.

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

M1G is closed. Next engineering priority is no longer the M1G decoder cluster.

The still-open cross-cutting correctness/hardening issues are KI-062, KI-063, KI-054, and KI-064. M1H owns listener/rejoin/movement lifecycle. Keep those issues visible, but do not retroactively expand M1G to claim they were part of its core progressive finite-engine closeout.

## Reminders

- M1E final Minecraft PASS: skipped/unrecorded.
- M1F focused Minecraft transport PASS: unrecorded.
- M1G source/test/CI/package/component: PASS at `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`.
- M1G focused audible/core Minecraft PASS: recorded 2026-09-19 on NeoForge 21.1.247; KI-046 resolved.
- KI-051/053/055/056/057/058/060/061 are resolved at source/component level.
- KI-054/062/063/064 remain open post-M1G.
- Current authority: `CURRENT-STATE.md`, `HANDOFF-2026-09-18-M1G-COMPLETE.md`, this file, `TESTING.md`, `VERIFIED-FACTS.md`, and exact source.


