# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

Current finalized M1F source/test facts use candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`. Pre-M1G source-audit facts use the unchanged source under documentation head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`.

## Repository/platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current preparation branch: `codex/m1g-preparation`.

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- pre-finalization M1F transport checkpoint: `934e74b8ff619178d703f73df8a16ee97b3fc2af`;
- M1F sliding/validation pass: `80d4fd983a7595101d5c8b8fa26011c6e79cd880`;
- M1F validation/shutdown hardening: `3654a6018d50469a1e8f0a3d19543ae6a7ab8fcd`;
- M1F in-place sliding refinement: `c579ddf3589a448d80e62df584358d5053a29e1d`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- finalized-M1F documentation head / M1G preparation base: `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1;
- Java 21;
- CC:Tweaked 1.120.0;
- NeoForge 21.1.247 baseline;
- NeoForge 21.1.248 compatibility.

## CI/package facts

### FACT-CI-001

M1E final hardening CI `34757923455` passed both target NeoForge versions.

M1F final source/test CI `34763362365` passed both target NeoForge versions including build/tests, packaged-mod verification, and artifact upload.

M1F 21.1.247 final source/test artifact:

- artifact id `10319592968`;
- artifact ZIP SHA-256 `b971f027cad9c22265e3080f17725859fa447ce71cd1d2d0a14f2d1710b1c131`;
- JAR `hqspeaker-1.1.4-1.21.1-neoforge.jar`;
- JAR SHA-256 `2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`.

M1F 21.1.248 final source/test artifact:

- artifact id `10319563040`;
- artifact ZIP SHA-256 `131a250297ec3570c1ed1e0c61cc1d6b7569dff8ecc86d80080cb1ad345ff062`.

CI is not Minecraft runtime proof.

### FACT-CI-002

Documentation-head run `34763711105` for head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` completed successfully on both NeoForge 21.1.247 and 21.1.248.

At the owner's request it was run again. Fresh rerun jobs:

- `103742611713` — NeoForge 21.1.247 — success;
- `103742612481` — NeoForge 21.1.248 — success.

Both fresh rerun jobs passed build, tests, package verification, and artifact upload.

### FACT-CI-003

The first M1F finalization attempt `34762952499` failed only in test compilation because a new test directly referenced a Minecraft packet superclass unavailable on the pure test classpath. Production `compileJava` succeeded. The range sanity rule was moved into a pure validator used by packet/server paths; subsequent finalization runs passed.

## Packaging/dependency facts

### FACT-BUILD-001

The packaged project uses:

- JLayer `1.0.1.4` / compatibility range `[1.0.1.4,1.0.2)`;
- mp3spi `1.9.5.4` / range `[1.9.5.4,1.9.6)`;
- Tritonus Share `0.3.7.4` / range `[0.3.7.4,0.3.8)`.

JLayer is therefore already available to M1G without introducing a new MP3 dependency.

## CC:T base contract

### FACT-CCT-001

The exposed peripheral type is `speaker`.

### FACT-CCT-002

The composite delegates standard `playNote`, `playSound`, `playAudio`, and `stop` behavior to CC:T's real `SpeakerPeripheral`.

### FACT-CCT-003

Native `speaker_audio_empty` remains owned by standard CC:T `playAudio`. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Server media assets/import

### FACT-ASSET-001

`MediaAssetStore` owns immutable UUID-addressed encoded server files independently of playback. It supports quotas, reference counting, seekable reads, and final-reference deletion.

### FACT-ASSET-002

Logical release failures transfer to `MediaAssetReleaseQueue`. Active prepared/playback/range paths do not intentionally log-and-forget a still-live final reference.

### FACT-ASSET-003

Server shutdown clears speaker composites before closing shared media services. `ServerMediaAssets.closeServer()` stops/drains range IO before closing the store.

### FACT-ASSET-004

ComputerCraft files use temporary writable staging only to import immutable MediaAssets. The modern Lua module exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## M1E facts

### FACT-M1E-001

Canonical finite states are PLAYING, PAUSED, ENDED, and ERROR. Successful prepared playback starts the server clock immediately.

### FACT-M1E-002

Server duration/clock owns natural EOF. Client transfer/decode/render state is not canonical playback authority.

### FACT-M1E-003

Client finite READY requests fresh state; client ERROR is diagnostic only. Projection failure does not roll back canonical playback.

### FACT-M1E-004

M1E source/test/CI hardening is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final focused Minecraft M1E acceptance was explicitly skipped; no final runtime PASS is recorded.

## M1F transport facts

### FACT-M1F-001

HQ protocol version is `5`. Modern finite transport uses bounded range request/data messages rather than whole-file CHUNK/END transfer.

### FACT-M1F-002

`HQFiniteMediaStatePacket` carries server-selected `anchorOffset` and `anchorTime`. A fresh `FiniteRangeWindow` is unanchored and cannot request bytes until authoritative state installs an anchor.

### FACT-M1F-003

Current M1F tuning is:

- max range 128 KiB;
- active client encoded window 512 KiB;
- max outstanding requests/player 4;
- max outstanding bytes/player 512 KiB;
- server range IO workers 2;
- range IO queue 64.

These are implementation tuning values, not frozen API guarantees.

### FACT-M1F-004

`FiniteRangeValidation` is the shared pure identity/bounds/relevance rule used by the packet/server range path.

### FACT-M1F-005

Accepted range work retains the MediaAsset before queueing and performs seek/read on a dedicated background worker. Temporary lease release uses the shared retry-safe release owner.

### FACT-M1F-006

Before range data is sent, the current server session generation/asset and current player UUID/relevance are rechecked. Stale/replaced/disconnected/out-of-range completions are discarded.

### FACT-M1F-007

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

It supports arbitrary reset/re-anchor and forward `advanceTo(...)` sliding. Forward sliding discards consumed prefix bytes, preserves useful unread overlap, and keeps bounded memory.

### FACT-M1F-008

`FiniteRangeTransportTest` moves bytes from a real 2 MiB server MediaAsset through asynchronous exact range reads into the bounded client window, slides/refills beyond one full window, then re-anchors to a distant offset while preserving exact-byte correctness and bounded memory.

### FACT-M1F-009

Modern `HQFiniteMediaClient` does not create complete-song `.part/.media` files and does not use `FileFiniteAudioStream` as its prepared transport consumer.

### FACT-M1F-010

The current source tree has no modern finite CHUNK/END whole-file packet classes and the composite has no `audioPlayStaged()` command.

### FACT-M1F-011

M1F source/test/CI/package and deterministic/component completion is recorded at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`.

No focused real-Minecraft M1F transport PASS is recorded.

### FACT-M1F-012

M1F intentionally has no modern prepared-file decoder/renderer. `HQFiniteMediaClient` currently owns transport/session/window/anchor state only.

## Pre-M1G exact source facts

### FACT-M1G-PREP-001

`HQFiniteMediaClient.Session` currently contains the BEGIN descriptor, one `FiniteRangeWindow`, anchor offset/time, anchor-ready state, and terminal state. It does not contain a decoder, decoded PCM queue, or sound renderer.

### FACT-M1G-PREP-002

`FileFiniteAudioStream` is backed by a complete local `Path`. Its JavaSound branch opens/decodes that complete file and its inherited Vorbis branch also uses a complete local file. It is not the modern M1G input model.

### FACT-M1G-PREP-003

`HQAudioStream` finite mode decodes a complete finite payload asynchronously into a retained `FiniteAudioTrack`. `FiniteAudioTrack` retains the complete mono signed-16 PCM byte array. That decoded memory therefore scales with track duration and does not satisfy the M1G bounded-PCM target.

### FACT-M1G-PREP-004

Inherited live `StreamingAudioSource` already uses the exact packaged JLayer classes frame-by-frame: `Bitstream`, `Decoder`, `Header`, and `SampleBuffer`. It downmixes decoded MP3 channels to mono signed 16-bit little-endian PCM.

This proves dependency/API availability, not the final finite lifecycle architecture.

### FACT-M1G-PREP-005

Current `MediaMetadata` fields are format, duration, sample rate, channels, bits per sample, and seek points. It does not contain a normalized final common-WAV layout descriptor such as data length/frame alignment/sample representation.

### FACT-M1G-PREP-006

Current `FiniteMediaAnalyzer.analyzeWav` accepts historical WAV shapes broader than the final M1G target. Current source accepts channel counts up to 8, PCM format tag 1 with broad bit widths, IEEE float tag 3 including 32/64-bit, and A-law/mu-law tags 6/7.

The final M1G common-WAV target documented by the project is narrower: mono/stereo unsigned 8-bit PCM, signed 16/24/32-bit PCM, and 32-bit IEEE float.

### FACT-M1G-PREP-007

Current `HQFiniteMediaBeginPacket.MediaFormat` is `MP3`, `OGG`, or `AUDIO_FILE`; current BEGIN/state wire metadata does not carry a normalized common-WAV layout descriptor.

### FACT-M1G-PREP-008

Current inherited renderer code proves the project can return a custom `AudioStream` from a positional `AbstractSoundInstance` through Minecraft `SoundManager`, category `SoundSource.BLOCKS`, with linear attenuation. Current `HQSoundChannelControl` can reach the sound's channel handle for pause/unpause.

This is existing capability evidence, not a chosen M1G renderer architecture.

### FACT-M1G-PREP-009

Existing finite/live code normally produces mono signed 16-bit PCM and typically preserves source sample rate. `HQAudioStream.SAMPLE_RATE = 48000` is a fallback/raw constant, not proof that modern finite audio is already defined as 48 kHz.

### FACT-M1G-PREP-010

A semantic seek may require decoder restart/pre-roll even when the server-selected coarse encoded `anchorOffset` remains unchanged. The M1F window can reuse immutable encoded bytes; codec state/audible target is separate local state.

## M1G preparation status

### FACT-M1G-PREP-011

M1G implementation has not started on `codex/m1g-preparation`. Preparation is documentation/source audit only.

Three implementation choices remain intentionally unresolved for owner selection:

- renderer path;
- WAV layout ownership;
- finite sample-rate policy.

## Later facts

### FACT-NEXT-001

Full dynamic listener late-entry/proactive-leave/return-rejoin/reload/robust-underrun lifecycle remains M1H.

### FACT-NEXT-002

Native FLAC remains gated M1I work.

### FACT-NEXT-003

Inherited legacy finite/live/multispeaker code remains for later migration/removal milestones.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved.
