# Verified facts

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

The current build workflow triggers on unfiltered `push` and `pull_request` events and builds both configured NeoForge targets. There is currently no documentation-path exclusion or concurrency cancellation in `.github/workflows/build.yml`.

## Packaging facts

### FACT-BUILD-001

Packaged dependencies include JLayer `1.0.1.4`, mp3spi `1.9.5.4`, and Tritonus Share `0.3.7.4`. Modern progressive MP3 decoding uses JLayer.

## CC:T contract facts

### FACT-CCT-001

The exposed peripheral type is `speaker`; standard `playNote`, `playSound`, `playAudio`, and `stop` delegate to CC:T's real speaker behavior. Native `speaker_audio_empty` remains CC:T-owned, while HQ RAW uses `hqspeaker_audio_empty`.

### FACT-CCT-002

The exact target CC:T 1.120.0 client speaker implementation explicitly updates live channel linear attenuation when speaker volume changes because Minecraft's sound-engine volume refresh does not update attenuation distance. Its calculation is `Math.max(volume, 1) * sound.getSound().getAttenuationDistance()`.

### FACT-CCT-003

Minecraft 1.21.1 exposes `SoundInstance.canStartSilent()` for long-lived sounds which should be allowed to start while currently silent. Current `FiniteSpeakerSound` does not override it.

## Asset/import facts

### FACT-ASSET-001

`MediaAssetStore` owns immutable UUID-addressed encoded server files with quotas, reference counting, and seekable reads. CC files use temporary writable staging only to import MediaAssets.

### FACT-ASSET-002

Active prepared/playback/range release paths preserve retry ownership when final release fails. Server shutdown attempts to stop/drain range IO before the store closes.

### FACT-ASSET-003

`MediaAssetStore.close()` currently clears completed-entry bookkeeping before shutdown deletion attempts. If one of those deletions fails, a subsequent `close()` has no retained completed-entry list to retry; next-start orphan pruning can remove managed leftovers. `ServerMediaAssets.closeServer()` removes its static server entry only after `store.close()` returns successfully, so a thrown store-close failure also skips registry removal. The current `ServerStoppedEvent` handler catches/logs that failure and does not schedule another close retry. This was documented by the 2026-09-14 audit and is not fixed in source.

A later full-repository audit identified a stronger shutdown path: `ServerMediaAssets.closeServer()` calls `FiniteRangeReadService.close()` before `MediaAssetStore.close()`. `FiniteRangeReadService.close()` may throw if its workers do not terminate within its shutdown wait or if player accounting remains non-empty. If that happens, `store.close()` and `SERVERS.remove(...)` are never reached, so the store's root lock can remain held and the stopped server/assets remain in the static registry. This is not fixed in source.

### FACT-ASSET-004

Each `HQMediaStaging` instance creates a ComputerCraft save-directory mount under a fresh random `hqspeaker/staging/<uuid>` path. Its cleanup path unmounts attached computers/releases prepared ownership but does not clear arbitrary leftover files in that mount. CC:T 1.120.0 implements `createSaveDirMount()` as a persistent disk-backed `WritableFileMount` rooted at the requested subdirectory; unmounting does not delete it. This is KI-061 and is not fixed in source.

### FACT-ASSET-005

`MediaAssetStore.writeExact()` currently retries a zero-byte `ReadableByteChannel.read(...)` indefinitely using `Thread.onSpinWait()` with no zero-read limit, while several other readers in the repository use bounded zero-read guards. `MediaAssetStore.importAsset()` also uses `Files.move(..., ATOMIC_MOVE)` without an `AtomicMoveNotSupportedException` fallback. These are storage hardening gaps and are not fixed in source.

## M1E facts

### FACT-M1E-001

The server owns finite generation/state/time/control/natural EOF. Client READY requests state; client ERROR is diagnostic. Final focused Minecraft M1E acceptance was skipped/unrecorded.

## M1F facts

### FACT-M1F-001

Modern finite transport uses bounded range request/data rather than whole-file CHUNK/END transfer. Current tuning: 128 KiB max range, 512 KiB client encoded window, four outstanding requests/player, 512 KiB outstanding bytes/player, two range IO workers, queue size 64.

### FACT-M1F-002

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE; supports arbitrary re-anchor and forward sliding under a fixed memory cap.

### FACT-M1F-003

Modern prepared transport does not create a complete client song `.part/.media` file and does not expose `audioPlayStaged()`.

## M1G architecture facts

### FACT-M1G-ARCH-001

Locked owner choices are A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll.

Output is mono signed 16-bit PCM at source sample rate; one physical speaker remains one mono positional source.

### FACT-M1G-ARCH-002

The owner-selected M1G scope additionally specifies an explicit server-authoritative decoder/re-anchor revision, a fixed 32-block core listening/delivery radius with HQ volume changing gain rather than radius, global-volume-zero transport/render hibernation while canonical server time continues, and ordinary non-gapless replay after local physical EOF while authoritative looping remains enabled. Future Sound Physics Remastered compatibility owns deliberate extended-range/acoustic behavior and matching transport relevance.

## M1G integrated source facts

### FACT-M1G-001

Modern finite protocol version is 6. BEGIN carries an MP3/common-WAV `FiniteDecodeDescriptor`.

### FACT-M1G-002

Modern prepared/local media is narrowed to MP3 or supported common WAV: U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or selected narrow PCM/float WAVEX.

### FACT-M1G-003

STATE anchor selection provides exact frame-aligned WAV anchors and conservative E1 MP3 pre-roll anchors. `FiniteDecodeAnchorSelector.Anchor` contains exactly two fields: encoded byte `offset` and anchor time `seconds`. It does not contain frame-index, skip-frame, or skip-sample fields.

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

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation, and one source per physical speaker. It exposes `updatePosition(...)`, but the current modern finite client does not call that method after renderer creation. Modern finite STATE does not carry x/y/z updates; BEGIN carries initial world/block position. Moving-source/VS2 lifecycle therefore remains incomplete.

### FACT-M1G-011

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared finite engine.

## 2026-09-14 audit facts

### FACT-AUDIT-001

`HQFiniteMediaClient.state0()` currently resets the encoded window when the server anchor is outside the current slid window, even if the coarse anchor is unchanged. In that same case the decoder epoch may be preserved rather than restarted. This is KI-053 and remains unfixed.

### FACT-AUDIT-002

`ProgressiveMp3DecoderTest` currently tests discard/downmix helpers rather than decoding a real MP3 fixture through JLayer. The current client test tree has no focused `FinitePcmAudioStreamTest`.

### FACT-AUDIT-003

`scripts/m1d_media_analysis_test.lua` reflects the historical broad M1D prepared-format surface and is not a valid current M1G modern-prepared acceptance gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs.

### FACT-AUDIT-004

The 2026-09-14 audits updated documentation only; no implementation/test-script fix was made.

### FACT-AUDIT-005

After the modern renderer has started, an empty live PCM queue is represented as short local silence until PCM returns. Current M1G does not implement general long-underrun catch-up/rejoin to the then-current server position; that broader recovery remains M1H.

### FACT-AUDIT-006

`CONTROL SEEK` currently calls `cancelDecodeEpoch()` before any new decode epoch number is installed. `cancelDecodeEpoch()` can wake/cancel the old worker, while `decoderFailed()` treats an old-worker failure as current whenever `session.decodeEpoch` still equals that worker's epoch. Until the replacement STATE starts a new epoch, expected seek cancellation can therefore race with `decoderFailed()` and fail the client session. This is KI-056 and is not fixed in source.

### FACT-AUDIT-007

The server sends authoritative STATE after successful pause/resume/seek/volume/loop transitions, and `statePacket()` recomputes the codec anchor from the then-current canonical position. The client currently restarts when that encoded anchor changes. Exact WAV anchors therefore can change on ordinary non-seek STATE updates, and coarse MP3 anchors can change as playback advances. This is part of KI-057.

### FACT-AUDIT-008

Same-coarse-anchor semantic seek restart currently depends on the preceding SEEK control setting a client-local restart flag. STATE itself carries no explicit decoder/reanchor revision separate from its time-derived anchor. This is the other half of KI-057 and is not fixed in source.

### FACT-AUDIT-009

Modern finite live volume updates mutate the sound instance's volume and refresh the BLOCKS category volume, but they do not explicitly install the owner-selected fixed 32-block attenuation distance on the active channel. This is part of KI-058.

### FACT-AUDIT-010

`HQFiniteMediaServer` currently uses a fixed 32-block relevance radius for modern finite BEGIN/STATE/range serving. The owner has selected that fixed-radius shape for M1G rather than volume-dependent relevance; volume above 1 must not silently enlarge the core HQ finite range. Future SPR compatibility may intentionally change both acoustic and transport range later.

### FACT-AUDIT-011

`HQFiniteMediaClient.tryStartRenderer()` sets `rendererStarted = true` before calling `SoundManager.play(sound)` and has no path which clears/retries that latch merely because the sound failed to become active. This is KI-060.

### FACT-AUDIT-012

NeoForge 1.21.1 payload handlers execute on the main thread by default unless registration explicitly requests the network thread. The current modern packet registration does not opt into network-thread execution, so the audit did not identify a packet-handler game-state threading bug from that registration pattern.

## 2026-09-16 full-repository review facts rechecked against source

### FACT-AUDIT-013

`HQSpeakerCompositePeripheral.callMethod(...)` and `tickOwnership()` are both synchronized on the composite. Server tick calls `HQSpeakerCompositePeripheral.tickAll()`, which invokes `tickOwnership()` for every active composite. The dynamic STREAM methods route through `callMethod(...)` into `HQSpeakerPeripheral.startStreamAtTick(...)`, whose URL validation performs synchronous `InetAddress.getAllByName(host)`. A computer-thread stream call can therefore hold the composite monitor during DNS while the server tick waits for the same monitor. This does not apply to every annotated composite method: for example, `audioPrepareStaged(...)` is not itself synchronized on the composite.

### FACT-AUDIT-014

`HQSpeakerCompositePeripheral.startRaw(...)` calls `beginReplacingHQ(Owner.RAW)` before it checks RAW capacity and may then return `false`; `audioPlayPrepared(...)` calls `beginReplacingHQ(Owner.STAGED_FINITE)` before `finite.playPrepared(...)` can reject or throw. A rejected/failed replacement can therefore stop an existing HQ continuous source before the new source is accepted.

### FACT-AUDIT-015

Inherited live HLS uses one monotonic `currentSegmentIndex` across refreshed playlists whose segment lists are indexed from zero. After the initial playlist window has been consumed, a normal refreshed live window can therefore have `segs.size() <= currentSegmentIndex`, causing no new segments to play. This is inherited live-stream work, not modern M1G finite playback.

### FACT-AUDIT-016

Legacy Lua-visible capability lists advertise formats/stream capabilities broader than the modern prepared engine. `HQSpeakerPeripheral.speakSupportedFiles()` includes `mp2`, `mp4`, `m4a`, and `aac`, while the modern prepared gate accepts only supported common WAV or MP3. These lists describe inherited surfaces and must not be treated as the modern prepared contract.

### FACT-AUDIT-017

Legacy `playNoteAll(...)` synthesizes a sine wave from pitch and does not use its `instrument` argument; `playSoundAll(...)` delegates to that path and does not use the requested `soundName`. These inherited helpers do not preserve normal CC:T note/sound semantics.

## Current selected direction and remaining implementation choice

### FACT-M1G-NEXT-001

Loop behavior is selected: ordinary local replay after physical EOF while authoritative state still says `looping=true`, with a normal restart gap acceptable. It is not implemented yet.

### FACT-M1G-NEXT-002

Decoder/re-anchor behavior is selected: introduce an explicit server-authoritative decoder/re-anchor revision so semantic seek is self-describing and ordinary state snapshots do not restart healthy decoders. One implementation-shape choice remains before coding protocol v7: retain PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional low-latency hints, or remove that duplicate authority path and let STATE alone carry those transitions. The unreleased internal v6 protocol does not impose compatibility pressure to keep them.

### FACT-M1G-NEXT-003

Range and volume-zero behavior are selected: M1G keeps the existing fixed 32-block core radius; HQ volume changes gain rather than radius; global HQ volume zero hibernates local decode/render/range requests while canonical server time continues. General dynamic listener lifecycle remains M1H; extended acoustic/range behavior belongs to later SPR compatibility.

## Later milestone facts

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H. Native FLAC remains gated M1I. Inherited legacy finite/live/multispeaker code remains for later migration/removal.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. The provenance mismatch remains unresolved.
