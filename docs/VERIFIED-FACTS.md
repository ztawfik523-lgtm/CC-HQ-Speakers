# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

Current source/test facts in this file use M1F finalization candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` unless stated otherwise.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current branch: `codex/m1f-finalization`.

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- M1B asset store: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- original M1E semantics: `d0e66ab9135359627086c13647d5241ad778643f`;
- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- pre-finalization M1F transport checkpoint: `934e74b8ff619178d703f73df8a16ee97b3fc2af`;
- M1F sliding/validation pass: `80d4fd983a7595101d5c8b8fa26011c6e79cd880`;
- M1F validation/shutdown hardening: `3654a6018d50469a1e8f0a3d19543ae6a7ab8fcd`;
- M1F in-place sliding refinement: `c579ddf3589a448d80e62df584358d5053a29e1d`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1;
- Java 21;
- CC:Tweaked 1.120.0;
- NeoForge 21.1.247 baseline;
- NeoForge 21.1.248 compatibility.

### FACT-CI-001

M1E final hardening CI run `34757923455` passed both target NeoForge versions.

M1F finalization CI run `34763362365` passed both target NeoForge versions, including build/tests, packaged-mod verification, and artifact upload.

M1F 21.1.247 artifact:

- artifact id `10319592968`;
- artifact ZIP SHA-256 `b971f027cad9c22265e3080f17725859fa447ce71cd1d2d0a14f2d1710b1c131`;
- JAR `hqspeaker-1.1.4-1.21.1-neoforge.jar`;
- JAR SHA-256 `2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`.

M1F 21.1.248 artifact:

- artifact id `10319563040`;
- artifact ZIP SHA-256 `131a250297ec3570c1ed1e0c61cc1d6b7569dff8ecc86d80080cb1ad345ff062`.

CI is not Minecraft runtime proof.

### FACT-CI-002

The first M1F finalization attempt `34762952499` failed only in test compilation because a new unit test directly referenced a Minecraft packet superclass unavailable on the pure test classpath. Main `compileJava` succeeded. The packet range rule was moved into a pure shared validator used by both packet/server paths; subsequent finalization runs passed.

## Packaging/build

### FACT-BUILD-001

The packaged JAR embeds the established exact sound-library versions while retaining their compatibility ranges:

- JLayer `1.0.1.4` / range `[1.0.1.4,1.0.2)`;
- mp3spi `1.9.5.4` / range `[1.9.5.4,1.9.6)`;
- Tritonus Share `0.3.7.4` / range `[0.3.7.4,0.3.8)`.

## CC:T base contract

### FACT-CCT-001

The exposed peripheral type is `speaker`.

### FACT-CCT-002

The composite delegates standard `playNote`, `playSound`, `playAudio`, and `stop` behavior to CC:T's real `SpeakerPeripheral`.

### FACT-CCT-003

Native `speaker_audio_empty` remains owned by standard CC:T `playAudio`. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Server media assets/import

### FACT-ASSET-001

`MediaAssetStore` owns UUID-named immutable encoded server files separately from speakers/playbacks. It supports exact import, quotas, retain/release lifetime, final-reference deletion, and seekable reads.

### FACT-ASSET-002

`MediaAssetStore.release()` keeps the entry/reference alive if final file deletion throws.

### FACT-ASSET-003

`MediaAssetReleaseQueue` is the retry owner for logical releases which fail. Playback, prepared/detached/rejected cleanup, and accepted range-read leases use it.

### FACT-ASSET-004

Server shutdown clears speaker composites before closing shared media services. `ServerMediaAssets.closeServer()` closes/drains range IO before closing the asset store.

### FACT-ASSET-005

ComputerCraft local files use temporary writable staging only to import immutable MediaAssets. The bundled Lua module exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## M1D analysis facts

### FACT-M1D-001

`FiniteMediaAnalyzer` inspects encoded media without decoding the complete track to PCM and uses bounded read windows.

### FACT-M1D-002

Historical analyzer breadth does not define final prepared/local support. Core final target remains MP3 plus implemented common WAV; native FLAC is gated later.

### FACT-M1D-003

MP3 analysis records coarse encoded seek points. Final common-WAV direct time-to-byte layout remains M1G work.

## M1E server-authority facts

### FACT-M1E-001

Canonical finite states are PLAYING, PAUSED, ENDED, and ERROR. Successful prepared playback starts the server clock immediately.

### FACT-M1E-002

Server duration/clock determines natural EOF. Non-looping exact-duration seek ends; looping exact-duration seek wraps to zero.

### FACT-M1E-003

Client finite status is READY or diagnostic ERROR. Client transport/decode/render state does not own canonical playback.

### FACT-M1E-004

Server ERROR freezes position. Prepared-start throwable work completes before canonical session installation. Client/Lua projection is best-effort and per-recipient isolated.

### FACT-M1E-005

M1E source/test/CI hardening is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

The final M1E focused Minecraft acceptance was explicitly skipped by the owner; no final runtime PASS is recorded.

## M1F transport facts

### FACT-M1F-001

HQ protocol version is `5`. Modern finite transport uses bounded `HQFiniteMediaRangeRequestPacket` / `HQFiniteMediaRangeDataPacket`, not whole-file CHUNK/END transfer.

### FACT-M1F-002

`HQFiniteMediaStatePacket` carries server-selected `anchorOffset` and `anchorTime`. The client waits for authoritative STATE before first byte demand.

A newly constructed `FiniteRangeWindow` is unanchored; `nextRequest()` returns empty until `reset(anchor)` is applied.

### FACT-M1F-003

Current implementation bounds are:

- max range: 128 KiB;
- active client encoded window: 512 KiB;
- max outstanding requests/player: 4;
- max outstanding bytes/player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are tuning values, not frozen public API.

### FACT-M1F-004

`FiniteRangeValidation` is a pure shared rule used by packet/server range admission. It validates wire range sanity plus source/asset/generation/bounds and exposes the current listener-relevance rule.

### FACT-M1F-005

Accepted range work retains the MediaAsset before queueing. The worker opens/positions/reads the encoded asset off the submitting/server thread and releases its temporary lease through the shared retry queue.

### FACT-M1F-006

Before range data is sent, `HQFiniteMediaServer.completeRange()` requires the current session generation/asset, resolves the current player by UUID, and requires current relevance. Stale replacement, disconnected, removed, wrong-dimension, or out-of-range completions are discarded rather than reviving old transport state.

### FACT-M1F-007

`FiniteRangeReadService` enforces bounded per-player request/byte accounting. Queued cancellation releases accounting and asset lease.

Its close path can be retried after a shutdown wait timeout; the media store is not closed until range-service close succeeds.

### FACT-M1F-008

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

It supports arbitrary reset/re-anchor and forward `advanceTo(...)` sliding. Sliding drops consumed prefix data, preserves useful unread overlap, keeps only whole still-useful pending requests, and opens new tail demand.

Partial forward sliding shifts the byte buffer in place instead of allocating another full client window.

### FACT-M1F-009

`FiniteRangeWindowTest` proves anchor gating, non-zero offsets, stale response rejection, missing-vs-EOF, retry, malformed-response recovery, sliding prefetch preservation, in-flight request handling, and repeated bounded refill beyond one window.

### FACT-M1F-010

`FiniteRangeReadServiceTest` proves exact arbitrary reads, dedicated-worker execution, per-player limits, in-flight asset lifetime, queued shutdown cleanup, deferred release retry ownership, and retryable shutdown timeout behavior.

### FACT-M1F-011

`FiniteRangeTransportTest` is an integrated fake consumer: a 2 MiB MediaAsset is range-read asynchronously into the bounded client window, advanced/refilled beyond one window, re-anchored to a distant offset, and byte-checked while bounded.

### FACT-M1F-012

Modern `HQFiniteMediaClient` does not create complete-song `.part/.media` files and does not use `FileFiniteAudioStream` as the modern prepared transport consumer.

### FACT-M1F-013

Current source tree has no modern `HQFiniteMediaChunkPacket` or `HQFiniteMediaEndPacket` classes.

### FACT-M1F-014

The project-specific prototype `audioPlayStaged()` is absent from the current composite/API. Temporary staging remains import plumbing for prepare/playFile.

### FACT-M1F-015

M1F source/test/CI/package completion is recorded at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`.

No focused real-Minecraft M1F transport PASS is recorded.

### FACT-M1F-016

M1F intentionally does not decode/render prepared MP3/WAV. M1G owns progressive decode, PCM, and audible positional rendering.

## Later facts

### FACT-NEXT-001

M1G has not started and is the next implementation milestone.

### FACT-NEXT-002

Full dynamic listener late-entry/leave-return/rejoin lifecycle remains M1H. M1F only enforces current relevance on range work.

### FACT-NEXT-003

Native FLAC remains gated later work.

### FACT-NEXT-004

Inherited legacy finite/live/multispeaker code remains for later migration/removal.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved.
