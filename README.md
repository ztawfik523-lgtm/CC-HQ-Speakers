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

HQ `speakPCM` is an open-ended producer feed with bounded backpressure. It does not pretend to have finite duration or arbitrary seek. HQ RAW uses its own `hqspeaker_audio_empty` retry event rather than replacing CC:T's native `speaker_audio_empty` semantics.

### Finite media

Core finite product target:

- MP3 / MPEG Layer III
- common WAV

Wanted but separately gated:

- normal native FLAC, only after its exact analyzer/decoder/seek/package path is proven

Finite files have truthful server-owned duration, position, pause/resume, seek, loop, volume, and EOF.

The target architecture streams finite encoded data progressively from the server to relevant clients. Clients do not need the whole file before playback and do not maintain a persistent song cache. They keep only bounded temporary encoded/decoded RAM for active playback.

One physical speaker renders one mono positional source. Mono input stays mono; stereo input is downmixed to mono; more-than-stereo finite input is rejected.

OGG Vorbis, AIFF/AIF, AU/SND, Ogg-FLAC, and unusual WAV encodings are not final finite product requirements. Frozen M1D historically contains analysis support for some of those formats, but the replacement streaming engine is intentionally narrower.

### Live network streams

Live MP3/HLS/TS remain later work. Live sources are open-ended and must not expose fake finite duration/seek. Future live pause/resume means reconnecting to the current live point rather than preserving old stream history.

## Current development status

Active branch:

`codex/m1e-server-authoritative-finite`

M1E server-authority source/tests/CI are finalized and re-reviewed. The final M1E code/test candidate is:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

GitHub Actions run `34725651930` passed both target NeoForge versions with tests/package verification.

The project owner chose not to perform the final manual M1E Minecraft acceptance run. Therefore M1E is **not recorded as Minecraft-runtime PASS**, even though the project may move on later by explicit decision.

Current checkpoint: **documentation/preparation only. M1F implementation has not started.**

The old whole-file sender/client remains only as a temporary bridge. The next implementation milestones are:

- **M1F:** client-requested bounded encoded streaming with off-thread server IO and no final client disk cache;
- **M1G:** progressive MP3/common-WAV decoding with bounded RAM and mono output;
- **M1H:** dynamic listeners, late join, leave/re-enter, seek/underrun recovery;
- **M1I:** optional/gated native FLAC extension;
- **M1J:** multispeaker shared clocks with one positional renderer per physical speaker.

## Lua quick start

For a ComputerCraft-visible local file, use the bundled module:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

For preload/reuse:

```lua
local asset = hq.prepareFile(speaker, "/music/song.mp3")
local info = hq.preparedInfo(speaker, asset)

assert(hq.playPrepared(speaker, asset, { volume = 0.6 }))
assert(hq.releasePrepared(speaker, asset))
```

See `docs/LUA-API.md` for the full programming reference, including finite controls, status fields, events, raw PCM backpressure, low-level prepared-media calls, and which inherited/prototype APIs should not be used for new programs.

### Prototype API note

`audioPlayStaged()` was introduced by this project's old staged/local-file prototype. It is **not** an original HQ Speakers compatibility API.

Project decision: remove it when M1F implementation begins. New programs should use `hqspeaker.playFile()` or prepare/play/release.

## Main finite controls

The capability-oriented control surface includes:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

The bundled `hqspeaker` Lua module provides `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, and `playFile`.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

CI targets both supported NeoForge versions with Java 21 and verifies packaged mod metadata, mixins, JarJar metadata/dependencies, and the bundled ComputerCraft ROM module.

## Documentation

For a fresh continuation, read in this order:

1. `docs/HANDOFF-2026-09-13-PRE-M1F.md` — current plain-language-first handoff
2. `docs/LUA-API.md` — Lua/ComputerCraft API reference
3. `docs/CURRENT-STATE.md` — current implementation truth
4. `docs/VERIFIED-FACTS.md` — source/CI/runtime facts only
5. `docs/ARCHITECTURE.md` — accepted architecture
6. `docs/M1E-SERVER-AUTHORITY.md` — exact M1E behavior
7. `docs/M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ streaming contract
8. `docs/ROADMAP.md` — milestone order
9. `docs/KNOWN-ISSUES.md` — unresolved problems
10. `docs/TESTING.md` — evidence/testing rules
11. `docs/FUTURE-CLEANUP.md` — parked cleanup inventory

Older handoffs and milestone docs remain evidence/history, but they do not override current source, current CI, or the current handoff.