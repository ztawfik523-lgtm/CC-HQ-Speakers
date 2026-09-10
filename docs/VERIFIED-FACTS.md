# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

## Repository/platform

### FACT-REPO-001

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Untouched fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

Reviewed M1 reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility

Build dependency:

`cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`

### FACT-PLATFORM-002

At reviewed M1 HEAD, GitHub Actions completed successfully for both NeoForge 21.1.247 and 21.1.248. The workflow runs `clean build` on Java 21 and verifies mod metadata, mixin config, jarjar metadata, mp3spi, JLayer, and Tritonus artifacts.

### FACT-PLATFORM-003

The network registrar at reviewed M1 HEAD registers five payload types:

- audio
- stop
- finite/player control
- ICY metadata
- finite/player status

## CC:T 1.120.0 base speaker contract

Source basis: official CC:Tweaked 1.120.0 for Minecraft 1.21.1, release tag `v1.21.1-1.120.0` (release commit `98f3a71`), plus official speaker documentation.

### FACT-CCT-001

The normal peripheral type is `speaker`.

### FACT-CCT-002

`playNote(instrument [, volume [, pitch]])` accepts optional volume/pitch, resolves a real note-block instrument, validates the instrument, and is subject to the configured per-tick note limit.

The official documentation says omitted pitch defaults to `12`, while the exact `v1.21.1-1.120.0` source uses `pitchA.orElse(1.0)`. This is an upstream source/documentation discrepancy and is not silently reconciled here.

### FACT-CCT-003

`playSound(name [, volume [, pitch]])` resolves a Minecraft/modded sound identifier, accepts optional volume/pitch, and returns false when a sound/audio conflict prevents playback.

### FACT-CCT-004

`playAudio(audio [, volume])` accepts signed 8-bit samples, maximum 128*1024 samples per call, at 48 kHz. It buffers one pending call at a time and returns false when it cannot accept another buffer.

If volume is omitted, the documented behavior is to reuse the previous `playAudio` volume.

### FACT-CCT-005

`speaker_audio_empty` is used as backpressure notification after the internal audio buffer is pulled/freed so another `playAudio` call can be accepted.

### FACT-CCT-006

`stop()` is a standard speaker method. It stops/clears the speaker's current playAudio/latest playSound state.

## Current HQ replacement facts

### FACT-HQ-001

`ComputerCraftSpeakerBlockEntityMixin` replaces the normal CC:T speaker peripheral with `HQSpeakerPeripheral`.

`HQSpeakerPeripheral.getType()` returns `speaker`.

### FACT-HQ-002

Current `playNote` ignores its `instrument` argument and synthesizes a sine wave into the HQ PCM queue.

Its Java method takes primitive volume and pitch arguments rather than optional arguments.

### FACT-HQ-003

Current `playSound` ignores `soundName` and delegates to a generated harp/sine-note path.

### FACT-HQ-004

Current HQ source exposes `speakStop()` and `audioStop()`, but no standard Lua `stop()` method in `HQSpeakerPeripheral`.

### FACT-HQ-005

`playAudio` preserves signed 8-bit input conversion; `speakPCM` accepts signed 16-bit input. Both become `PCM_S16LE`.

### FACT-HQ-006

Current raw/HQ server queue is a bounded queue of 16 `SpeakerChunk`s. `speakerTick()` polls one chunk per server tick.

`speaker_audio_empty` is currently scheduled whenever that queue is below `SPEAKER_READY_MARK`, not when an actual client raw buffer has been consumed.

### FACT-HQ-007

Finite encoded media is capped at 8 MiB in both peripheral and audio packet code and is transported as a whole encoded `byte[]`.

### FACT-HQ-008

`HQAudioStream` uses a single-thread `HQSpeaker-Decoder` executor and whole-file finite decode.

OGG uses `STBVorbis.stb_vorbis_decode_memory`; JavaSound-supported finite media uses `readAllBytes()` after conversion.

Decoded finite PCM has a 64 MiB post-decode validation cap.

## M1 finite facts

### FACT-M1-001

Finite packets carry:

- generation
- finite looping state
- finite paused state

### FACT-M1-002

`FiniteAudioTrack` retains complete mono signed-16-bit PCM, exact sample rate, and frame-aligned cursor state. Renderer forks share retained PCM with independent cursors.

### FACT-M1-003

Finite controls include PAUSE, RESUME, SEEK, SET_VOLUME, and SET_LOOP.

Finite client status transitions include READY, STARTED, PAUSED, RESUMED, SEEKED, ENDED, and ERROR.

### FACT-M1-004

Finite pause/resume reaches the actual Minecraft `Channel` through client-only accessors of `SoundManager.soundEngine` and `SoundEngine.instanceToChannel`.

### FACT-M1-005

Finite loop uses retained cursor rewind and does not use Minecraft `SoundInstance.looping`.

### FACT-M1-006

Finite client state currently uses one renderer boundary per logical finite item.

### FACT-M1-007

Server finite state currently uses the first successful renderer as `anchorRenderer`. PAUSED/RESUMED/SEEKED/ENDED are anchor-gated.

### FACT-M1-008

Server `promoteLocked(track)` removes all finite tracks before a STARTED track.

## Stream facts

### FACT-STREAM-001

`StreamingAudioSource` has MP3_STREAM, HLS_STREAM, and TS_STREAM paths and a bounded PCM queue.

### FACT-STREAM-002

Stream volume is currently applied inside `StreamingAudioSource.queuePCM()` by scaling PCM samples. The Minecraft `HQSpeakerSound` also uses the packet volume.

### FACT-STREAM-003

Live HLS parsing records `EXT-X-MEDIA-SEQUENCE`, but `StreamingAudioSource.streamHLS()` advances using a persistent `currentSegmentIndex` across refreshed playlists.

### FACT-STREAM-004

Direct TS calls `TSDemuxer.demux(InputStream)` and receives a complete `List<AudioFrame>` before iterating and queueing decoded output.

### FACT-STREAM-005

`decodeAudioFrame` returns the compressed `frame.data` unchanged when JavaSound reports `UnsupportedAudioFileException`.

## Multi-speaker facts

### FACT-SYNC-001

All/group calls may assign a shared future start tick, sync group UUID, and expected group size.

### FACT-SYNC-002

Audio packet delivery is per physical speaker to players within the speaker radius. The expected sync group size is derived from the full server-side member set, not a per-player received subset.

### FACT-SYNC-003

`SharedStreamingGroup` waits for `taps.size() >= expectedTaps` before starting its shared decoder.

## Lifecycle facts

### FACT-LIFE-001

`HQSpeakerPeripheralProvider` cache keys contain dimension resource location and block position. Cached values contain `HQSpeakerPeripheral`, which stores the concrete `Level`.

A `forget(Level, BlockPos)` method exists in the provider.

### FACT-LIFE-002

At reviewed M1 source, no call site to `HQSpeakerPeripheralProvider.forget()` is present in the tracked Java tree.

## Test facts

### FACT-TEST-001

Before P0, the only Java test class was `FiniteAudioTrackTest` with five tests.

### FACT-TEST-002

The M1 Lua helper immediately seeks after disabling loop, so it cannot observe the source-proven loop-disable position clock bug before that seek overwrites the base position.

The helper stops finite playback before exercising raw PCM, so it does not cover finite-to-raw mode interaction.

## License

### FACT-LICENSE-001

Top-level repository LICENSE is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0.

Do not silently relicense; resolve provenance before public release.
