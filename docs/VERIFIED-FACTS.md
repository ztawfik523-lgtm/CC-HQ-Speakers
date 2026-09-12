# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

When behavior changed after the reviewed M1 reference or a frozen milestone, historical facts are explicitly scoped instead of being called current.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

References:

- untouched fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`;
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- completed M0.5 preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- completed M1B asset-store foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- verified M1C + server-storage-config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- M1E semantic implementation checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`;
- pre-preparation M1E diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`;
- current implementation branch: `codex/m1e-server-authoritative-finite`.

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

The exact M1C + server-storage-config base `33bcc6e04a2734500b7b15b84bee884562539216` also completed both target-version jobs successfully after a pure-HLS-test dependency was corrected and the old prototype packet's hard-coded 512 MiB policy check was removed.

The final frozen M1D head `4a2cd5de96228fc091226c7e72fb669b82be258c` completed GitHub Actions run `34635484316` successfully. The workflow head SHA exactly matches that frozen M1D commit. Both target NeoForge jobs completed successfully with build/tests, package verification, and candidate-JAR upload.

The M1E semantic implementation checkpoint `d0e66ab9135359627086c13647d5241ad778643f` completed GitHub Actions run `34658958488` successfully on both NeoForge 21.1.247 and 21.1.248. This is source/test/package evidence for the M1E authority implementation, not Minecraft runtime proof.

The later diagnostic Java head `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7` completed GitHub Actions run `34686003774` successfully on both NeoForge 21.1.247 and 21.1.248. Those later Java changes add runtime diagnostics; they do not make M1E a Minecraft-runtime PASS.

### FACT-PLATFORM-003

Current protocol version is `4`. `HQSpeakerNetwork` registers eleven custom payload types, including the M1E server-to-client `HQFiniteMediaStatePacket`. The transitional finite begin/chunk/end/control family still exists; client-pulled asset ranges are M1F work.

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

The inherited HQ stop helper remains radius-local; dynamic leave/re-enter renderer ownership remains later M1H work.

## M1B reusable media-asset facts

### FACT-M1B-001

`MediaAssetStore` owns UUID-named encoded files independently of physical speakers. The store, not a speaker, owns the filesystem path.

### FACT-M1B-002

`MediaAssetStore.importAsset` reserves declared bytes before copying, writes a UUID `.part` using a bounded 64 KiB direct buffer, rejects short/long sources, forces the completed file, atomically renames it to `.media`, and only then publishes the asset.

### FACT-M1B-003

A successful import starts with one reference. `retain` adds a reference; `release` removes one. Final release deletes the `.media` file and reduces committed-byte accounting. If deletion fails, bookkeeping remains instead of pretending the bytes were freed.

### FACT-M1B-004

The store separately tracks committed and reserved bytes, enforces caller-supplied per-asset and total limits, and reserves before copying so concurrent imports cannot overcommit the configured total.

### FACT-M1B-005

The store prunes UUID-named `.part`/`.media` process leftovers on construction and uses an OS file lock so a second live store cannot prune the first store's directory.

### FACT-M1B-006

If shutdown races an active import, the root lock stays held until the unpublished import cleans up. Close cleanup is retryable.

### FACT-M1B-007

`openRead(assetId)` returns a seekable encoded-file channel for a live asset.

## M1C local-import and server-storage facts

### FACT-M1C-001

`HQMediaStaging` owns the ComputerCraft writable staging mount. `HQFiniteMediaServer` no longer owns or mounts staging storage.

### FACT-M1C-002

`ServerMediaAssets` keeps one shared `MediaAssetStore` per running `MinecraftServer`, under the server/world root `hqspeaker/media-assets` directory.

### FACT-M1C-003

HQ Speaker registers NeoForge `SERVER` configuration for its own media storage only:

- `mediaStorage.maxAssetMiB`, default `512`;
- `mediaStorage.maxTotalMiB`, default `2048`.

Both are world/server-restart settings. A value of `0` disables that HQ Speaker-specific quota by using an effectively unbounded internal limit. These settings do not change ComputerCraft filesystem capacity.

### FACT-M1C-004

The temporary writable staging mount follows the same per-asset policy as the prepared-media store. For the `0`/unlimited policy, HQ Speaker clamps the staging capacity to `Long.MAX_VALUE - MountConstants.MINIMUM_FILE_SIZE` before calling CC:T because `WritableFileMount` internally adds that accounting overhead to the supplied capacity. This avoids signed-long overflow without creating a practical hidden quota.

### FACT-M1C-005

The old staged-finite begin packet no longer imposes its previous hard-coded 512 MiB policy check; it validates positive wire size while server-side config/store enforce file-size policy.

### FACT-M1C-006

The composite exposes `audioPrepareStaged`, `audioPlayPrepared`, and `audioReleasePrepared`. Prepared ownership is tracked by ComputerCraft computer ID.

### FACT-M1C-007

Detaching a preparing ComputerCraft computer releases prepared references still owned through that speaker. `audioReleasePrepared` only releases a prepared reference owned by that calling computer ID.

### FACT-M1C-008

Prepared playback retains a separate playback reference. A prepared asset UUID may be passed to another speaker on the same server because media identity is server-wide.

### FACT-M1C-009

The bundled `hqspeaker.lua` provides `prepareFile`, `playPrepared`, `releasePrepared`, and `playFile`. The helper checks source size before copy, removes partial staging when copy fails, and retries staged cleanup after successful prepare.

### FACT-M1C-010

Once asset import succeeds, failure to remove the temporary staging file does not invalidate the shared asset.

### FACT-M1C-011

On server shutdown, speaker/prepared ownership is cleaned before shared media-store close. Failed store close remains reachable so cleanup may be retried.

## M1D server media-analysis facts

### FACT-M1D-001

`FiniteMediaAnalyzer` identifies finite media from encoded bytes rather than filename extension and uses a single 64 KiB read window. It does not decode the complete track to PCM.

### FACT-M1D-002

Frozen M1D's analyzed prepared/local finite set is:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- WAV forms accepted by the M1D JavaSound-parity checks;
- uncompressed AIFF/AIF within the JavaSound reader's supported shape;
- AU/SND encodings supported by the JavaSound reader.

OGG using another codec such as Opus and compressed AIFC are explicitly rejected by M1D. This historical set does not define the final narrowed product scope.

### FACT-M1D-003

`MediaMetadata` records actual format, positive duration, sample rate, channel count, bits-per-sample where meaningful, and coarse encoded-file seek hints.

MP3/OGG seek metadata is bounded to at most 4096 points per asset. The index self-thins by retaining every other point and doubling its interval when necessary.

### FACT-M1D-004

MP3 analysis skips an ID3v2 prefix, scans for MPEG Layer III frame sync, walks frame headers, derives duration from encoded frame sample counts, and records bounded coarse byte offsets. It supports MPEG-1/2/2.5 Layer III timing and rejects a stream which changes sample rate or channel layout mid-stream.

M1D MP3 duration is encoded-frame duration; encoder-delay/padding correction is not yet implemented.

### FACT-M1D-005

OGG Vorbis analysis requires a complete 30-byte Vorbis identification header and validates version, channels, sample rate, block-size exponents, and framing bit. It walks Ogg pages to the final granule position for duration, records bounded coarse page offsets, and rejects chained Vorbis logical streams.

### FACT-M1D-006

WAV analysis follows the first `data` chunk after `fmt `, validates block alignment against sample width/channel count, limits IEEE floating PCM to 32/64-bit, and derives duration from complete frames.

AIFF analysis reads `COMM`/`SSND`, limits samples to 1–32 bits, rejects non-zero SSND offsets to match the current JavaSound reader behavior, and verifies the declared frame count fits available sound bytes.

AU analysis accepts the JavaSound reader's current mu-law/A-law, signed linear PCM, float, and double encodings and derives duration from complete frames.

### FACT-M1D-007

The supported prepared-file path first imports exact staging bytes into an immutable server asset, then analyzes that committed copy. The asset UUID is returned to Lua only after metadata attaches successfully. If analysis fails, the temporary asset reference is released instead of returning a usable asset.

### FACT-M1D-008

The composite exposes `audioPreparedInfo(assetId)` and the bundled Lua module exposes `preparedInfo(speaker, assetId)`. Prepared playback status uses the same server-derived format/duration/sample-rate/channel facts immediately.

### FACT-M1D-009

Frozen M1D's exposed `speakSupportedFiles()` list is `wav`, `ogg`, `mp3`, `aiff`, `aif`, `au`, and `snd`. MP2, MP4, M4A, and AAC were removed from advertisement without exact finite-decoder evidence. Later M1G product-scope narrowing is separate from this historical fact.

### FACT-M1D-010

The analyzer converts numeric/container overflows into checked analysis failures and bounds repeated zero-read/no-progress behavior instead of spinning indefinitely.

### FACT-M1D-011

At frozen M1D head, the transitional finite sender used fixed recipients and client STARTED/ENDED-style authority. M1D changed file truth/duration source, not that transport/state architecture. M1E subsequently removed renderer authority; fixed recipients/whole-file transfer remain until M1F.

## M1E server-authority facts

### FACT-M1E-001

Current `HQFiniteMediaServer` semantic states are `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`. There is no server `LOADING` state for client readiness. Session construction sets known duration, starts `FinitePlaybackClock` immediately, and enters `PLAYING`.

### FACT-M1E-002

`successfulRenderers`, canonical `observed`, and the 15-second no-renderer timeout are removed. Playback time/EOF no longer depend on a renderer appearing.

### FACT-M1E-003

`FinitePlaybackClock.reachedEnd(now)` provides deterministic non-looping EOF detection. Server tick/status/control paths can finalize known-duration EOF. Canonical natural EOF finishes the clock, enters ENDED, closes the transitional transfer, then releases the playback asset reference.

### FACT-M1E-004

Non-looping `seek(duration)` immediately enters ENDED. Looping exact-duration seek wraps to 0 through the clock's looping normalization.

### FACT-M1E-005

Protocol v4 adds `HQFiniteMediaStatePacket`. The packet contains source/media/generation, semantic state, canonical position, duration, volume, looping, and optional error detail. Immutable setup such as format, total encoded size, and speaker position remains in the transitional BEGIN packet rather than being duplicated in every STATE packet.

### FACT-M1E-006

Current `HQFiniteMediaStatusPacket.Transition` contains only READY and ERROR. The prototype renderer-authority transitions STARTED, PAUSED, RESUMED, SEEKED, and ENDED are removed. READY requests fresh server state; ERROR is diagnostic and does not become canonical playback failure.

### FACT-M1E-007

The transitional M1E client still receives/writes the complete encoded file before constructing `FileFiniteAudioStream`, but it no longer starts at 0 when transfer ends. It reports READY and waits for a fresh authoritative STATE, then attempts to start/seek from the current server position or remains paused/destroys itself according to server state.

### FACT-M1E-008

The client's `FinitePlaybackClock` remains as a local renderer projection, not canonical playback authority. Natural local decoder end does not send a canonical ENDED transition to the server.

### FACT-M1E-009

The 2026-09-12 diagnostic runtime on the target NeoForge/CC:T stack repeatedly reached complete old-file transfer, JavaSound/mp3spi decoder open, READY, fresh authoritative STATE, renderer submission, and then a first PCM read which returned no data. The client subsequently reported diagnostic ERROR while the server continued authoritative state/control traffic.

The captured logs did not contain the ComputerCraft terminal line `M1E server-authority contract passed`, so that diagnostic run is not a recorded M1E runtime PASS.

### FACT-M1E-010

For the tested MP3, the server analyzer reported `161.304` seconds while the JavaSound/mp3spi bridge reported `322.584` seconds. Client JavaSound/mp3spi duration is therefore not the canonical duration source; the M1E server timeline continues to use server `MediaMetadata` duration.

### FACT-M1E-011

Current `FileFiniteAudioStream.JavaSoundDecoder.seek()` calculates the desired skip using decoded PCM frame/byte quantities and then calls the converted stream's `skip(long)`. The shipped mp3spi `DecodedMpegAudioInputStream.skip(long)` instead estimates MPEG frames from the compressed stream length and returns compressed bytes skipped. The current helper also returns the requested target after an early EOF break and clears `ended`, so its returned position does not prove the decoder physically reached the requested position.

Current `HQFiniteMediaClient` renderer restart also performs a seek in `restartStreamOnly()` and then seeks the same stream again in `startRenderer()`.

## Frozen/prototype and transitional transport facts

### FACT-PROTO-001

The frozen prototype proved writable ComputerCraft staging, 256 KiB chunked client transfer, disk-backed client encoded files, and file-backed incremental decoded reading.

### FACT-PROTO-002

Current M1E transport still uses fixed recipients and transitional begin/chunk/end/control packets. The server still pushes the whole encoded asset and reads its transfer channel from server tick code. M1F replaces this transport.

### FACT-PROTO-003

The renderer-authority status behavior was a frozen M1D/prototype fact and is no longer current after M1E. Current client->server finite status is READY/ERROR only.

## Retained finite/stream/multispeaker facts

### FACT-FINITE-001

`FiniteAudioTrack` retains complete mono signed-16-bit PCM with frame-aligned cursor state. Legacy finite decode/control code remains until later migration even though the staged/prepared M1E server semantics are now authoritative.

### FACT-STREAM-001

`StreamingAudioSource` still has MP3_STREAM, HLS_STREAM, and TS_STREAM paths. Known retained issues include double volume application, HLS progression based on persistent segment index, whole-list TS demux, and an unsupported-decode path which may return compressed frame bytes unchanged.

### FACT-SYNC-001

Inherited multi-speaker All/At behavior still uses expected group/tap concepts. `SharedStreamingGroup` waits for expected tap count before shared decode.

## Lifecycle facts

### FACT-LIFE-001

The provider cache is explicitly cleared on speaker removal, server Level unload, and server shutdown; weak Level keys are only a fallback because cached values retain their Level.

### FACT-LIFE-002

Speaker/prepared ownership is cleaned before shared media-store close on `ServerStoppedEvent`.

## Test facts

### FACT-TEST-001

Current pure Java tests include retained finite/HLS/path/clock tests, `RawFeedLifetimeTest`, `MediaAssetStoreTest`, `FiniteMediaAnalyzerTest`, and `MediaStorageLimitsTest`.

`FinitePlaybackClockTest` includes explicit natural-end tests: non-looping reaches known duration, while looping never reports natural EOF. Existing exact-end seek, loop-rebase, and pause/resume cases remain.

`FiniteMediaAnalyzerTest` uses synthetic container/frame structures for WAV, AIFF, AU, OGG Vorbis, and MP3. It covers ID3v2 handling, non-Vorbis/truncated OGG rejection, WAV first-data/block-alignment/float-width behavior, AIFF width/offset/truncation behavior, bounded seek metadata, JavaSound conversion parity for accepted PCM fixtures, and channel reset after success/failure.

`MediaStorageLimitsTest` covers normal staging capacity and the overflow-safe unlimited staging sentinel.

### FACT-TEST-002

`scripts/p0_cc_speaker_contract.lua`, `scripts/m1a_output_contract.lua`, `scripts/m1c_local_import_test.lua`, `scripts/m1d_media_analysis_test.lua`, and `scripts/m1e_server_authority_test.lua` exist as runtime contracts for their respective milestones.

The M1D script includes frozen-M1D renderer-observation expectations. The M1E script instead checks immediate server-authoritative progression, pause/resume, non-looping exact-end EOF, and looping exact-end wrap.

None of these should be reported as a runtime pass until actually executed successfully in Minecraft on the target stack.

## License

### FACT-LICENSE-001

Top-level repository `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. Current M0.5-M1E source changes do not resolve that provenance mismatch.
