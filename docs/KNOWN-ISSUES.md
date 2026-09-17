# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E + M1F complete at source/test/CI/package level; M1G integrated engine in progress.**

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including tests, package verification, and artifact upload.

Repository/source audits on 2026-09-14 found KI-053 through KI-061 below. A 2026-09-16 full-repository review was subsequently rechecked against exact source: confirmed cross-cutting findings are recorded below as KI-062 through KI-064, while inherited/later findings are parked in `FUTURE-CLEANUP.md`. These audits and later scope decisions changed documentation only; no source fix has been made yet.

Current owner scope is recorded in `M1G-SCOPE-DECISIONS-2026-09-14.md` and overrides older option lists in historical handoffs.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.** M1F source/test/CI/package/component work is complete.

### KI-046 — M1G audible Minecraft acceptance is not recorded

**Active.** The progressive decoder/renderer is integrated in source, but there is no recorded focused Minecraft audible PASS. CI cannot prove audibility, attenuation, channel lifecycle, starvation/refill, seek quality, or loop behavior.

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

**Resolved for modern prepared/local playback.** New prepared assets are gated to MP3 or supported common WAV. Historical OGG/AIFF/AU support may still exist in inherited code and does not define the modern prepared surface.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Resolved in source.** Server anchors use E1 conservative earlier seek points and JLayer decoding discards pre-target PCM. Audible seek proof remains part of KI-046/KI-055.

### KI-033 — historical WAV acceptance broader than converter target

**Resolved for modern prepared playback.** Common WAV is narrowed to U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or the selected narrow PCM/float WAVEX subset.

### KI-047 / KI-048 / KI-049 / KI-050 — client integration, WAV decoder, MP3 decoder, renderer

**Resolved in source.** `HQFiniteMediaClient` owns decoder epochs; `ProgressiveWavDecoder`, `ProgressiveMp3Decoder`, `FinitePcmQueue`, `FinitePcmAudioStream`, and `FiniteSpeakerSound` are integrated. Focused component/runtime proof is still incomplete under KI-052/KI-055.

## Active high priority — M1G

### KI-051 — ordinary loop replay is selected but not implemented

**Owner policy selected; source work remains.**

M1G does not require gapless or continuous-source looping. At local physical EOF, if authoritative state still says `looping=true`, the client should start the same media again with a fresh local decoder/render iteration. A normal restart gap is acceptable.

Do not add M1G work for MP3 delay/padding trimming, loop-head prefetch solely to hide the boundary, a permanent OpenAL source across iterations, or SPR-specific loop continuity.

Seek/replacement/stop must still supersede stale local replay through generation/revision checks. General severe-starvation current-time rejoin remains M1H unless a narrower correctness bug requires it.

### KI-052 — focused integrated M1G control/lifecycle proof is incomplete

Source paths exist for pause/resume, seek/replacement, volume, stop, catch-up discard, decoder cancellation, and renderer-close cancellation. Missing proof includes real MP3 integration, repeated seeks, long starvation/refill, renderer adapter lifecycle, selected ordinary replay behavior, and actual positional output.

### KI-053 — same-anchor STATE can reset a slid encoded window without restarting the decoder

**Active source correctness issue. No fix has been applied.**

`HQFiniteMediaClient.state0()` resets the `FiniteRangeWindow` whenever the server anchor is outside the client's current window. If the coarse anchor itself is unchanged, `restart` can remain false, so an already-advanced decoder keeps its cursor while the window is reset backward to old bytes.

That can cause redundant refetch/latency and can make a live decoder cursor stale if it no longer fits the reset fixed-size window.

A semantic seek is different and must still create fresh codec state even when the selected coarse anchor byte is unchanged.

Required regression coverage: slide forward, apply an ordinary same-anchor STATE without rewind/failure, then separately prove same-anchor semantic SEEK still restarts decoder state.

### KI-056 — SEEK cancellation can report expected cancellation as a fatal decoder failure

**Active source correctness issue found by the deeper 2026-09-14 client audit. No fix has been applied.**

`CONTROL SEEK` currently calls `cancelDecodeEpoch()` before the old worker identity is invalidated. The expected cancellation can therefore wake an old decoder which schedules `decoderFailed(...)` while its epoch token still appears current, killing a valid session before replacement authoritative state arrives.

The selected fix direction is the explicit decoder/re-anchor revision. Any local cancellation must invalidate the old worker identity **before** cancellation can wake that worker, and deterministic cancellation-order coverage is required.

### KI-057 — STATE does not distinguish a timeline snapshot from decoder re-anchor intent

**Active protocol/client coordination issue. No fix has been applied.**

Every authoritative STATE carries a time-derived codec anchor. Current client logic treats `anchorChanged` as a reason to reset/restart the decode epoch. The server also sends STATE after ordinary pause/resume/volume/loop controls. For WAV the exact frame anchor changes with time, so a normal state update can tear down a healthy decoder/renderer. For MP3 this happens when the coarse seek point advances.

The opposite failure also exists: semantic seek correctness for a same coarse anchor currently depends on the preceding `CONTROL SEEK` setting `restartRequested`. If that best-effort control projection fails while the authoritative STATE still arrives, STATE alone does not identify the operation as requiring fresh codec state.

**Owner decision:** move to an explicit server-authoritative decoder/re-anchor revision (protocol revision) so ordinary state snapshots preserve a healthy decoder and semantic seek is self-describing.

Before coding, compare whether finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets still buy enough to justify their duplicate ordering path. A v7 STATE-only authority for these transitions may be simpler overall; retaining redundant controls is not required for compatibility with the unreleased v6 internal protocol.

### KI-058 — modern-finite channel attenuation does not yet implement the selected fixed-range contract

**Active renderer correctness issue. No fix has been applied.**

The selected HQ finite contract deliberately differs from native CC:T high-volume range semantics:

- one fixed core listening/delivery radius;
- normal positional attenuation inside that radius;
- HQ `volume` changes gain/loudness, not the core HQ radius;
- future Sound Physics Remastered compatibility owns deliberate extended-range behavior and the matching transport relevance.

Minecraft/CC:T normally scale linear attenuation distance when volume is above 1. Copying CC:T's `max(volume, 1) * attenuationDistance` workaround would therefore violate the selected HQ contract.

The implementation should instead explicitly apply the fixed HQ attenuation distance to the live `Channel` and update gain on volume changes without enlarging that distance. `HQSoundChannelControl` already provides access to the underlying channel.

Runtime acceptance must check that distance attenuation works, volume changes loudness, and volume above 1 does not silently enlarge the M1G core range.

### KI-059 — fixed-radius policy selected; numeric value remains the existing 32 blocks unless explicitly changed

**Product decision resolved; source currently already uses the selected policy shape.**

`HQFiniteMediaServer` currently uses `SPEAKER_RADIUS = 32.0` for BEGIN/STATE/range relevance. Earlier audits treated this as conflicting with vanilla CC:T volume-3 reach. The owner has now deliberately chosen a different HQ finite policy: M1G keeps a fixed core radius and does not make transport relevance follow volume.

The owner has not separately requested a different numeric radius, so 32 blocks remains the conservative existing value for M1G unless changed explicitly.

Do not pull general volume-aware late-entry/leave lifecycle into M1G. Future SPR compatibility may intentionally widen/alter both acoustic distance and server delivery relevance; keep the core range policy localized enough that compatibility work can replace it later without redesigning the finite protocol.

### KI-060 — renderer startup is latched before SoundManager proves the sound actually started

**Active renderer robustness issue. No fix has been applied.**

`tryStartRenderer()` sets `rendererStarted = true` immediately before calling `SoundManager.play(sound)`. There is no later path which clears/retries that latch if Minecraft never allocates/starts the sound.

The selected global-volume-zero behavior is targeted hibernation, not silent continuous decode:

- canonical server time keeps advancing;
- local decoder/renderer are cancelled while HQ volume is exactly zero;
- no encoded media ranges are requested while globally muted;
- non-zero volume recreates local decode/render state from current authoritative state.

A player's own Minecraft MASTER/BLOCKS slider is client-local and must not change server transport. `SoundInstance.canStartSilent()` may still be appropriate so a locally-muted source is allowed to exist and later become audible, but the one-way `rendererStarted` latch must be hardened regardless.

### KI-054 — shutdown cleanup can lose deletion retry state or leave the media-store root lock held

**Active shutdown correctness/hardening issue. No fix has been applied.**

Normal final release keeps bookkeeping alive if file deletion fails. `MediaAssetStore.close()` instead clears completed entries before deletion attempts and marks close cleanup complete even when a deletion throws, so a later `close()` cannot retry those completed files.

There are two registry/lifetime failure paths:

1. if `store.close()` itself throws, `ServerMediaAssets.closeServer()` skips `SERVERS.remove(server, assets)`;
2. more seriously, `ServerMediaAssets.closeServer()` calls `rangeReads.close()` **before** `store.close()`. `FiniteRangeReadService.close()` may throw if workers do not stop within its shutdown wait or if player accounting remains non-empty. In that case `store.close()` is never called at all, so the `MediaAssetStore` root file lock can remain held and `SERVERS.remove(...)` is also skipped.

In a long-lived JVM/integrated-server restart, a later store on the same root can then fail with "media asset store directory is already in use" until the process exits. The current `ServerStoppedEvent` handler catches/logs the close failure but performs no recovery.

Next-start orphan pruning only helps once the old lock is gone; it does not cure a same-JVM still-held root lock. This needs explicit failure-injection coverage and a shutdown design which releases the store/registry safely even when range-service shutdown reports failure.

### KI-055 — current test/script surface mixes modern M1G acceptance with historical legacy tests

**Active testing/documentation gap.**

The Java suite strongly covers transport, common-WAV analysis/conversion, starvation-aware encoded input, PCM queue behavior, and server state. It does not currently run a known real MP3 fixture through the full JLayer progressive decoder across window progression/starvation, and there is no focused `FinitePcmAudioStreamTest` in the current client test tree.

Several Lua scripts are historical/legacy. In particular `scripts/m1d_media_analysis_test.lua` expects the old broad M1D prepared format surface and must not be used as a current M1G pass/fail gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern prepared path.

`TESTING.md` is the authoritative current matrix until those scripts are replaced/reworked.

### KI-061 — per-speaker staging mounts can leave unreachable files on disk

**Active storage-lifecycle issue found by the 2026-09-14 storage sweep. No fix has been applied.**

Each `HQMediaStaging` instance creates a persistent ComputerCraft save-directory mount under a fresh random path such as `hqspeaker/staging/<uuid>`. `cleanup()` detaches computers and releases prepared-asset ownership, but it does not clear arbitrary files still present in that writable staging mount.

CC:T 1.120.0 implements `createSaveDirMount()` as a normal persistent `WritableFileMount` rooted at the requested server-storage subdirectory. Unmounting or dropping the Java mount object does not delete that directory. A program using the low-level staging API, a failed/interrupted copy, or any other leftover staged file can therefore become unreachable when the speaker/composite is destroyed and later recreated with a new random staging ID. Repeated speaker lifecycle churn can accumulate those files across restarts.

The high-level `hq.playFile()` path normally deletes/consumes its temporary staging file, so this is not ordinary successful-playback corruption. It is still a disk-leak/lifecycle gap.

A likely fix is to clear the mount contents when the whole `HQMediaStaging` object is being cleaned up, after all attached computers are unmounted. Do not clear the shared mount on one computer's ordinary `detach()`. Add failure handling and deterministic coverage for leftover-file cleanup.

## Active cross-cutting correctness / hardening

### KI-062 — a blocking stream URL lookup can hold a monitor required by the server tick

**Confirmed by the 2026-09-16 full-repository review and exact-source recheck. No fix has been applied.**

`HQSpeakerCompositePeripheral.callMethod(...)` is synchronized. Dynamic `speakStream` / `speakHLS` / `speakTS` dispatch through that method into the inherited stream-start path. `HQSpeakerPeripheral.validateStreamUrl(...)` performs synchronous `InetAddress.getAllByName(host)` during that call.

The server main thread calls `HQSpeakerCompositePeripheral.tickAll()` every server tick, and `tickOwnership()` is synchronized on the same composite. A slow DNS resolution on a ComputerCraft computer thread can therefore hold the monitor while the server tick waits for it, stalling the whole server until the lookup returns.

Do not broaden this claim to every media operation: the composite's annotated `audioPrepareStaged(...)` method is not itself synchronized on this monitor. The confirmed problem is the dynamic synchronized call path, especially the legacy stream URL validation.

Fix direction must preserve ownership ordering without allowing a main-thread tick to wait behind blocking I/O. Moving/blocking DNS outside the shared monitor and narrowing or eliminating the tick monitor dependency are the primary design targets.

### KI-063 — replacement attempts can destroy a valid current source before the new source is accepted

**Confirmed. No fix has been applied.**

`HQSpeakerCompositePeripheral.startRaw(...)` calls `beginReplacingHQ(Owner.RAW)` before it computes whether the new RAW chunk can be accepted. If capacity is full, it can then return `false` after the previous HQ source has already been stopped.

`audioPlayPrepared(...)` similarly calls `beginReplacingHQ(Owner.STAGED_FINITE)` before `finite.playPrepared(...)`. Asset lookup/analyzed-state/media-service failures can therefore throw after the previous HQ source has been destroyed.

A retryable `false` or failed replacement should not silently mean "the old valid playback was also stopped" unless that destructive behavior is explicitly chosen and documented. Validation/admission should happen before ownership transfer where practical.

### KI-064 — `MediaAssetStore` import has an unbounded zero-read spin and no non-atomic move fallback

**Confirmed storage hardening gap. No fix has been applied.**

`MediaAssetStore.writeExact(...)` loops indefinitely if its `ReadableByteChannel` repeatedly returns zero bytes: it calls `Thread.onSpinWait()` and immediately retries with no bounded zero-read counter. Other repository readers already use bounded zero-read guards.

The import commit step also calls `Files.move(part, media, ATOMIC_MOVE)` without falling back when the filesystem does not support atomic moves. A valid import can therefore fail on filesystems where atomic move is unavailable even though a same-filesystem non-atomic rename would otherwise succeed.

Both changes are local to storage/import behavior and have existing `MediaAssetStoreTest` coverage to extend. They are not part of the decoder protocol design.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter and moving-source lifecycle is not final

M1H owns proactive out-of-range cleanup, late-entry discovery, return/rejoin, dimension/world/resource-reload recovery, robust general underrun rejoin, and final VS2 movement lifecycle.

The fixed M1G radius decision intentionally does **not** pull general dynamic listener lifecycle forward.

The 2026-09-16 source review also confirmed that modern `FiniteSpeakerSound.updatePosition(...)` currently has no M1G client call site after renderer creation. BEGIN carries the initial world/block position, while modern STATE does not carry x/y/z. A speaker moving on a VS2 ship can therefore keep its modern finite sound at the original client position until that lifecycle is implemented. Do not "fix" this by assuming STATE already contains world position; it does not.

## Gated/later work

- Sound Physics Remastered compatibility owns future acoustic/range extension and matching transport relevance; do not prebuild that integration in M1G.
- Gapless MP3/LAME/Xing delay/padding handling is not an M1G requirement.
- Continuous-source loop engineering is not an M1G requirement.
- KI-034: native FLAC remains gated M1I work.
- KI-018: inherited expected-member multispeaker barrier can deadlock partial listeners — M1J.
- KI-011 / KI-012 / KI-023: inherited finite engine/APIs remain legacy — M1L.
- KI-013 through KI-019: inherited live/HLS/TS/OpenAL lifecycle/gain issues — M3/later cleanup. The 2026-09-16 review additionally confirmed the refreshed-live-playlist `currentSegmentIndex` bug; see `FUTURE-CLEANUP.md`.
- KI-022: separate `hqspeaker:hq_speaker` registration remains a release-cleanup decision.
- KI-025: top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0; resolve provenance before public release and do not silently relicense.

## Reminders

- M1E final Minecraft PASS: skipped/unrecorded.
- M1F focused Minecraft transport PASS: unrecorded.
- M1G integrated source is green, but audible runtime PASS is unrecorded.
- KI-053/KI-054/KI-056/KI-057/KI-058/KI-060/KI-061/KI-062/KI-063/KI-064 are source findings with no implementation fix yet.
- KI-051 and KI-059 now have owner-selected product behavior but still need any corresponding source/test work described above.
- Historical docs/scripts may preserve earlier contracts; `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, current testing/facts documents, and exact source override them.
