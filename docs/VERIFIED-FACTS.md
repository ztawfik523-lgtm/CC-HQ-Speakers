# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

Historical facts are scoped to the checkpoint where they were true. Current-source facts below describe the M1F branch after code head `934e74b8ff619178d703f73df8a16ee97b3fc2af` unless stated otherwise.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- M1B asset store: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- original M1E semantics: `d0e66ab9135359627086c13647d5241ad778643f`;
- M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`;
- pre-M1F documentation base: `bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`;
- first M1F transport commit: `07fb22b7e127f74615cb2f381d04f21fd202e550`;
- M1F hardening: `2de8398804eb40fbafd2ada79fd6ce188e109b34`;
- M1F final code/test head before docs: `934e74b8ff619178d703f73df8a16ee97b3fc2af`.

Active implementation branch: `codex/m1f-demand-driven-finite`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1;
- Java 21;
- CC:Tweaked 1.120.0;
- NeoForge 21.1.247 baseline;
- NeoForge 21.1.248 compatibility.

### FACT-CI-001

Frozen M1D CI run `34635484316` passed both target NeoForge versions.

Original M1E CI run `34658958488` passed both targets.

M1E finalization candidate run `34725651930` passed both targets, including package verification/artifact upload.

M1F initial run `34731551626` passed both targets.

M1F hardening run `34731757274` passed both targets.

Final M1F code-head run `34731827907` passed NeoForge 21.1.247 and 21.1.248, including build/tests, packaged-mod verification, and artifact upload.

These are source/test/package facts, not Minecraft runtime proof.

## CC:T base contract

### FACT-CCT-001

The normal exposed peripheral type is `speaker`.

### FACT-CCT-002

Current composite delegates standard `playNote`, `playSound`, `playAudio`, and `stop` behavior to CC:T's real `SpeakerPeripheral`.

### FACT-CCT-003

Native `speaker_audio_empty` remains owned by standard CC:T `playAudio`. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## HQ RAW/output ownership

### FACT-RAW-001

The normal single-speaker composite tracks one HQ continuous owner and prevents incompatible HQ continuous sources from overlapping accidentally.

### FACT-RAW-002

Current modern `speakPCM` per-call maximum exposed by the composite is `131072` contiguous samples.

HQ RAW admission uses bounded server-side queue/duration accounting and separate `hqspeaker_audio_empty` retry signaling.

RAW does not truthfully expose finite duration/arbitrary seek/natural EOF.

## Server media assets/import

### FACT-ASSET-001

`MediaAssetStore` owns UUID-named immutable encoded server files separately from physical speakers/playbacks. It supports exact import, per-asset/total quotas, retain/release lifetime, final-reference deletion, and seekable reads.

### FACT-ASSET-002

`ServerMediaAssets` owns one shared media store per running Minecraft server under the server/world root `hqspeaker/media-assets`.

### FACT-ASSET-003

ComputerCraft local files are copied through temporary HQ writable staging, imported into a reusable MediaAsset, then played through prepared-asset ownership.

Staging is not the final playback/library owner.

### FACT-ASSET-004

Prepared ownership is tracked by ComputerCraft computer ID. Playback takes a separate asset reference, so releasing preparation after successful play does not stop that playback.

### FACT-ASSET-005

The bundled `hqspeaker` module exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## M1D analysis facts

### FACT-M1D-001

`FiniteMediaAnalyzer` identifies supported historical M1D formats from encoded bytes without decoding the complete track to PCM and uses bounded read windows.

### FACT-M1D-002

Frozen M1D historically analyzed MP3, OGG Vorbis, WAV, AIFF, and AU forms. That historical analyzer breadth does not define the final product promise, which is MP3 + common WAV with optional later gated FLAC.

### FACT-M1D-003

MP3 analysis derives duration from encoded Layer III frames and records bounded coarse encoded seek points.

Current WAV analysis records the first supported audio-data start as its seek point; final direct common-WAV layout/time-to-byte metadata is M1G work.

## M1E server-authority facts

### FACT-M1E-001

Modern finite server semantic states are `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`. The canonical server clock starts immediately on successful play and does not wait for client renderer readiness.

### FACT-M1E-002

Server duration/clock determines natural finite EOF. Non-looping exact-duration seek ends; looping exact-duration seek wraps to zero.

### FACT-M1E-003

Client finite status is READY or diagnostic ERROR. Client renderer/decode status does not own canonical playback state.

### FACT-M1E-004

The 2026-09-12 runtime diagnostic showed the server continuing its own playback/control timeline while the old client MP3 bridge failed after decoder setup.

The final focused M1E manual acceptance script was not run to a recorded PASS because the project owner explicitly chose to skip it. M1E must not be described as Minecraft-runtime verified.

### FACT-M1E-005

For the tested MP3, server analysis reported `161.304` seconds while the old JavaSound/mp3spi bridge reported `322.584` seconds. The old client duration/seek bridge is not authoritative.

## M1F current transport facts

### FACT-M1F-001

Current HQ protocol version is `5`.

Modern finite transport registers bounded client-to-server range requests and server-to-client range data instead of whole-file CHUNK/END transfer.

### FACT-M1F-002

`HQFiniteMediaChunkPacket` and `HQFiniteMediaEndPacket` are removed from current M1F source/registration.

### FACT-M1F-003

Current `HQFiniteMediaStatePacket` includes authoritative semantic state plus `anchorOffset` and `anchorTime` selected by the server.

The M1F client waits for authoritative STATE before sending its first encoded range request.

### FACT-M1F-004

Current M1F implementation bounds are:

- max one range response: 128 KiB;
- one active finite client encoded window: 512 KiB;
- max outstanding range requests per player: 4;
- max outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are current implementation values, not guaranteed permanent public API constants.

### FACT-M1F-005

`FiniteRangeReadService` performs prepared-asset range reads away from the Minecraft tick thread using a bounded executor/queue.

Every accepted range job retains the MediaAsset before it is queued and releases that temporary reference on completion/cancellation.

### FACT-M1F-006

Before a completed range is sent, the server rechecks current session generation/asset identity and player connection/relevance. Stale completions are discarded.

### FACT-M1F-007

`ServerMediaAssets.closeServer()` stops/drains range IO before closing the MediaAsset store. Range-service close is retryable after an incomplete first shutdown attempt.

### FACT-M1F-008

`FiniteRangeWindow` is bounded by capacity independent of full asset size and distinguishes:

- DATA_AVAILABLE;
- NEED_DATA;
- TRUE_ASSET_EOF;
- CANCELLED_OR_STALE.

It supports arbitrary encoded re-anchors, timeout/retry, and rejects stale/malformed responses without permanently wedging demand.

### FACT-M1F-009

Current modern `HQFiniteMediaClient` no longer creates `.part/.media` complete-song files and no longer uses `FileFiniteAudioStream` for prepared playback.

The old `FileFiniteAudioStream` class may still exist as unused/legacy source for later cleanup.

### FACT-M1F-010

The project-specific prototype API `audioPlayStaged()` is removed from the current composite/server path. It was not an inherited HQ Speakers compatibility API.

### FACT-M1F-011

Modern finite `audioStatus()` no longer reports old whole-transfer `transferredBytes`; `totalBytes` remains server asset metadata.

### FACT-M1F-012

M1F source/test/CI is complete at `934e74b8...`, but no M1F Minecraft runtime acceptance has been recorded.

M1F intentionally does not decode/render prepared MP3/WAV; M1G owns that.

## Current unresolved/later facts

### FACT-NEXT-001

M1G has not started. It owns progressive MP3/common-WAV decode/conversion, temporary-starvation handling, MP3 reservoir pre-roll, bounded mono PCM, decoder cancellation, and audible positional rendering.

### FACT-NEXT-002

Full dynamic listener late-entry/leave-return/underrun lifecycle remains M1H. M1F already rejects/discards irrelevant range work but does not claim complete M1H behavior.

### FACT-NEXT-003

Native FLAC is not currently proven/advertised as final support; it is gated M1I work.

### FACT-NEXT-004

Inherited legacy finite/live/multispeaker code remains for later migration/removal. The new M1F prepared path must not be modeled on old whole-packet/whole-PCM behavior.

## License

### FACT-LICENSE-001

Top-level repository `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved and must not be silently relicensed.
