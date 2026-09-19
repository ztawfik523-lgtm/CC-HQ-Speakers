# Verified facts

Updated: 2026-09-19

Facts only. Recommendations and unresolved choices belong elsewhere.

## Repository / platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current active branch: `codex/m1j-multispeaker`.

Important source checkpoints:

- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- M1G preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`;
- final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`;
- post-M1G hardening source checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`, latest full verification CI `35406595856`;
- M1H-1 listener-membership source checkpoint: `84e7bab99009e5871934a960908945ceb00a10a9`, CI `35410830197`;
- M1H-2 recovery source/test checkpoint: `aa72f0d2fc9f8cde53cd956389beca1743d06165`, CI `35411480844`;
- M1H-3 moving-source source/package checkpoint: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`, CI `35451236630`;
- M1J first implementation checkpoint: `b557773b9c6f6b8029aec132a1706f0d8da914bd`, CI `35466635285`.

### FACT-PLATFORM-001

Target stack: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.247 baseline and 21.1.248 compatibility.

## M1J multispeaker facts

### FACT-M1J-001

Modern prepared multispeaker uses one shared `FinitePlaybackAuthority` and one playback asset reference across independently positional physical speaker endpoints. The member set is snapshotted at start; there is no expected-member barrier.

### FACT-M1J-002

Protocol v8 adds `playbackId` and `stateRevision` while retaining `decodeRevision`. A client keeps one `FinitePlaybackProjection` per shared playback. Equivalent same-revision STATE packets arriving later from another endpoint do not re-anchor the shared local clock.

### FACT-M1J-003

Modern prepared pause/resume/seek/loop/stop are shared-playback semantics. Endpoint volume and mute are independent. Muting uses effective volume zero without overwriting the configured endpoint volume, so unmute rejoins current playback time.

### FACT-M1J-004

Encoded range windows, decoders, PCM queues, and Minecraft renderers remain per physical endpoint. Sharing those resources is a later profiling decision, not part of M1J correctness.

### FACT-M1J-005

CI `35466635285` passed both supported NeoForge targets for checkpoint `b557773b9c6f6b8029aec132a1706f0d8da914bd`. This is source/test/package evidence, not focused Minecraft multispeaker runtime proof.

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

Post-M1G hardening starts range shutdown at `ServerStoppingEvent`, keeps failed close ownership reachable, retries stale closing services before a new server opens the media root, and preserves failed-deletion bookkeeping/root-lock ownership until cleanup succeeds. KI-054 is resolved at source/component level.

### FACT-ASSET-004

Each `HQMediaStaging` instance creates a persistent ComputerCraft save-directory mount under a fresh random `hqspeaker/staging/<uuid>` path. Final M1G whole-owner cleanup unmounts/releases ownership and then deletes all remaining top-level staging entries; the writable mount's delete operation removes nested directories recursively. One-computer detach does not clear the shared mount. KI-061 is resolved in source.

### FACT-ASSET-005

`MediaAssetStore.writeExact()` now bounds repeated zero-byte reads and fails deterministically after the no-progress limit while tolerating temporary zero reads. Import first attempts `ATOMIC_MOVE` and falls back to a same-root non-atomic move when unsupported. KI-064 is resolved with deterministic component tests.

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

Modern BEGIN carries initial world position and block coordinates. Modern STATE still does not carry x/y/z. M1H-3 now uses BEGIN block coordinates to recompute moving-source world position locally and calls `FiniteSpeakerSound.updatePosition(...)` while the renderer is active.

## Rechecked client/protocol facts

### FACT-AUDIT-001

KI-053 is resolved: ordinary same-revision STATE no longer resets a healthy slid range window merely because the time-derived codec anchor moved or fell outside the current window.

### FACT-AUDIT-002

KI-055 deterministic coverage is resolved: real JLayer MP3 progressive decode and pure renderer-read policy tests exist.

### FACT-AUDIT-003

`scripts/m1d_media_analysis_test.lua` remains historical and is not a valid current M1G prepared-format gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking APIs.

### FACT-AUDIT-004

After renderer start, a short empty PCM queue still becomes local silence. M1H-2 now treats five seconds of continuous renderer starvation as a recovery condition and rejoins current authoritative server time.

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

Post-M1G hardening keeps blocking stream DNS outside both the ownership monitor and the command-order lock. DNS may still block the calling ComputerCraft command, but server tick/cleanup cannot wait behind that DNS, and the CC:T main-thread `audioPlayPrepared` path cannot be blocked by it either. Validated single-speaker stream commit reacquires the short command-order lock only after DNS returns. A mutation revision rejects that normal stream start if a newer playback/control command was issued while DNS was pending. KI-062 is resolved.

### FACT-AUDIT-013 — replacement-before-admission

RAW replacement now validates/converts the incoming PCM before ownership transfer. Prepared replacement uses a retained, fully constructed `PreparedStart` admission token and only stops current ownership before a no-normal-rejection commit. KI-063 is resolved for the known RAW/prepared paths.

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

KI-062, KI-063, KI-054, and KI-064 are resolved by post-M1G hardening checkpoint `3d30ce4564de749f32171666df65de739b08ad77`, CI `35406595856`.

### FACT-M1H-LISTENER-001

M1H-1 adds an admitted-listener UUID set for each active modern finite session. The server checks membership every server tick against the fixed 32-block relevance rule.

### FACT-M1H-LISTENER-002

A newly relevant player receives BEGIN plus current authoritative STATE immediately. The existing READY -> STATE path remains enabled, so one additional same-revision STATE may follow. This is intentional.

### FACT-M1H-LISTENER-003

A player who leaves relevance receives targeted STOP before membership removal when still connected. Disconnected players are pruned without a packet. A player in another dimension can still receive targeted cleanup through the server player list.

### FACT-M1H-LISTENER-004

READY and finite range requests/completions now require both current relevance and current listener membership. Stop/replacement clears admitted listeners with STOP; natural end/error sends terminal STATE to admitted listeners and clears membership.

### FACT-M1H-LISTENER-005

M1H-1 source checkpoint `84e7bab99009e5871934a960908945ceb00a10a9` passed CI `35410830197` on NeoForge 21.1.247 and 21.1.248, including deterministic tests, packaged-mod verification, and artifact upload. Focused real-Minecraft walk-in/walk-out/re-entry acceptance was deferred by the owner and remains in the backlog.

### FACT-M1H-RECOVERY-001

M1H-2 source/test checkpoint `aa72f0d2fc9f8cde53cd956389beca1743d06165` passed CI `35411480844` on NeoForge 21.1.247 and 21.1.248, including deterministic tests, packaged-mod verification, and artifact upload.

### FACT-M1H-RECOVERY-002

An unexpected SoundEngine/renderer stream close is treated as local recovery rather than a fatal decode error. Lost-renderer recovery retries READY once per second until an authoritative STATE arrives.

### FACT-M1H-RECOVERY-003

Five seconds of continuous renderer PCM starvation triggers current-time recovery. Ordinary same-revision STATE snapshots do not reset that timer, and same-revision authoritative STATE can rebuild a discarded local decoder without changing server `decodeRevision`.

### FACT-M1H-RECOVERY-004

Focused Minecraft resource-reload, renderer-loss, and long-starvation recovery testing is not recorded and is deferred to the runtime backlog.

### FACT-M1H-MOVING-001

M1H-3 source/package checkpoint `5cd6d6ddcad4b5b4887b903f471de0f2f812795c` passed CI `35451236630` on NeoForge 21.1.247 and 21.1.248, including the existing deterministic suite, packaged-mod verification, and artifact upload.

### FACT-M1H-MOVING-002

Sable Companion 1.6.0 is embedded in the packaged mod. Modern finite speaker position resolution first handles Sable sublevels, then the existing VS2 ship transform path, otherwise keeping the static block-center position.

### FACT-M1H-MOVING-003

The client updates the existing finite positional sound locally each client tick. The server resolves the moving speaker position locally for the fixed 32-block membership/range check. Protocol v7 has no moving-position packet and no continuous x/y/z network traffic.

### FACT-M1H-MOVING-004

Native ordinary Create contraption assembly/disassembly behavior is not part of M1H-3. The selected scope is Sable-based moving worlds (including Aeronautics-style sublevels) plus VS2 rather than a universal movement-provider framework.

### FACT-M1H-MOVING-005

Focused real-Minecraft Sable/Aeronautics and VS2 movement testing is not recorded and remains in the runtime backlog.

## Later milestone facts

M1H-1 membership, M1H-2 reload/loss/starvation recovery, and M1H-3 Sable/VS2 moving-source support are implemented at source/test/CI/package level. Their focused Minecraft checks are deferred to the runtime backlog. M1J modern finite multispeaker is implemented at source/test/CI/package level but still awaits focused Minecraft multispeaker acceptance. Optional codecs such as FLAC remain future decision-gate work. Inherited legacy finite/live/multispeaker code remains for later migration/removal. SPR acoustic/range compatibility remains later integrated compatibility work.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. The provenance mismatch remains unresolved.
