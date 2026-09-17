# Known issues / product gaps

Updated: 2026-09-17

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164` on NeoForge 21.1.247 and 21.1.248.

Current owner scope is recorded in `M1G-SCOPE-DECISIONS-2026-09-14.md`. Historical option lists do not override it.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.** M1F source/test/CI/package/component work is complete.

### KI-046 — M1G audible Minecraft acceptance is not recorded

**Active.** Progressive decode/render is integrated in source, but there is no recorded focused Minecraft audible PASS. CI cannot prove audibility, attenuation, channel lifecycle, starvation/refill, seek quality, loop replay, or selected volume-zero behavior.

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

### KI-021 — modern finite format surface needed narrowing

**Resolved for modern prepared/local playback.** New prepared assets are gated to MP3 or supported common WAV. Historical OGG/AIFF/AU support in inherited code does not define the modern prepared surface.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Resolved in source.** Server anchors use E1 conservative earlier seek points and JLayer decodes forward/discards pre-target PCM. Audible seek proof remains open.

### KI-033 — historical WAV acceptance broader than converter target

**Resolved for modern prepared playback.** Common WAV is narrowed to U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or the selected narrow PCM/float WAVEX subset.

### KI-047 / KI-048 / KI-049 / KI-050 — client integration, WAV decoder, MP3 decoder, renderer

**Resolved in source.** `HQFiniteMediaClient`, `ProgressiveWavDecoder`, `ProgressiveMp3Decoder`, `FinitePcmQueue`, `FinitePcmAudioStream`, and `FiniteSpeakerSound` are integrated. Focused component/runtime proof is still incomplete under KI-052/KI-055.

## Active high priority — M1G

### KI-051 — ordinary loop replay is selected but not implemented

**Owner policy selected; source work remains.**

At local physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration. A normal restart gap is acceptable.

Do not add M1G work for MP3 delay/padding trimming, loop-head prefetch solely to hide the boundary, a permanent OpenAL/Minecraft source across iterations, or SPR-specific loop continuity.

Do not reopen L1/L2/L3 or L4a/L4b unless the owner explicitly changes the requirement.

### KI-052 — focused integrated M1G control/lifecycle proof is incomplete

Missing proof includes real MP3 integration, repeated seeks, cancellation ordering, long starvation/refill, renderer adapter lifecycle, selected ordinary replay, selected fixed-range volume behavior, and actual positional output.

### KI-053 — same-anchor STATE can reset a slid encoded window without restarting the decoder

**Active source correctness issue. No fix has been applied.**

`HQFiniteMediaClient.state0()` can reset the `FiniteRangeWindow` backward to an unchanged coarse anchor while preserving an already-advanced decoder epoch. The decoder cursor can therefore become inconsistent with the active window.

Ordinary STATE must preserve a healthy decoder/window. Semantic seek is different and must still create fresh codec state even when the selected encoded anchor byte is unchanged.

### KI-056 — expected SEEK cancellation can report as fatal decoder failure

**Active source correctness issue. No fix has been applied.**

Current SEEK cancellation can wake the old decoder before its epoch identity is invalidated. The worker may schedule `decoderFailed(...)` while it still appears current, killing a valid session before replacement state is installed.

The fix must invalidate stale worker identity **before** cancellation can wake/report.

### KI-057 — STATE does not distinguish timeline snapshot from decoder-reanchor intent

**Active protocol/client coordination issue. No fix has been applied.**

The server sends time-derived anchors in STATE. Current client logic treats anchor change as restart intent, so ordinary pause/resume/volume/loop STATE can restart healthy decoding. Same-coarse-anchor semantic seek still depends on the preceding CONTROL path setting local restart state.

**Owner decision:** move to an explicit server-authoritative decoder/re-anchor revision, likely protocol v7. Ordinary STATE preserves healthy decode state; semantic seek is self-describing.

One implementation-shape choice remains: keep finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional latency hints, or remove the duplicate path and use STATE as the sole transition authority. Correctness must not depend on CONTROL/STATE ordering either way.

`FiniteDecodeAnchorSelector.Anchor` contains only encoded `offset` and anchor `seconds`; STATE already carries both. Do not invent missing frame/skip metadata as part of this fix.

### KI-058 — modern finite channel does not yet enforce the selected fixed-range attenuation contract

**Active renderer correctness issue. No fix has been applied.**

Selected M1G contract:

- fixed 32-block core listening/delivery radius;
- positional attenuation inside that radius;
- HQ `volume` changes gain/loudness, not core range;
- volume >1 must not silently enlarge attenuation distance;
- future SPR compatibility owns intentional extended range/acoustics and matching transport relevance.

The live modern finite channel should explicitly use the fixed HQ attenuation distance while gain changes independently.

### KI-059 — fixed-radius policy selected

**Product decision resolved.**

`HQFiniteMediaServer` already uses `SPEAKER_RADIUS = 32.0`. The owner selected fixed-range M1G behavior and did not request another number, so 32 blocks remains the core value unless explicitly changed.

Do not pull dynamic volume-aware listener lifecycle into M1G.

### KI-060 — renderer startup is latched before SoundManager proves start

**Active renderer robustness issue. No fix has been applied.**

`tryStartRenderer()` sets `rendererStarted=true` before `SoundManager.play(sound)` proves the source became active. There is no later path which simply clears/retries that latch when start fails.

Selected global-volume-zero behavior is **hibernation**, not silent continuous decode:

- canonical server time keeps advancing;
- local decoder/renderer are cancelled;
- encoded range requests stop;
- non-zero volume rebuilds from current authoritative state.

A player's MASTER/BLOCKS slider is client-local and must not change server transport. `canStartSilent()` may still be useful for that local case.

### KI-055 — real-MP3 and renderer-adapter deterministic coverage is incomplete

The suite does not currently run a known real MP3 fixture through the full JLayer progressive decoder across window progression/starvation, and there is no focused `FinitePcmAudioStreamTest`.

Historical Lua scripts are not substitutes for the modern prepared path. See `TESTING.md`.

### KI-061 — per-speaker staging mounts can leave unreachable files

Each `HQMediaStaging` creates a persistent save-directory mount under a fresh random `hqspeaker/staging/<uuid>` path. Whole-owner cleanup releases/unmounts ownership but does not remove arbitrary leftover staged files, so interrupted/low-level staging can accumulate unreachable files across speaker recreation/restarts.

Fix whole-staging cleanup after attached computers are unmounted. Do not clear the shared mount on one computer's ordinary detach.

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

The practical latest-review sequence is to eliminate KI-062 first, then complete KI-053/056/057 as one v7 revision cluster. KI-058/060, KI-051, KI-055, and KI-061 follow for M1G completion. KI-063 and KI-054/KI-064 may be grouped according to patch cohesion.

A broader safety-first batch which closes KI-062/063/054/064 before v7 is also defensible. Do not silently pull HLS/legacy/release cleanup into either sequence.

## Reminders

- M1E final Minecraft PASS: skipped/unrecorded.
- M1F focused Minecraft transport PASS: unrecorded.
- M1G integrated source is green, but audible runtime PASS is unrecorded.
- KI-053/KI-054/KI-056/KI-057/KI-058/KI-060/KI-061/KI-062/KI-063/KI-064 have no source fix yet.
- KI-051 and KI-059 have selected product behavior but corresponding source/test work remains.
- Current authority: `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, this file, `TESTING.md`, `VERIFIED-FACTS.md`, and exact source.
