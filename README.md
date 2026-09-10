# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality PCM, finite encoded media, Internet streams, metadata, multi-speaker helpers, and richer playback control.

This fork targets:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 / 21.1.248

## Product direction

This mod is a **programmable audio peripheral**, not a prebuilt music player.

Lua decides whether a sound is music, speech, an alarm, a notification, ambience, a soundboard entry, or something else. The Java implementation only distinguishes sources when their technical capabilities differ.

### Raw/feed audio

- `playAudio` — CC:T-compatible signed 8-bit PCM feed
- `speakPCM` — HQ signed 16-bit PCM feed

These are open-ended feeds. They do not have a truthful finite duration or arbitrary seek position.

### Finite media

- `speakMp3`
- `speakOgg`
- `speakWav`
- `speakAudio` / `speakFile` / `speakPacked`

Finite media can support truthful duration, position, seek, loop, pause/resume, stop, natural EOF, and error state.

### Live network streams

- `speakStream`
- `speakHLS`
- `speakTS`

Live sources are open-ended. They should not expose fake finite duration/seek. The intended future pause/resume behavior is reconnect-to-live, not preservation of a historical stream cursor.

## Current development status

The M1 finite-media branch added retained decoded PCM, exact decoded sample rates, generation-aware state/control, real finite EOF, pause/resume, seek, duration/position, looping, and live finite volume control.

However, the current reviewed M1 reference (`fba84a3`) still has source-proven compatibility, lifecycle, raw-feed, multi-client, sync, and stream defects. It should not be treated as a finished release candidate.

See:

- `docs/CURRENT-STATE.md`
- `docs/KNOWN-ISSUES.md`
- `docs/CC-T-COMPATIBILITY-CONTRACT.md`
- `docs/P0-DESIGN-DECISIONS.md`
- `docs/P0-TEST-MATRIX.md`

## API direction

Standard CC:T `speaker` behavior is a hard compatibility requirement:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- `speaker_audio_empty`

HQ extensions add richer formats and controls. The exact public surface should remain capability-oriented rather than application-oriented.

Current M1 finite controls include:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

Group/all/index variants exist for many HQ calls.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

CI builds both supported NeoForge versions with Java 21 and verifies the packaged mod metadata, mixin config, jarjar metadata, MP3SPI, JLayer, and Tritonus dependencies.
