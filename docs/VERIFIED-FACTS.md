# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

When behavior changed after the reviewed M1 reference, historical facts are explicitly scoped to that reference instead of being called current.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

References:

- untouched fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`;
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- completed M0.5 preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- completed M1B asset-store foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- current implementation branch: `codex/m1c-local-import`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility

Build dependency: `cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`.

### FACT-PLATFORM-002

The exact M1B head `40091ee32f412c1208e9016fca288b8d4f902dfa` completed the Java 21 GitHub Actions matrix successfully on NeoForge 21.1.247 and 21.1.248, including tests, package verification, and candidate-JAR upload.

During M1C, source head `c29db04b4c5ae38a0b06fca747100dd2b5f48a0a` also completed both target-version jobs successfully after the initial checked-exception compile error was corrected. Later documentation/lifecycle commits require their own exact-head CI before being called final.

A green CI build is build/test/package evidence, not Minecraft runtime proof.

### FACT-PLATFORM-003

Current `HQSpeakerNetwork` still registers the existing ten custom payload types. M1C does not replace the prototype begin/chunk/end/status packet family; client-pulled asset ranges are M1F work.

## CC:T 1.120.0 base speaker contract

### FACT-CCT-001

The normal peripheral type is `speaker`.

### FACT-CCT-002

`playNote(instrument [, volume [, pitch]])` accepts optional volume/pitch, resolves a real note-block instrument, validates the instrument, and is subject to the configured per-tick note limit.

Official documentation says omitted pitch defaults to `12`, while exact 1.120.0 source uses `pitchA.orElse(1.0)`. This is an upstream source/documentation discrepancy.

### FACT-CCT-003

`playSound(name [, volume [, pitch]])` resolves a Minecraft/modded sound identifier, accepts optional volume/pitch, rejects jukebox-song sound events, and returns false when native sound/audio conflict prevents playback.

### FACT-CCT-004

`playAudio(audio [, volume])` accepts signed 8-bit samples, maximum `128 * 1024` samples per call, at 48 kHz. It has one pending DFPWM buffer and returns false when another cannot be accepted.

### FACT-CCT-005

`speaker_audio_empty` is emitted after native pending audio is pulled/freed and another standard `playAudio` buffer may be accepted.

### FACT-CCT-006

`stop()` sets a native stop flag processed by `SpeakerPeripheral.update()` on a later server tick. It clears native DFPWM/latest arbitrary sound state; pending note events are stored separately and are not cleared by `SpeakerPeripheral.stop()`.

### FACT-CCT-007

`IDynamicPeripheral.callMethod` may run from ComputerCraft computer/Lua threads and one peripheral may be used by more than one computer. Main-thread Lua functions are queued to the main thread by CC:T.

## Historical reviewed-M1 HQ facts

### FACT-HIST-001

The inherited `HQSpeakerPeripheral.playNote` ignored its instrument argument and synthesized PCM; inherited `playSound` reused that generated-note path.

### FACT-HIST-002

The inherited `HQSpeakerPeripheral` exposed `speakStop()` / `audioStop()` but did not itself provide the standard CC:T `stop()` implementation.

### FACT-HIST-003

The inherited single-speaker server audio queue is an `ArrayBlockingQueue` of 16 `SpeakerChunk`s and `speakerTick()` polls one chunk per server tick.

### FACT-HIST-004

Legacy finite byte input is capped at 8 MiB and enters the inherited whole-packet/whole-decoded-PCM path.

### FACT-HIST-005

Legacy `HQAudioStream` finite decode uses a single-thread decoder executor. Retained finite decode may hold complete converted PCM and has a 64 MiB post-decode cap.

## Current M1A compatibility/output facts

### FACT-M1A-001

`ComputerCraftSpeakerBlockEntityMixin` exposes an `HQSpeakerCompositePeripheral` for the normal CC:T speaker while retaining the original CC:T `SpeakerPeripheral`; the exposed type remains `speaker`.

### FACT-M1A-002

The composite dispatches exposed standard `playNote`, `playSound`, `playAudio`, and `stop` to the original CC:T `SpeakerPeripheral`.

### FACT-M1A-003

The legacy HQ object is attached through an `IComputerAccess` proxy which suppresses its synthetic `speaker_audio_empty`, leaving standard `speaker_audio_empty` sourced by CC:T.

### FACT-M1A-004

The normal single-speaker composite tracks one HQ continuous owner: RAW, legacy finite, staged/prepared finite bridge, stream intent, or none. Starting a new incompatible HQ source stops the prior HQ source. Repeated accepted `speakPCM` calls while RAW owns output continue the same raw feed.

### FACT-M1A-005

An HQ continuous-source start also calls native CC:T `stop()`. Native pending notes remain separate. While HQ continuous output is active, exposed standard `playSound` / `playAudio` return false instead of overlapping it.

### FACT-M1A-006

The composite exposes `speakMaxSamples()` as `131072`, matching the inherited contiguous table conversion ceiling.

### FACT-M1A-007

HQ RAW uses separate `hqspeaker_audio_empty` admission. Admission checks both the inherited 16-entry packet queue and a duration allowance of `135872` outstanding samples. Accepted outstanding samples drain by `2400` samples per server tick.

### FACT-M1A-008

`RawFeedLifetime` is a pure Java state model for exact outstanding RAW samples, capacity, server-tick drain, packet-queue gating, and idle release. `RawFeedLifetimeTest` covers these behaviors.

### FACT-M1A-009

The legacy client RAW path has a bounded 64-chunk queue and may drop newly received RAW PCM when full. M1A admission is producer/server pacing, not a per-listener acknowledgement protocol.

### FACT-M1A-010

Calls which change the composite's current owner run one at a time on the same physical speaker, preventing two ComputerCraft computers from interleaving source replacement.

### FACT-M1A-011

`audioStatus()` follows the current composite owner. RAW does not claim finite seek/loop capabilities. `audioStop()` stops the current HQ source; standard `stop()` also requests native CC:T stop.

### FACT-M1A-012

Inherited `*All` / `*At` helpers still call legacy speaker instances directly and have not been migrated to the modern ownership/sync architecture.

### FACT-M1A-013

The inherited HQ stop helper remains radius-local; dynamic leave/re-enter renderer ownership remains later M1I work.

## M1B reusable media-asset facts

### FACT-M1B-001

`MediaAsset` gives one encoded finite-media asset a UUID, positive byte size, and diagnostic source name. The store, not the speaker, owns the filesystem path.

### FACT-M1B-002

`MediaAssetStore.importAsset` reserves the declared bytes before copying, writes a UUID `.part` using a bounded 64 KiB direct buffer, rejects short/long sources, forces the completed file, atomically renames it to `.media`, and only then publishes the asset.

### FACT-M1B-003

A successful import starts with one reference. `retain` adds a reference; `release` removes one. Final release deletes the `.media` file and reduces committed-byte accounting. If deletion fails, the store keeps bookkeeping rather than claiming the bytes were freed.

### FACT-M1B-004

The store separately tracks committed and reserved bytes, enforces caller-supplied per-asset and total limits, and reserves before copying so concurrent imports cannot overcommit the configured total.

### FACT-M1B-005

The store prunes UUID-named `.part`/`.media` process leftovers on construction and uses an OS file lock so a second live store cannot prune the first store's directory.

### FACT-M1B-006

If shutdown races an active import, the root lock stays held until the unpublished import cleans up. Close cleanup is retryable.

### FACT-M1B-007

`openRead(assetId)` returns a seekable encoded-file channel for a live asset. Media analysis, transfer, and decode are separate later layers.

## Current M1C local-import facts

### FACT-M1C-001

`HQMediaStaging` now owns the ComputerCraft writable staging mount. `HQFiniteMediaServer` no longer owns or mounts staging storage.

### FACT-M1C-002

`ServerMediaAssets` keeps one shared `MediaAssetStore` per running `MinecraftServer`, under the server/world root `hqspeaker/media-assets` directory.

The current per-asset/staging ceiling is 512 MiB. The current total-store value is an explicitly interim 2 GiB implementation value and is not recorded as a settled product-policy decision.

### FACT-M1C-003

The composite exposes `audioPrepareStaged`, `audioPlayPrepared`, and `audioReleasePrepared` in addition to the retained staging mount methods.

`audioPrepareStaged` imports a staged file into the shared asset store and returns the asset UUID. Prepared ownership is tracked by ComputerCraft computer ID.

### FACT-M1C-004

Detaching a preparing ComputerCraft computer releases prepared references still owned through that speaker. `audioReleasePrepared` only releases a prepared reference owned by that calling computer ID.

### FACT-M1C-005

`HQFiniteMediaServer.playPrepared` looks up the server-wide asset, retains a separate playback reference, opens the encoded asset, and uses the asset UUID as the transitional session/media ID. It releases that playback reference on stop or terminal/error paths guarded against duplicate release.

### FACT-M1C-006

A prepared asset UUID may be deliberately passed to another speaker on the same server because the encoded asset store is server-wide. Releasing the original preparation reference does not remove the encoded file while another playback reference remains.

### FACT-M1C-007

The bundled `hqspeaker.lua` now provides `prepareFile`, `playPrepared`, `releasePrepared`, and `playFile`. `playFile` performs prepare -> play -> release of the temporary preparation reference.

The helper checks source size before `fs.copy`, deletes a partial staged destination when copy fails, and retries staged cleanup after successful prepare.

### FACT-M1C-008

Once asset import has succeeded, a failure to remove the temporary staging file does not invalidate the shared asset. Java logs the staging cleanup failure and returns the prepared asset ID.

### FACT-M1C-009

On server shutdown, provider/speaker cleanup runs before `ServerMediaAssets.closeServer`, so prepared/playback references are released before final shared-store close. If shared-store close throws, the server-store wrapper remains in the map so cleanup may be retried.

### FACT-M1C-010

M1C prepared playback still bridges into the old finite sender. Fixed recipient capture, server-pushed whole-file transfer, client READY/STARTED/ENDED authority, and the renderer-observation timeout remain present until later milestones.

### FACT-M1C-011

`audioStatus()` includes `assetId` for a prepared-asset transitional finite session.

## Frozen/prototype finite facts

### FACT-PROTO-001

The frozen prototype proved writable ComputerCraft staging, 256 KiB chunked client transfer, disk-backed client encoded files, and file-backed incremental decode paths.

### FACT-PROTO-002

The current transitional finite sender still uses fixed recipients and prototype begin/chunk/end packets.

### FACT-PROTO-003

The current transitional finite status path still accepts client READY/STARTED/ENDED/error transitions and therefore is not yet the final server-authoritative finite state model.

## Retained finite/stream/multispeaker facts

### FACT-FINITE-001

`FiniteAudioTrack` retains complete mono signed-16-bit PCM with frame-aligned cursor state. Legacy finite controls/status transitions remain in source until migration.

### FACT-STREAM-001

`StreamingAudioSource` still has MP3_STREAM, HLS_STREAM, and TS_STREAM paths. Known retained issues include double volume application, HLS progression based on persistent segment index, whole-list TS demux, and an unsupported-decode path which may return compressed frame bytes unchanged.

### FACT-SYNC-001

Inherited multi-speaker All/At behavior still uses expected group/tap concepts. `SharedStreamingGroup` waits for expected tap count before shared decode.

## Lifecycle facts

### FACT-LIFE-001

The provider cache is explicitly cleared on speaker removal, server Level unload, and server shutdown; weak Level keys are only a fallback because cached values retain their Level.

### FACT-LIFE-002

M1C adds a server-wide asset-store lifecycle. Speaker/prepared ownership is cleaned before shared store close on `ServerStoppedEvent`.

## Test facts

### FACT-TEST-001

Current pure Java tests include retained finite/HLS/path/clock tests, `RawFeedLifetimeTest`, and `MediaAssetStoreTest` covering exact import, reference lifetime, quota reservation, concurrent imports, crash cleanup, directory locking, and shutdown/import behavior.

### FACT-TEST-002

`scripts/p0_cc_speaker_contract.lua` is the standard CC:T runtime contract. `scripts/m1a_output_contract.lua` checks M1A single-speaker HQ ownership and RAW admission behavior.

### FACT-TEST-003

`scripts/m1c_local_import_test.lua` is the M1C prepared-asset runtime contract. It checks unused prepare/release, released-asset rejection, prepared playback, separate playback-reference lifetime, `audioStatus().assetId`, and the `hqspeaker.playFile` convenience path.

None of these scripts should be reported as a runtime pass until actually executed successfully in Minecraft on the target stack.

## License

### FACT-LICENSE-001

Top-level repository `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. Current M0.5-M1C source changes do not resolve that provenance mismatch.
