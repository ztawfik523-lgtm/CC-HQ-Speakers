# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

## Repository / platform

### FACT-REPO-001

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`.

Current implementation branch: `codex/m1g-progressive-finite-decode`.

Important checkpoints:

- M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`;
- M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- M1G preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`;
- current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`;
- documentation checkpoint before the final chat-limit handoff refresh: `7ec70d4674b237f055d450e1290a652f7c23b65d`.

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1;
- Java 21;
- CC:Tweaked 1.120.0;
- NeoForge 21.1.247 baseline;
- NeoForge 21.1.248 compatibility.

## CI / package facts

### FACT-CI-001

M1E final hardening CI `34757923455` passed both target NeoForge versions.

M1F final source/test CI `34763362365` passed both target NeoForge versions including build/tests, packaged-mod verification, and artifact upload.

### FACT-CI-002

M1G integrated source checkpoint `957832348eaa6e497282d923f2312c9c7d7c550f` passed CI `34778546164` on both NeoForge 21.1.247 and 21.1.248, including build, tests, packaged-mod verification, and artifact upload.

Artifacts:

- 21.1.247 artifact id `10324148909`, ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact id `10324273482`, ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

### FACT-CI-003

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both NeoForge 21.1.247 and 21.1.248, including build, tests, packaged-mod verification, and artifact upload.

The compare from integrated source checkpoint `957832348eaa6e497282d923f2312c9c7d7c550f` to `7ec70d4674b237f055d450e1290a652f7c23b65d` contains documentation changes only.

CI is not Minecraft runtime proof.

## Packaging / dependency facts

### FACT-BUILD-001

The packaged project uses:

- JLayer `1.0.1.4` / compatibility range `[1.0.1.4,1.0.2)`;
- mp3spi `1.9.5.4` / range `[1.9.5.4,1.9.6)`;
- Tritonus Share `0.3.7.4` / range `[0.3.7.4,0.3.8)`.

JLayer is therefore available to modern finite M1G without introducing a new MP3 dependency.

## CC:T base contract

### FACT-CCT-001

The exposed peripheral type is `speaker`.

### FACT-CCT-002

The composite delegates standard `playNote`, `playSound`, `playAudio`, and `stop` behavior to CC:T's real `SpeakerPeripheral`.

### FACT-CCT-003

Native `speaker_audio_empty` remains owned by standard CC:T `playAudio`. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Server media assets / import

### FACT-ASSET-001

`MediaAssetStore` owns immutable UUID-addressed encoded server files independently of playback. It supports quotas, reference counting, seekable reads, and final-reference deletion.

### FACT-ASSET-002

Logical release failures transfer to `MediaAssetReleaseQueue`; active prepared/playback/range paths retain a retry owner rather than intentionally forgetting a still-live final reference.

### FACT-ASSET-003

Server shutdown clears speaker composites before shared media services close. `ServerMediaAssets.closeServer()` stops/drains range IO before closing the store.

### FACT-ASSET-004

ComputerCraft files use temporary writable staging only to import immutable MediaAssets. Modern Lua exposes `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## M1E facts

### FACT-M1E-001

Canonical finite states are PLAYING, PAUSED, ENDED, and ERROR. Successful prepared playback starts the server clock immediately.

### FACT-M1E-002

Server duration/clock owns natural EOF. Client transfer/decode/render state is not canonical playback authority.

### FACT-M1E-003

Client READY requests fresh state; client ERROR is diagnostic only. Projection failure does not roll back canonical playback.

### FACT-M1E-004

M1E source/test/CI hardening is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final focused Minecraft M1E acceptance was explicitly skipped; no final runtime PASS is recorded.

## M1F transport facts

### FACT-M1F-001

M1F finalized demand-driven finite transport uses bounded range request/data messages rather than whole-file CHUNK/END transfer.

M1G later bumped the overall modern protocol to version 6 to carry the decoder descriptor; this does not restore whole-file transfer.

### FACT-M1F-002

A fresh `FiniteRangeWindow` is unanchored and cannot request bytes until authoritative server STATE supplies an encoded anchor.

### FACT-M1F-003

Current transport tuning remains:

- max range 128 KiB;
- active client encoded window 512 KiB;
- max outstanding requests/player 4;
- max outstanding bytes/player 512 KiB;
- server range IO workers 2;
- range IO queue 64.

These are implementation tuning values, not frozen public API guarantees.

### FACT-M1F-004

Range work is validated by source/asset/generation/bounds/relevance rules, performed on bounded background workers, and stale/replaced/disconnected/out-of-range completions are discarded before send.

### FACT-M1F-005

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE, supports arbitrary re-anchor, and supports forward sliding which discards consumed prefix data while preserving useful unread overlap under a fixed memory cap.

### FACT-M1F-006

Modern prepared transport does not create a complete client song `.part/.media` file and does not use modern CHUNK/END packet classes or `audioPlayStaged()`.

### FACT-M1F-007

M1F source/test/CI/package and deterministic/component completion is recorded at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`.

No focused real-Minecraft M1F transport PASS is recorded.

## M1G locked architecture facts

### FACT-M1G-ARCH-001

The owner selected:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout carried to clients;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1 — coarse conservative MP3 pre-roll from an earlier analyzed seek point.

Modern decoded output representation is mono signed 16-bit PCM at source sample rate.

### FACT-M1G-ARCH-002

One physical speaker remains one mono positional source. Application meaning such as music/effect/alarm/notification remains Lua policy rather than Java source roles.

## M1G integrated source facts

### FACT-M1G-001

Modern finite protocol version is 6. BEGIN carries a decoder-facing MP3/common-WAV `FiniteDecodeDescriptor` rather than the historical `MP3/OGG/AUDIO_FILE` ambiguity.

### FACT-M1G-002

New prepared/local media is narrowed to MP3 or the supported common-WAV subset.

Common WAV supports mono/stereo:

- U8 PCM;
- S16 PCM;
- S24 PCM;
- S32 PCM;
- F32 IEEE float;
- classic RIFF/WAVE;
- narrow `WAVE_FORMAT_EXTENSIBLE` PCM/float with `validBits == containerBits`.

Surround, compressed/telephony/companded WAV, float64, unusual widths, and differing valid/container widths are rejected by the modern gate.

### FACT-M1G-003

`FiniteDecodeAnchorSelector` supplies exact frame-aligned WAV anchors and conservative E1 MP3 pre-roll anchors from an earlier analyzed seek point.

### FACT-M1G-004

`FiniteEncodedInputStream` is a decoder-worker-only view over the M1F window. Temporary NEED_DATA waits/refills on the worker rather than returning EOF. True asset EOF alone returns normal stream EOF; cancellation/stale state aborts the decoder epoch.

### FACT-M1G-005

`FinitePcmQueue` is fixed-capacity mono-S16 storage. Decoder writes backpressure when full; renderer-facing reads are nonblocking and distinguish DATA, STARVED, EOF, and CANCELLED.

### FACT-M1G-006

`ProgressiveWavDecoder` converts supported common-WAV representations progressively to mono S16 at the original source sample rate without retaining whole-track PCM.

### FACT-M1G-007

`ProgressiveMp3Decoder` uses the packaged JLayer dependency progressively, validates decoded sample rate/channel facts against analyzed metadata, decodes from the earlier E1 anchor, and discards pre-target PCM before audible output.

### FACT-M1G-008

`HQFiniteMediaClient` owns local decoder epochs. Seek/replacement/stop invalidates stale encoded waits, decoder work, PCM, and renderer state. Accepted range arrivals wake the active encoded input.

### FACT-M1G-009

Before renderer start, bounded queued PCM can be discarded forward toward the projected authoritative server time so decoder/prebuffer delay does not become permanent audible lag.

### FACT-M1G-010

`FinitePcmAudioStream` reads the bounded PCM queue without performing network/disk/codec work. Temporary starvation produces short bounded silence instead of terminal EOF.

### FACT-M1G-011

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation, and one source per physical speaker. Pause/resume and volume projection use the existing Minecraft channel-control path.

### FACT-M1G-012

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared finite engine.

### FACT-M1G-013

Current integrated source/tests/package checkpoint is `957832348eaa6e497282d923f2312c9c7d7c550f` / CI `34778546164`, green on both target NeoForge versions.

No focused audible Minecraft M1G PASS is recorded.

## Current unresolved fact / decision boundary

### FACT-M1G-NEXT-001

Loop-wrap rejoin is not yet implemented because the architecture choice remains open.

The documented owner options are:

- L1 — client EOF refresh;
- L2 — server wrap STATE;
- L3 — client local modulo/restart.

The next implementation chat must ask the owner to choose before implementing loop-wrap behavior.

## Later milestone facts

### FACT-NEXT-001

Full dynamic listener late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H.

### FACT-NEXT-002

Native FLAC remains gated M1I work.

### FACT-NEXT-003

Inherited legacy finite/live/multispeaker code remains for later migration/removal milestones.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. This provenance mismatch remains unresolved.