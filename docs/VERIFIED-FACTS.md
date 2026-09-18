# Verified facts

Updated: 2026-09-19

Facts only. Recommendations and unresolved choices belong elsewhere.

## Repository / platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current implementation branch: `codex/m1g-progressive-finite-decode`.

Important source checkpoints:

- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- M1G preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`;
- final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`.

### FACT-PLATFORM-001

Target stack: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.247 baseline and 21.1.248 compatibility.

## CI / package facts

### FACT-CI-001

M1E CI `34757923455`, M1F CI `34763362365`, and final M1G CI `35297026277` passed both target NeoForge versions. Final M1G CI included build, deterministic tests, packaged-mod verification, and artifact upload.

CI is not Minecraft runtime proof.

### FACT-CI-002

Final M1G artifacts for source checkpoint `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`:

- 21.1.247 artifact `10527498657`, SHA-256 `3b873edd94a924cf3922a75cdd059c2b3963500787bd479ec8b386600ef055b1`;
- 21.1.248 artifact `10528645404`, SHA-256 `3b9304a509d37bf2ef1c0797d4449fa2cb6c8cc705678e63a68ed8c2ef72f8b1`.

### FACT-CI-003

The current build workflow triggers on unfiltered `push` and `pull_request` events and builds both configured NeoForge targets. It has no documentation-path exclusion or concurrency cancellation.

## Packaging facts

### FACT-BUILD-001

Packaged dependencies include JLayer `1.0.1.4`, mp3spi `1.9.5.4`, and Tritonus Share `0.3.7.4`. Modern progressive MP3 decoding uses JLayer.

The inherited complete-file/legacy surfaces still justify keeping the SPI dependencies for now; they are not evidence that the modern prepared engine uses mp3spi.

## CC:T contract facts

### FACT-CCT-001

The exposed peripheral type is `speaker`; standard `playNote`, `playSound`, `playAudio`, and `stop` delegate to CC:T's real speaker behavior. Native `speaker_audio_empty` remains CC:T-owned, while HQ RAW uses `hqspeaker_audio_empty`.

### FACT-CCT-002

The exact target CC:T 1.120.0 client speaker implementation explicitly updates live channel linear attenuation when speaker volume changes because Minecraft's normal volume refresh does not update attenuation distance. Its calculation is `Math.max(volume, 1) * sound.getSound().getAttenuationDistance()`.

The owner-selected modern HQ finite contract intentionally differs: M1G keeps a fixed core radius and volume changes gain rather than range.

### FACT-CCT-003

Minecraft 1.21.1 exposes `SoundInstance.canStartSilent()` for long-lived sounds which should be allowed to start while currently silent. Final M1G `FiniteSpeakerSound` overrides it.

## Asset/import facts

### FACT-ASSET-001

`MediaAssetStore` owns immutable UUID-addressed encoded server files with quotas, reference counting, and seekable reads. CC files use temporary writable staging only to import MediaAssets.

### FACT-ASSET-002

Active prepared/playback/range release paths preserve retry ownership when final release fails. Server shutdown attempts to stop/drain range IO before the store closes.

### FACT-ASSET-003

`MediaAssetStore.close()` clears completed-entry bookkeeping before shutdown deletion attempts. If a completed-file deletion fails, a later `close()` has no retained completed-entry list to retry.

`ServerMediaAssets.closeServer()` removes its static server entry only after close succeeds. It also calls `FiniteRangeReadService.close()` **before** `MediaAssetStore.close()`. If range close throws, store close and registry removal are never reached, so the store root file lock and stopped-server/assets entry can remain alive in the JVM.

The current server-stop hook catches/logs the failure but does not schedule recovery. This is KI-054 and remains unfixed.

### FACT-ASSET-004

Each `HQMediaStaging` instance creates a persistent ComputerCraft save-directory mount under a fresh random `hqspeaker/staging/<uuid>` path. Final M1G whole-owner cleanup unmounts/releases ownership and then deletes all remaining top-level staging entries; the writable mount's delete operation removes nested directories recursively. One-computer detach does not clear the shared mount. KI-061 is resolved in source.

### FACT-ASSET-005

`MediaAssetStore.writeExact()` immediately retries repeated zero-byte reads with `Thread.onSpinWait()` and no bounded no-progress limit. `MediaAssetStore.importAsset()` also uses `Files.move(..., ATOMIC_MOVE)` without an unsupported-atomic-move fallback. This is KI-064 and remains unfixed.

## M1E facts

### FACT-M1E-001

The server owns finite generation/state/time/control/natural EOF. Client READY requests state; client ERROR is diagnostic. Final focused Minecraft M1E acceptance was skipped/unrecorded.

## M1F facts

### FACT-M1F-001

Modern finite transport uses bounded range request/data rather than whole-file CHUNK/END transfer. Current tuning: 128 KiB max range, 512 KiB client encoded window, four outstanding requests/player, 512 KiB outstanding bytes/player, two range IO workers, queue size 64.

### FACT-M1F-002

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE; it supports arbitrary re-anchor and forward sliding under a fixed memory cap.

### FACT-M1F-003

Modern prepared transport does not create a complete client song `.part/.media` file and does not expose `audioPlayStaged()`.

## M1G architecture facts

### FACT-M1G-ARCH-001

Locked media/renderer choices are A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll.

Output is mono signed 16-bit PCM at source sample rate; one physical speaker remains one mono positional source.

### FACT-M1G-ARCH-002

Implemented final M1G semantics are:

- explicit server-authoritative decoder/re-anchor revision;
- fixed 32-block modern finite core delivery/listening/attenuation radius;
- HQ volume changes gain rather than core radius;
- global-volume-zero local transport/decode/render hibernation while canonical server time continues;
- ordinary non-gapless replay after local physical EOF while authoritative looping remains enabled;
- future SPR compatibility owns intentional extended range/acoustics and matching transport relevance.

## M1G integrated source facts

### FACT-M1G-001

Modern finite protocol version is **7**. STATE carries a positive `decodeRevision`.

Semantic seek increments the server revision. Ordinary pause/resume/volume/loop snapshots preserve it.

### FACT-M1G-002

Modern prepared/local media is narrowed to MP3 or supported common WAV: U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or selected narrow PCM/float WAVEX.

### FACT-M1G-003

STATE anchor selection provides exact frame-aligned WAV anchors and conservative E1 MP3 pre-roll anchors. `FiniteDecodeAnchorSelector.Anchor` contains exactly encoded byte `offset` and anchor time `seconds`.

### FACT-M1G-004

`FiniteEncodedInputStream` is a decoder-worker-only view over the M1F range window. NEED_DATA waits/refills; true asset EOF alone returns normal EOF; cancellation/stale state aborts the epoch.

### FACT-M1G-005

`FinitePcmQueue` is fixed-capacity mono-S16 storage with producer backpressure and nonblocking renderer states.

### FACT-M1G-006

`ProgressiveWavDecoder` progressively converts supported WAV to mono S16 without whole-track PCM retention.

### FACT-M1G-007

`ProgressiveMp3Decoder` uses packaged JLayer frame-by-frame, validates analyzed rate/channel facts, decodes from earlier E1 anchors, and discards pre-target PCM.

A real synthetic mono 44.1 kHz MP3 fixture is decoded in tests across initial encoded starvation, repeated bounded range refill, multiple encoded-window slides, and pre-target discard.

### FACT-M1G-008

`FiniteDecodeCoordinator` separates server decoder revision from local worker identity. Old local identity is incremented before cancellation. Same-revision ordinary STATE keeps a healthy decoder; higher revision STATE restarts; stale lower revision STATE is ignored.

### FACT-M1G-009

Protocol v7 uses STATE as the sole authority for pause/resume/seek/volume/loop. The server no longer projects those nonterminal CONTROL actions. Explicit STOP remains distinct because stop removes the server session.

### FACT-M1G-010

`FinitePcmReadAdapter` is a pure renderer-read policy used by `FinitePcmAudioStream`. It maps live empty PCM to bounded silence and keeps physical EOF distinct from cancellation. The pure adapter is unit tested because the ordinary JUnit source set does not expose Minecraft's client-only `AudioStream`; the Minecraft adapter itself compiles/packages on both targets.

### FACT-M1G-011

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation, and one source per physical speaker. It overrides `canStartSilent()`.

The active channel is explicitly assigned a fixed 32-block attenuation distance independent of HQ volume.

### FACT-M1G-012

Global HQ volume zero keeps the canonical server playback clock alive while clients cancel decoder/renderer state and stop range demand. The server rejects new range work and drops completed range delivery while global volume is zero. A later non-zero STATE rebuilds from current authoritative position/anchor.

### FACT-M1G-013

At physical local EOF, authoritative `looping=true` causes a fresh local decode/render iteration. The client projects current server loop position modulo duration for catch-up. M1G does not claim sample-gapless MP3 or permanent-source loop continuity.

### FACT-M1G-014

Whole-owner `HQMediaStaging.cleanup()` deletes remaining top-level entries from the persistent staging mount after attached computers are detached and ownership references released.

### FACT-M1G-015

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry x/y/z. The current modern client still does not call `FiniteSpeakerSound.updatePosition(...)` after renderer creation, so moving-source/VS2 lifecycle remains M1H.

## Rechecked client/protocol facts

### FACT-AUDIT-001

KI-053 is resolved: ordinary same-revision STATE no longer resets a healthy slid range window merely because the time-derived codec anchor moved or fell outside the current window.

### FACT-AUDIT-002

KI-055 deterministic coverage is resolved: real JLayer MP3 progressive decode and pure renderer-read policy tests exist.

### FACT-AUDIT-003

`scripts/m1d_media_analysis_test.lua` remains historical and is not a valid current M1G prepared-format gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking APIs.

### FACT-AUDIT-004

After renderer start, an empty live PCM queue becomes short local silence until PCM returns. General long-underrun current-time rejoin remains M1H.

### FACT-AUDIT-005

KI-056 is resolved: the local worker token is invalidated before decoder/input/PCM cancellation, so expected old-worker cancellation cannot report as current.

### FACT-AUDIT-006

KI-057 is resolved: STATE carries explicit decoder revision and no longer overloads anchor movement as restart intent.

### FACT-AUDIT-007

Same-coarse-anchor semantic seek does not depend on CONTROL SEEK ordering; seek increments `decodeRevision` and STATE is self-describing.

### FACT-AUDIT-008

KI-058 is resolved: the modern finite live channel installs the selected fixed 32-block attenuation distance while HQ volume updates gain.

### FACT-AUDIT-009

`HQFiniteMediaServer` uses a fixed 32-block relevance radius for modern finite BEGIN/STATE/range serving.

### FACT-AUDIT-010

KI-060 source hardening is implemented: renderer start is latched after `SoundManager.play(...)` returns, activation/EOF are observed, failed/lost activation can request authoritative rejoin, global zero volume hibernates resources, and client-local silent start is allowed.

### FACT-AUDIT-011

NeoForge 1.21.1 payload handlers execute on the main thread by default unless registration opts into network-thread execution. Current modern packet registration does not opt into network-thread execution.

## Rechecked full-repository facts

### FACT-AUDIT-012 — monitor/DNS coupling

`HQSpeakerCompositePeripheral.callMethod(...)` and `tickOwnership()` are synchronized on the same composite. Dynamic STREAM calls can reach synchronous `InetAddress.getAllByName(host)` while `callMethod(...)` holds that monitor.

Composite `cleanup()` is also synchronized. Provider `forget`, `forgetLevel`, and `clearAll` call cleanup during removal, Level unload, and server stop.

A ComputerCraft thread blocked in DNS can therefore make server tick ownership work or lifecycle cleanup wait on that composite monitor. `audioPrepareStaged(...)` is not itself synchronized on this monitor. This is KI-062.

### FACT-AUDIT-013 — replacement-before-admission

RAW replacement transfers/stops HQ ownership before capacity acceptance is final. Prepared replacement transfers/stops ownership before `finite.playPrepared(...)` has completed all rejection/failure paths. A rejected/failed replacement can therefore destroy current valid playback. This is KI-063.

### FACT-AUDIT-014 — inherited HLS progression

Inherited live HLS keeps one monotonically increasing `currentSegmentIndex` across refreshed playlists whose segment arrays are fresh zero-based lists. After the initial window, a refreshed list may have no index at or above `currentSegmentIndex`, leaving the stream alive but producing no new segments.

### FACT-AUDIT-015 — legacy capability reporting

Legacy Lua-visible capability lists advertise formats broader than the modern prepared engine. For example, `speakSupportedFiles()` includes `mp2`, `mp4`, `m4a`, and `aac`, while modern prepared support is MP3 + supported common WAV.

### FACT-AUDIT-016 — legacy multispeaker note/sound helpers

Legacy `playNoteAll(...)` synthesizes a sine and does not use the requested instrument. `playSoundAll(...)` delegates to that sine path and does not use the requested sound name.

### FACT-AUDIT-017 — provider cache intent

`HQSpeakerPeripheralProvider` uses a Level-keyed `WeakHashMap`, but cached composite values themselves reference their Level. The source comment explicitly states that the weak key is only a fallback and deterministic lifecycle hooks must evict the cache. There is no `HQSpeakerPeripheral -> composite` back-reference.

### FACT-AUDIT-018 — legacy VS2 client movement path

The inherited client has a `tickPosition(...)` path which resolves a speaker's ship from packet block coordinates, transforms block-local position to world coordinates, and calls the sound object's `updatePosition(...)` each tick.

Because modern BEGIN already carries block coordinates, a future modern VS2 movement implementation could mirror this client-side pattern without necessarily adding new wire position fields. Whether M1H should do that or add authoritative position updates remains a design choice, not a fact.

### FACT-AUDIT-019 — retracted audit claims

Exact-source rechecking rejected several first-draft audit claims:

- there are no richer MP3 anchor frame/skip fields beyond `(offset, seconds)`;
- STATE does not carry live x/y/z;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- `HQFiniteMediaServer.tick()` does not itself perform the player/fanout work originally attributed to it;
- inherited HTTP streaming paths do close their streams;
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## Current selected direction / remaining choices

### FACT-M1G-CLOSE-001

M1G is complete at source/test/CI/package/component level at `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`.

A separate focused audible/core Minecraft acceptance PASS was recorded on 2026-09-19 using NeoForge 21.1.247 integrated singleplayer, resolving KI-046. That runtime evidence is separate from CI and does not claim manual runtime coverage of NeoForge 21.1.248.

### FACT-POST-M1G-001

KI-062, KI-063, KI-054, and KI-064 remain open cross-cutting correctness/hardening after M1G.

### FACT-M1H-NEXT-001

Modern moving-source/VS2 handling remains M1H. Two source-compatible approaches remain plausible: mirror the existing client-side block-coordinate transform path, or add explicit authoritative position updates. No choice has been made.

## Later milestone facts

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H. Native FLAC remains gated M1I. Inherited legacy finite/live/multispeaker code remains for later migration/removal. SPR acoustic/range compatibility remains later M2 work.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. The provenance mismatch remains unresolved.
