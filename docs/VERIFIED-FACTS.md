# Verified facts

Updated: 2026-09-17

Facts only. Recommendations and unresolved choices belong elsewhere.

## Repository / platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current implementation branch: `codex/m1g-progressive-finite-decode`.

Important source checkpoints:

- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- M1G preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`;
- current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

### FACT-PLATFORM-001

Target stack: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.247 baseline and 21.1.248 compatibility.

## CI / package facts

### FACT-CI-001

M1E CI `34757923455`, M1F CI `34763362365`, and M1G integrated-source CI `34778546164` passed both target NeoForge versions. M1G CI included build, tests, packaged-mod verification, and artifact upload.

### FACT-CI-002

M1G artifacts for source checkpoint `957832348eaa6e497282d923f2312c9c7d7c550f`:

- 21.1.247 artifact `10324148909`, ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482`, ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both targets.

CI is not Minecraft runtime proof.

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

Minecraft 1.21.1 exposes `SoundInstance.canStartSilent()` for long-lived sounds which should be allowed to start while currently silent. Current `FiniteSpeakerSound` does not override it.

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

Each `HQMediaStaging` instance creates a persistent ComputerCraft save-directory mount under a fresh random `hqspeaker/staging/<uuid>` path. Whole-owner cleanup unmounts/releases ownership but does not clear arbitrary leftover staged files. Unmounting does not delete the persistent directory. This is KI-061 and remains unfixed.

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

Owner-selected later M1G target semantics are:

- explicit server-authoritative decoder/re-anchor revision;
- fixed 32-block modern finite core listening/delivery radius;
- HQ volume changes gain rather than core radius;
- global-volume-zero local transport/render hibernation while canonical server time continues;
- ordinary non-gapless replay after local physical EOF while authoritative looping remains enabled;
- future SPR compatibility owns intentional extended range/acoustics and matching transport relevance.

These target semantics are not all implemented yet.

## M1G integrated source facts

### FACT-M1G-001

Modern finite protocol version is currently **6**. BEGIN carries an MP3/common-WAV `FiniteDecodeDescriptor`.

### FACT-M1G-002

Modern prepared/local media is narrowed to MP3 or supported common WAV: U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or selected narrow PCM/float WAVEX.

### FACT-M1G-003

STATE anchor selection provides exact frame-aligned WAV anchors and conservative E1 MP3 pre-roll anchors. `FiniteDecodeAnchorSelector.Anchor` contains exactly two fields: encoded byte `offset` and anchor time `seconds`. STATE carries both. It does not contain frame-index, skip-frame, or skip-sample fields.

### FACT-M1G-004

`FiniteEncodedInputStream` is a decoder-worker-only view over the M1F range window. NEED_DATA waits/refills; true asset EOF alone returns normal EOF; cancellation/stale state aborts the epoch.

### FACT-M1G-005

`FinitePcmQueue` is fixed-capacity mono-S16 storage with producer backpressure and nonblocking renderer states.

### FACT-M1G-006

`ProgressiveWavDecoder` progressively converts supported WAV to mono S16 without whole-track PCM retention.

### FACT-M1G-007

`ProgressiveMp3Decoder` uses packaged JLayer frame-by-frame, validates analyzed rate/channel facts, decodes from earlier E1 anchors, and discards pre-target PCM.

### FACT-M1G-008

`HQFiniteMediaClient` owns local decode epochs; seek/replacement/stop cancel old encoded waits, decode work, PCM, and renderer state. Range arrivals wake the active encoded input.

### FACT-M1G-009

`FinitePcmAudioStream` performs no network/disk/codec work and represents temporary PCM starvation with short bounded silence instead of terminal EOF.

### FACT-M1G-010

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation, and one source per physical speaker. It exposes `updatePosition(...)`.

Modern BEGIN carries initial world position and block coordinates. Modern STATE does **not** carry x/y/z. The current modern client does not call `FiniteSpeakerSound.updatePosition(...)` after renderer creation, so moving-source/VS2 lifecycle remains incomplete.

### FACT-M1G-011

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared finite engine.

## Rechecked client/protocol facts

### FACT-AUDIT-001

`HQFiniteMediaClient.state0()` can reset the encoded window when the server anchor is outside the current slid window even when the coarse anchor is unchanged; the decoder epoch may be preserved. This is KI-053.

### FACT-AUDIT-002

`ProgressiveMp3DecoderTest` does not decode a real MP3 fixture through JLayer. The current client test tree has no focused `FinitePcmAudioStreamTest`. This is part of KI-055.

### FACT-AUDIT-003

`scripts/m1d_media_analysis_test.lua` reflects the historical broad M1D format surface and is not a valid current M1G modern-prepared gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking APIs.

### FACT-AUDIT-004

After renderer start, an empty live PCM queue becomes short local silence until PCM returns. Current M1G does not implement general long-underrun current-time rejoin; that remains M1H.

### FACT-AUDIT-005

`CONTROL SEEK` currently cancels the decode epoch before a new worker identity is installed. Expected cancellation can therefore race into `decoderFailed()` while the old epoch still appears current. This is KI-056.

### FACT-AUDIT-006

The server sends STATE after successful pause/resume/seek/volume/loop transitions and recomputes the codec anchor from canonical position. The client currently treats anchor change as restart intent. This is part of KI-057.

### FACT-AUDIT-007

Same-coarse-anchor semantic seek restart currently depends on the preceding SEEK control setting client-local restart state. STATE has no explicit decoder/reanchor revision. This is the other half of KI-057.

### FACT-AUDIT-008

Modern finite live volume updates mutate sound volume and refresh BLOCKS category volume, but they do not explicitly install the owner-selected fixed 32-block attenuation distance on the active channel. This is KI-058.

### FACT-AUDIT-009

`HQFiniteMediaServer` uses a fixed 32-block relevance radius for modern finite BEGIN/STATE/range serving. The owner selected that fixed-radius shape for M1G rather than volume-dependent relevance.

### FACT-AUDIT-010

`HQFiniteMediaClient.tryStartRenderer()` sets `rendererStarted=true` before `SoundManager.play(sound)` and has no simple retry path merely because the sound failed to become active. This is KI-060.

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

### FACT-M1G-NEXT-001

Loop behavior is selected: ordinary local replay after physical EOF while authoritative state still says `looping=true`, with a normal restart gap acceptable. It is not implemented yet.

### FACT-M1G-NEXT-002

Decoder/re-anchor behavior is selected: introduce an explicit server-authoritative decoder/re-anchor revision so semantic seek is self-describing and ordinary snapshots do not restart healthy decoders.

One implementation-shape choice remains before coding v7: retain finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional low-latency hints, or remove that duplicate path and let STATE alone carry those transitions.

### FACT-M1G-NEXT-003

Range and volume-zero behavior are selected: M1G keeps the existing fixed 32-block core radius; HQ volume changes gain rather than radius; global HQ volume zero hibernates local decode/render/range requests while canonical server time continues.

### FACT-M1H-NEXT-001

Modern moving-source/VS2 handling remains M1H. Two source-compatible approaches remain plausible: mirror the existing client-side block-coordinate transform path, or add explicit authoritative position updates. No choice has been made.

## Later milestone facts

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H. Native FLAC remains gated M1I. Inherited legacy finite/live/multispeaker code remains for later migration/removal. SPR acoustic/range compatibility remains later M2 work.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. The provenance mismatch remains unresolved.
