# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

Historical facts remain scoped to the checkpoint where they were true. Current-source facts below describe Java/source head `934e74b8ff619178d703f73df8a16ee97b3fc2af` unless stated otherwise.

The reevaluation on 2026-09-13 changed milestone **status labels**, not the current Java source.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Active implementation branch: `codex/m1f-demand-driven-finite`.

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
- M1F Java/source head: `934e74b8ff619178d703f73df8a16ee97b3fc2af`;
- pre-reevaluation documentation head: `d748287fe2a5bfa38fe5a3f1bad2a864fc7fc591`.

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

M1F initial run `34731551626`, hardening run `34731757274`, and code-head run `34731827907` passed both target NeoForge versions.

These CI facts remain valid. They prove compilation, tests which actually exist, and package verification for those runs; they are not Minecraft runtime proof and do not prove untested failure paths.

## CC:T base contract

### FACT-CCT-001

The normal exposed peripheral type is `speaker`.

### FACT-CCT-002

The current composite delegates standard `playNote`, `playSound`, `playAudio`, and `stop` behavior to CC:T's real `SpeakerPeripheral`.

### FACT-CCT-003

Native `speaker_audio_empty` remains owned by standard CC:T `playAudio`. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Server media assets/import

### FACT-ASSET-001

`MediaAssetStore` owns UUID-named immutable encoded server files separately from speakers/playbacks. It supports exact import, quotas, retain/release lifetime, final-reference deletion, and seekable reads.

### FACT-ASSET-002

`MediaAssetStore.release()` keeps the entry/reference when final file deletion throws; it does not report that reference as successfully released.

### FACT-ASSET-003

`ServerMediaAssets` owns one shared store per running Minecraft server under `hqspeaker/media-assets` and currently also owns the per-server `FiniteRangeReadService`.

### FACT-ASSET-004

ComputerCraft local files are copied through temporary HQ writable staging, imported into a MediaAsset, and played through prepared-asset ownership. Playback takes a separate asset reference.

### FACT-ASSET-005

The bundled Lua module exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

### FACT-ASSET-006

`HQMediaStaging.releasePrepared()` restores an attached computer's prepared-ownership set when `MediaAssetStore.release()` throws.

By contrast, `releaseDetached()` is called after the detached computer's ownership set has been removed and only logs a release IOException; it does not preserve a retry owner. The detach-during-prepare cleanup has the same log-only release-failure behavior.

## M1D analysis facts

### FACT-M1D-001

`FiniteMediaAnalyzer` inspects encoded media without decoding the complete track to PCM and uses bounded read windows.

### FACT-M1D-002

Frozen M1D historically analyzed MP3, OGG Vorbis, WAV, AIFF, and AU. That historical breadth does not define the final product promise.

### FACT-M1D-003

MP3 analysis records coarse encoded seek points. Current WAV analysis records its audio-data start; final common-WAV direct time-to-byte layout remains later work.

## M1E server-authority facts

### FACT-M1E-001

Current finite server states are `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`. The canonical server clock starts immediately on successful prepared play and does not wait for client renderer readiness.

### FACT-M1E-002

Server duration/clock determines natural finite EOF. Non-looping exact-duration seek ends; looping exact-duration seek wraps to zero.

### FACT-M1E-003

Client finite status is READY or diagnostic ERROR. Client decode/render status does not own canonical playback state.

### FACT-M1E-004

The 2026-09-12 runtime diagnostic showed server state/control continuing while the old client MP3 bridge failed after decoder setup.

The final focused M1E manual acceptance script was not run to a recorded PASS because the owner explicitly chose to skip it.

### FACT-M1E-005

Current deterministic M1E-specific coverage includes `FinitePlaybackClockTest`. The current test tree contains no dedicated `HQFiniteMediaServer` state-machine test.

### FACT-M1E-006

Current `HQFiniteMediaServer.sendToRelevant()` performs direct per-player network sends without a local runtime-exception catch.

Authoritative start/stop/control/state methods call network-send helpers inline.

This is a source fact; no runtime packet-send failure has been recorded.

### FACT-M1E-007

Current `playPrepared()` assigns the new `session` before BEGIN/STATE sends. Its surrounding `catch (RuntimeException)` releases the retained asset but does not clear the installed session or its `assetReferenceHeld` flag.

### FACT-M1E-008

Current `releaseAssetReference()` clears `assetReferenceHeld` before invoking `MediaAssetStore.release()` and logs an IOException rather than restoring the flag.

Because the store preserves a reference when final deletion fails, source bookkeeping can diverge on that failure path.

## M1F current transport facts

### FACT-M1F-001

Current HQ protocol version is `5`.

Modern finite transport registers bounded range requests and range data instead of the old whole-file CHUNK/END transfer.

### FACT-M1F-002

`HQFiniteMediaChunkPacket` and `HQFiniteMediaEndPacket` are removed from current M1F source/registration.

### FACT-M1F-003

Current `HQFiniteMediaStatePacket` includes authoritative semantic state plus server-selected `anchorOffset` and `anchorTime`.

The current M1F client waits for STATE before its first range request.

### FACT-M1F-004

Current implementation bounds are:

- max one range response: 128 KiB;
- one active finite client byte window: 512 KiB;
- max outstanding range requests per player: 4;
- max outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are implementation values, not permanent public API guarantees.

### FACT-M1F-005

`FiniteRangeReadService` performs range reads on a bounded executor. An accepted task retains the MediaAsset before queueing and normally releases that temporary reference on completion/cancellation.

### FACT-M1F-006

Before successful range bytes are sent, `HQFiniteMediaServer.completeRange()` checks the current session generation/asset and current player relevance. Stale completions are discarded.

### FACT-M1F-007

`ServerMediaAssets.closeServer()` calls range-service close before store close.

`FiniteRangeReadService.close()` is written so a later call can retry waiting for termination after an earlier timeout/interruption.

### FACT-M1F-008

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE. It supports arbitrary `reset(anchorOffset)`, request/accept/retry/probe/copy/cancel.

It currently has no consume/discard/advance method which slides the live window forward while preserving unread prefetched bytes.

### FACT-M1F-009

Current modern `HQFiniteMediaClient` no longer creates complete-song `.part/.media` files and no longer uses `FileFiniteAudioStream` for prepared playback.

### FACT-M1F-010

The project-specific prototype API `audioPlayStaged()` is removed from the current composite/server path.

### FACT-M1F-011

Current M1F-specific deterministic tests are `FiniteRangeWindowTest` and `FiniteRangeReadServiceTest`.

They do not directly instantiate/test the complete `HQFiniteMediaServer`/`HQFiniteMediaClient` transport integration.

### FACT-M1F-012

The original pre-M1F design explicitly said M1F tests must prove source/generation/asset validation, relevance/dimension checks, stale completion discard, cancellation/account/ref cleanup, shutdown ordering, no tick-thread reads, packet sizing, exact bytes, arbitrary offsets, bounded client RAM, no client whole-song files, availability semantics, and direct-staged removal.

The current two M1F-specific unit-test classes cover only a subset of that list; several other items are currently supported by source review rather than dedicated deterministic tests.

### FACT-M1F-013

No focused M1F Minecraft runtime transport acceptance is currently recorded.

### FACT-M1F-014

M1F does not decode/render prepared MP3/WAV. M1G owns progressive decode, PCM, and audible positional rendering.

## Current milestone-status fact

### FACT-STATUS-001

After the 2026-09-13 reevaluation, the current project status is not `M1E fully finalized` or `M1F fully source/test/CI complete`.

The source architecture is implemented and both-target CI evidence remains green, but correctness/acceptance work is reopened as documented in `M1E-M1F-REEVALUATION-2026-09-13.md`.

No Java was changed by the reevaluation itself.

## Later facts

### FACT-NEXT-001

M1G has not started.

### FACT-NEXT-002

Full dynamic listener late-entry/leave-return/rejoin lifecycle remains M1H. M1F currently performs relevance checks on range admission/completion but does not implement the full M1H lifecycle.

### FACT-NEXT-003

Native FLAC is not currently proven/advertised as final support; it remains gated later work.

### FACT-NEXT-004

Inherited legacy finite/live/multispeaker code remains for later migration/removal.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved.
