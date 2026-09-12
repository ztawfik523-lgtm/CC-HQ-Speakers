# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality programmable audio while preserving the standard CC:T speaker contract.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline / 21.1.248 compatibility

## Product direction

This mod is a **programmable audio peripheral**, not a prebuilt music player. Lua decides whether audio is music, speech, an alarm, a notification, ambience, a soundboard entry, or something else. Java distinguishes sources only where their technical capabilities differ.

### Standard CC:T speaker

The normal `computercraft:speaker` remains the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` behavior is a compatibility requirement.

### HQ raw/feed audio

HQ `speakPCM` is an open-ended producer feed with bounded backpressure. It does not pretend to have finite duration or arbitrary seek.

### Finite media

Core finite product target:

- MP3 / MPEG Layer III
- common WAV

Wanted but separately gated:

- normal native FLAC, only after its exact analyzer/decoder/seek/package path is proven

Finite files have truthful server-owned duration, position, pause/resume, seek, loop, volume, and EOF.

The final architecture **streams finite encoded data progressively from the server to relevant clients**. Clients do not need the whole file before playback and do not maintain a persistent song cache. They keep only bounded temporary encoded/decoded RAM for active playback.

One physical speaker renders one mono positional source. Mono input stays mono; stereo input is downmixed to mono; more-than-stereo finite input is rejected.

OGG Vorbis, AIFF/AIF, AU/SND, Ogg-FLAC, and unusual WAV encodings are not final finite product requirements. Frozen M1D historically contains analysis support for some of those formats, but the replacement streaming engine is intentionally narrower.

### Live network streams

Live MP3/HLS/TS remain later work. Live sources are open-ended and must not expose fake finite duration/seek. Future live pause/resume means reconnecting to the current live point rather than preserving old stream history.

## Current development status

Frozen M1D source/test/CI head:

`4a2cd5de96228fc091226c7e72fb669b82be258c`

M1E exact code-bearing head:

`d0e66ab9135359627086c13647d5241ad778643f`

Active branch:

`codex/m1e-server-authoritative-finite`

M1E is **source/test/CI complete**. GitHub Actions run `34658958488` passed both target NeoForge versions at the exact M1E code-bearing head. Minecraft M1E runtime acceptance is still pending.

M1E finite playback now starts/advances on the server immediately, renderer status no longer owns the server clock/EOF, the no-renderer timeout is gone, and protocol v4 adds an authoritative server -> client finite STATE packet.

The old whole-file sender/client is intentionally still present only as a bridge. The next implementation milestones are:

- **M1F:** client-requested bounded encoded streaming with off-thread server IO and no final client disk cache;
- **M1G:** progressive MP3/common-WAV decoding with bounded RAM and mono output;
- **M1H:** dynamic listeners, late join, leave/re-enter, seek/underrun recovery;
- **M1I:** optional/gated native FLAC extension;
- **M1J:** multispeaker shared clocks with one positional renderer per physical speaker.

For a new chat or implementation handoff, start with `docs/CHAT-HANDOFF.md`, then verify the current branch source and CI before changing anything. The dated `docs/CHAT-HANDOFF-2026-09-12.md` remains as deeper historical context.

## Main finite controls

The capability-oriented control surface includes:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

The bundled `hqspeaker` Lua module also provides prepared/local-file helpers such as `prepareFile`, `playPrepared`, `releasePrepared`, and `playFile`.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

CI targets both supported NeoForge versions with Java 21 and verifies packaged mod metadata, mixins, JarJar metadata/dependencies, and the bundled ComputerCraft ROM module.

## Documentation

Read in this order for continuation work:

1. `docs/CHAT-HANDOFF.md` — canonical standalone continuation handoff
2. `docs/CURRENT-STATE.md` — current implementation truth
3. `docs/VERIFIED-FACTS.md` — source/CI/runtime facts only
4. `docs/ARCHITECTURE.md` — accepted architecture
5. `docs/M1E-SERVER-AUTHORITY.md` — exact completed M1E behavior
6. `docs/M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ streaming contract
7. `docs/ROADMAP.md` — milestone order
8. `docs/KNOWN-ISSUES.md` — unresolved problems
9. `docs/CHAT-HANDOFF-2026-09-12.md` — longer dated deep-context handoff

Historical milestone docs remain evidence for the commits they describe, but they do not override current source or the current accepted architecture.
