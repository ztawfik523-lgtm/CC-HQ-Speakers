# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality programmable audio while preserving the standard CC:T speaker contract.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline / 21.1.248 compatibility

## Product direction

This mod is a **programmable audio peripheral**, not a built-in music player.

Lua decides whether audio is music, speech, alarms, notifications, ambience, soundboards, or something else. Java distinguishes sources only where technical capabilities differ.

The normal `computercraft:speaker` remains the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain CC:T compatibility requirements.

One physical speaker remains one mono positional source.

## Current development status

M1E server-authoritative finite playback and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

M1G progressive decode/rendering is actively implemented on:

`codex/m1g-progressive-finite-decode`

Current green integrated M1G source checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

Source CI:

`34778546164`

NeoForge 21.1.247 and 21.1.248 both passed build, tests, packaged-mod verification, and artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

Focused real-Minecraft M1F transport acceptance is **not recorded**. Focused audible M1G Minecraft acceptance is also **not recorded**. CI/component proof is not runtime proof.

The current finite architecture is:

```text
ComputerCraft file
    -> reusable server MediaAsset
    -> server-authoritative finite playback state
    -> codec-aware server STATE anchor
    -> bounded client range requests
    -> bounded off-thread server reads
    -> bounded sliding client encoded RAM
    -> starvation-aware decoder-worker input
    -> progressive MP3/common-WAV decoder
    -> bounded mono signed-16 PCM queue at source sample rate
    -> nonblocking Minecraft AudioStream
    -> one positional BLOCKS SoundManager source
```

Read `docs/HANDOFF-2026-09-13-M1G-START.md`, `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`, and `docs/CURRENT-STATE.md` before continuing M1G.

## M1E finite server authority

Prepared finite playback has hardened server-owned semantics:

- successful playback starts canonical time immediately;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position, duration, pause/resume, seek, loop, volume, and natural EOF;
- server ERROR freezes position;
- client READY requests current state only;
- client ERROR is diagnostic only;
- client delivery is best-effort/per-recipient;
- MediaAsset release is retry-safe.

Final focused Minecraft M1E acceptance was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

## M1F finite encoded transport

The finalized M1F range transport uses bounded client-requested ranges rather than whole-song transfer.

Current implementation values:

- max range 128 KiB;
- client encoded window 512 KiB;
- max outstanding requests/player 4;
- max outstanding encoded bytes/player 512 KiB;
- range IO workers 2;
- range IO queue 64.

The client waits for authoritative STATE before first demand, supports arbitrary re-anchor, slides forward while retaining useful unread prefetch, and distinguishes missing data from true EOF/stale state.

The modern prepared path has no complete-song client `.part/.media` cache, no modern CHUNK/END whole-file packets, and no `audioPlayStaged()` route.

## M1G integrated implementation

Locked owner choices:

- **A1:** normal Minecraft `AudioStream` / `SoundManager` renderer;
- **B1:** server-normalized common-WAV layout;
- **C1:** preserve source sample rate;
- **D1:** narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` compatibility;
- **E1:** coarse conservative MP3 pre-roll from an earlier existing seek point.

Current source includes:

- protocol v6 BEGIN descriptor for modern MP3/common WAV;
- normalized narrow common-WAV/WAVEX layout;
- modern prepared/local format gate narrowed to MP3 + supported common WAV;
- exact WAV frame anchors and E1 MP3 pre-roll anchors;
- starvation-aware progressive encoded input over M1F;
- bounded PCM queue with decoder backpressure and nonblocking consumer reads;
- progressive U8/S16/S24/S32/F32 WAV conversion to mono S16 at source rate;
- progressive JLayer MP3 decode with earlier-anchor silent pre-roll and pre-target discard;
- decoder epoch cancellation/replacement on seek/replacement/stop;
- bounded prebuffer/catch-up toward projected authoritative server time;
- Minecraft `FinitePcmAudioStream` + positional `FiniteSpeakerSound` through `SoundManager` / `SoundSource.BLOCKS`;
- pause/resume/volume projection through the Minecraft channel-control path.

The inherited complete-file JavaSound/mp3spi finite bridge is not the modern prepared engine.

## Current architecture gate before more M1G source changes

The next chat must ask the owner to choose **loop-wrap rejoin**:

- **L1 — client EOF refresh:** local EOF during a looping server session requests fresh STATE and restarts from the authoritative anchor.
- **L2 — server wrap STATE:** server detects canonical loop wrap and proactively projects fresh STATE.
- **L3 — client local modulo/restart:** client predicts loop wrap locally and reconciles later.

Do not silently choose among L1/L2/L3. Full pros/cons are in `docs/HANDOFF-2026-09-13-M1G-START.md`.

## Lua finite-file API

Recommended one-call use:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Reusable helpers:

- `prepareFile`
- `preparedInfo`
- `playPrepared`
- `releasePrepared`

Finite controls:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

See `docs/LUA-API.md`.

## Milestone sequence

- **M1E:** server-authoritative finite timeline — source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded.
- **M1F:** bounded client-requested encoded transport — source/test/CI/package + component acceptance complete; focused Minecraft transport acceptance unrecorded.
- **M1G:** progressive MP3/common-WAV decode + positional renderer — **in progress; loop-wrap policy choice pending**.
- **M1H:** dynamic listener/late-join/leave-return/recovery.
- **M1I:** optional gated native FLAC.
- later milestones cover multispeaker sync/sharing, legacy migration, RAW/OpenAL hardening, final testing, SPR, live streams, and release cleanup.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

## Documentation

Read current development docs in this order:

1. `docs/HANDOFF-2026-09-13-M1G-START.md`
2. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
3. `docs/CURRENT-STATE.md`
4. `docs/TESTING.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1F-FINALIZATION-2026-09-13.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current records.