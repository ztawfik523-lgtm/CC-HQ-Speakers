# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

Current source facts in this file are based on M1E hardening code candidate `521d4323d9216c8a99e8ec60426997c3330c4068` unless stated otherwise.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current hardening branch: `codex/m1e-final-hardening`.

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- M1B asset store: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- original M1E semantics: `d0e66ab9135359627086c13647d5241ad778643f`;
- historical M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`;
- M1F Java/source checkpoint before reevaluation: `934e74b8ff619178d703f73df8a16ee97b3fc2af`;
- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1;
- Java 21;
- CC:Tweaked 1.120.0;
- NeoForge 21.1.247 baseline;
- NeoForge 21.1.248 compatibility.

### FACT-CI-001

M1E final hardening CI run `34757923455` passed both target NeoForge versions, including build/tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 final-hardening artifact:

- artifact id `10317494766`;
- artifact ZIP SHA-256 `1a8231afab97b0374063301bd586e82c94f1306e39ccbf77ad048e6330d615a5`;
- JAR `hqspeaker-1.1.4-1.21.1-neoforge.jar`;
- JAR SHA-256 `da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`.

NeoForge 21.1.248 artifact id is `10317912658`, artifact ZIP SHA-256 `d4d359bf276e5d8d3b615a52d4f4a748c58799102a7063711bda6df0d6f0b05e`.

CI is not Minecraft runtime proof.

## Packaging/build

### FACT-BUILD-001

The packaged JAR embeds:

- JLayer `1.0.1.4` with compatibility range `[1.0.1.4,1.0.2)`;
- mp3spi `1.9.5.4` with compatibility range `[1.9.5.4,1.9.6)`;
- Tritonus Share `0.3.7.4` with compatibility range `[0.3.7.4,0.3.8)`.

The Gradle declaration prefers the exact bundled version while retaining the compatibility range. This avoids dynamic range metadata lookup during dependency resolution.

### FACT-BUILD-002

Before the exact-version preference was added, two CI attempts failed before Java compilation because NeoForged Maven returned HTTP 502 while Gradle attempted to list versions for the ranged sound-library dependencies.

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

`MediaAssetReleaseQueue` is the retry owner for logical releases which fail. Callers transfer responsibility to the queue instead of forgetting the still-live reference.

### FACT-ASSET-004

Playback, prepared/detached staging ownership, rejected prepared assets, and accepted range-read leases use the shared retry-safe release path.

### FACT-ASSET-005

`ServerMediaAssets` owns one shared store, release queue, and bounded finite range-read service per Minecraft server.

### FACT-ASSET-006

Server shutdown clears speaker composites before closing shared media services. `ServerMediaAssets.closeServer()` closes/drains range IO before closing the asset store.

### FACT-ASSET-007

ComputerCraft local files are copied through temporary writable staging, imported into a MediaAsset, and played through prepared-asset ownership. Playback takes a separate asset reference.

### FACT-ASSET-008

The bundled Lua module exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## M1D analysis facts

### FACT-M1D-001

`FiniteMediaAnalyzer` inspects encoded media without decoding the complete track to PCM and uses bounded read windows.

### FACT-M1D-002

Frozen M1D historically analyzed MP3, OGG Vorbis, WAV, AIFF, and AU. That historical breadth does not define the final product promise.

### FACT-M1D-003

MP3 analysis records coarse encoded seek points. Final common-WAV direct time-to-byte layout remains M1G work.

## M1E server-authority facts

### FACT-M1E-001

Canonical finite states are `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`.

A successful prepared play installs a server-owned session whose clock starts immediately. It does not wait for client READY or renderer setup.

### FACT-M1E-002

Server duration/clock determines natural EOF. Non-looping exact-duration seek ends; looping exact-duration seek wraps to zero.

### FACT-M1E-003

Client finite status is READY or diagnostic ERROR. Client decode/render/transport state does not own canonical playback.

### FACT-M1E-004

Server-side ERROR freezes canonical position at the failure instant.

### FACT-M1E-005

Prepared-start construction/snapshot work which can throw is completed before canonical session installation. Work after installation is best-effort projection.

### FACT-M1E-006

Client network projection is exception-contained and broadcasts isolate each relevant player independently. One recipient's runtime send failure does not abort canonical state or later recipients.

### FACT-M1E-007

Lua state-event projection is best-effort per attached computer.

### FACT-M1E-008

Playback release hands responsibility to `MediaAssetReleaseQueue`. A final-file deletion failure cannot make the session silently forget a still-live reference.

### FACT-M1E-009

Current deterministic M1E-specific coverage includes `FinitePlaybackClockTest`, `FinitePlaybackStateMachineTest`, `BestEffortProjectionTest`, and `MediaAssetReleaseQueueTest`.

### FACT-M1E-010

The final M1E manual Minecraft acceptance script was not run to a recorded PASS because the owner explicitly chose to skip it.

### FACT-M1E-011

The 2026-09-12 runtime diagnostic showed server state/control continuing while the obsolete client MP3 bridge failed after decoder setup. This supports authority separation but is not the skipped final M1E acceptance run.

### FACT-M1E-012

M1E source/test/CI hardening is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

The remaining missing final Minecraft PASS is an explicit waived runtime-evidence boundary, not an open M1E source blocker.

## M1F current transport facts

### FACT-M1F-001

HQ protocol version is `5`. Modern finite transport uses bounded range requests/data instead of whole-file CHUNK/END transfer.

### FACT-M1F-002

`HQFiniteMediaStatePacket` carries authoritative semantic state plus server-selected `anchorOffset` and `anchorTime`. The M1F client waits for STATE before first range demand.

### FACT-M1F-003

Current implementation bounds are:

- max range response: 128 KiB;
- active finite client encoded window: 512 KiB;
- max outstanding requests/player: 4;
- max outstanding encoded bytes/player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are implementation values, not permanent public API guarantees.

### FACT-M1F-004

Accepted range work retains the MediaAsset before queueing and releases that lease through the shared retry-safe release path.

### FACT-M1F-005

Before successful range data is sent, the server rechecks current session generation/asset and current player relevance.

### FACT-M1F-006

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE and supports arbitrary re-anchor/reset.

It currently has no consume/discard/advance method which slides the live window while preserving unread prefetched bytes.

### FACT-M1F-007

Modern prepared client transport no longer creates complete-song `.part/.media` files and no longer uses `FileFiniteAudioStream` as its prepared transport consumer.

### FACT-M1F-008

The project-specific prototype API `audioPlayStaged()` is removed from current source.

### FACT-M1F-009

M1F deterministic acceptance remains incomplete relative to the original pre-M1F matrix, and no focused M1F Minecraft runtime transport PASS is recorded.

### FACT-M1F-010

M1F does not decode/render prepared MP3/WAV. M1G owns progressive decode, PCM, and audible positional rendering.

## Later facts

### FACT-NEXT-001

M1G has not started.

### FACT-NEXT-002

Full dynamic listener late-entry/leave-return/rejoin lifecycle remains M1H.

### FACT-NEXT-003

Native FLAC is not currently proven as final support; it remains gated later work.

### FACT-NEXT-004

Inherited legacy finite/live/multispeaker code remains for later migration/removal.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved.
