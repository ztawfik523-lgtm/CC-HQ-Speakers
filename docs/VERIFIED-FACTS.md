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

## Packaging facts

### FACT-BUILD-001

Packaged dependencies include JLayer `1.0.1.4`, mp3spi `1.9.5.4`, and Tritonus Share `0.3.7.4`. Modern progressive MP3 decoding uses JLayer.

## CC:T contract facts

### FACT-CCT-001

The exposed peripheral type is `speaker`; standard `playNote`, `playSound`, `playAudio`, and `stop` delegate to CC:T's real speaker behavior. Native `speaker_audio_empty` remains CC:T-owned, while HQ RAW uses `hqspeaker_audio_empty`.

## Asset/import facts

### FACT-ASSET-001

`MediaAssetStore` owns immutable UUID-addressed encoded server files with quotas, reference counting, and seekable reads. CC files use temporary writable staging only to import MediaAssets.

### FACT-ASSET-002

Active prepared/playback/range release paths preserve retry ownership when final release fails. Server shutdown stops/drains range IO before the store closes.

### FACT-ASSET-003

`MediaAssetStore.close()` currently clears completed-entry bookkeeping before shutdown deletion attempts. If one of those deletions fails, a subsequent `close()` has no retained completed-entry list to retry; next-start orphan pruning can remove managed leftovers. This was documented by the 2026-09-14 audit and is not fixed in source.

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

## M1G integrated source facts

### FACT-M1G-001

Modern finite protocol version is 6. BEGIN carries an MP3/common-WAV `FiniteDecodeDescriptor`.

### FACT-M1G-002

Modern prepared/local media is narrowed to MP3 or supported common WAV: U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or selected narrow PCM/float WAVEX.

### FACT-M1G-003

STATE anchor selection provides exact frame-aligned WAV anchors and conservative E1 MP3 pre-roll anchors.

### FACT-M1G-004

`FiniteEncodedInputStream` is a decoder-worker-only view over the M1F range window. NEED_DATA waits/refills; true asset EOF alone returns normal EOF; cancellation/stale state aborts the epoch.

### FACT-M1G-005

`FinitePcmQueue` is fixed-capacity mono-S16 storage with producer backpressure and nonblocking renderer states.

### FACT-M1G-006

`ProgressiveWavDecoder` progressively converts supported WAV to mono S16 without whole-track PCM retention.

### FACT-M1G-007

`ProgressiveMp3Decoder` uses packaged JLayer frame-by-frame, validates analyzed rate/channel facts, decodes from earlier E1 anchors, and discards pre-target PCM.

### FACT-M1G-008

`HQFiniteMediaClient` owns local decode epochs; seek/replacement/stop invalidate old encoded waits, decode work, PCM, and renderer state. Range arrivals wake the active encoded input.

### FACT-M1G-009

`FinitePcmAudioStream` performs no network/disk/codec work and represents temporary PCM starvation with short bounded silence instead of terminal EOF.

### FACT-M1G-010

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, positional linear attenuation, and one source per physical speaker.

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

The 2026-09-14 audit updated documentation only; no implementation/test-script fix was made.

## Current unresolved decision

### FACT-M1G-NEXT-001

Loop-wrap client rejoin is not implemented. Owner choice remains L1 client EOF refresh, L2 proactive server wrap STATE, or L3 client local modulo/restart.

## Later milestone facts

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H. Native FLAC remains gated M1I work. Inherited legacy finite/live/multispeaker code remains for later migration/removal.

## License

### FACT-LICENSE-001

Top-level `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0. The provenance mismatch remains unresolved.
