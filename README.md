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

M1E through M1H are complete at source/test/CI/package level. Focused M1H Minecraft listener/recovery/movement checks are deferred for now.

**M1J modern finite multispeaker is source/test/CI/package complete; focused Minecraft multispeaker acceptance is deferred.** First implementation checkpoint:

- source: `b557773b9c6f6b8029aec132a1706f0d8da914bd`;
- CI: `35466635285`;
- NeoForge 21.1.247 and 21.1.248 both passed build, deterministic tests, packaged-mod verification, and artifact upload.

Current finite implementation:

```text
ComputerCraft file
-> reusable server MediaAsset
-> one server-authoritative shared playback
-> protocol v9; shared playbackId + stateRevision + decodeRevision + codec-aware anchor
-> independent physical speaker endpoints
-> bounded per-endpoint client range/decode/render path
-> one shared client-projected timeline per playback
-> one positional BLOCKS SoundManager source per physical speaker
```

A multispeaker start snapshots the speakers currently attached to the calling ComputerCraft computer. There is no expected-member barrier. Removing/replacing one endpoint does not stop the remaining endpoints.

Shared controls are play/pause/resume/seek/loop/stop. Volume and mute are endpoint-local; explicit `*All` and `*At` controls apply them to selected endpoints.

The inherited complete-file finite bridge and finite expected-member barrier have been removed. The remaining legacy audio path is RAW/live-only.

## Completed M1G contract

Locked media/renderer choices remain A1 Minecraft `AudioStream`/SoundManager, B1 server-normalized common-WAV layout, C1 source-rate preservation, D1 narrow PCM/float WAVEX, and E1 conservative MP3 pre-roll.

M1G now implements:

- explicit server-authoritative decoder/re-anchor revision in protocol v7;
- semantic seek increments revision while ordinary STATE preserves healthy decoder/window state;
- stale local worker identity is invalidated before cancellation;
- STATE is the sole nonterminal transition authority; explicit STOP remains;
- fixed **32-block** modern-finite core delivery and attenuation radius;
- HQ volume changes gain, not radius;
- global HQ volume zero keeps canonical time running while client transport/decode/render hibernates;
- client-local mute support through `canStartSilent()`;
- renderer activation/loss recovery through authoritative rejoin;
- ordinary non-gapless replay after physical EOF while authoritative looping remains enabled;
- real packaged-JLayer MP3 coverage across starvation/refill/sliding/pre-target discard;
- deterministic renderer-read policy coverage;
- persistent staging-leftover cleanup on whole-owner destruction.

Resolved M1G issues: KI-051, KI-053, KI-055, KI-056, KI-057, KI-058, KI-060, KI-061. KI-059 was already a product decision.

M1G intentionally does not include gapless MP3, permanent-source loop engineering, dynamic volume-aware listener membership, full M1H rejoin/movement lifecycle, or Sound Physics Remastered range/acoustic integration.

## Post-M1G hardening

The selected Option A hardening pass is complete at source checkpoint `3d30ce4564de749f32171666df65de739b08ad77`. Latest full verification on the same source tree is CI `35406595856`, green on NeoForge 21.1.247 and 21.1.248 with deterministic tests, packaged-mod verification, and artifacts.

Closed:

- KI-062 — DNS/server-monitor coupling;
- KI-063 — destructive replacement before admission;
- KI-054 — shutdown cleanup/root-lock retry handling;
- KI-064 — import no-progress and atomic-move fallback.

M1H source work, M1J modern finite multispeaker, and finite API/engine convergence are complete at source/test/CI/package level. Their focused Minecraft checks remain in the runtime backlog. Current non-runtime work is release-oriented API/dependency cleanup and hardening.

## Audit recheck notes

A September 16 full-repository audit was challenged against exact source. The important surviving findings above remain, but several first-draft claims were retracted:

- MP3 anchors contain only `(offset, seconds)` and STATE already carries both;
- modern STATE does not carry live x/y/z coordinates;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- the Level-keyed WeakHashMap intentionally relies on explicit lifecycle eviction because cached values reference their Level;
- inherited HTTP stream paths close their streams;
- release retry ticking is `ServerMediaAssets.tickPendingReleases()`.

For VS2 movement, modern BEGIN already carries block coordinates and the legacy client already transforms those coordinates client-side each tick. M1H may reuse that pattern or add explicit position updates later; no M1G protocol change is implied.

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

- **M1E–M1G:** modern bounded progressive finite engine — complete; focused M1G audible/core Minecraft PASS recorded on NeoForge 21.1.247.
- **M1H:** listener lifecycle, recovery, and Sable/VS2 moving sources — source/test/CI/package complete; focused runtime backlog deferred.
- **M1J:** modern finite multispeaker — source/test/CI/package complete; focused runtime acceptance deferred. Shared authority/endpoints, protocol v8 shared client timeline, group playback helpers, independent volume/mute, and modern indexed controls are implemented.
- **Performance gate:** shared decode/network fan-out is deferred unless realistic runtime profiling proves duplicated client decode work materially expensive.
- **Finite API/engine convergence:** complete. MP3/WAV compatibility names use the modern engine; OGG/generic whole-file aliases and the duplicate finite engine are removed; current protocol is v9.
- **Current non-runtime work:** RAW/API and release-oriented cleanup, followed by optional codec decisions and integrated compatibility/stress. Radio/ICY/HLS/TS and provider playback are future/optional features, not core blockers.

## Build

```text
./gradlew clean build
./gradlew clean build -PneoForgeVersion=21.1.248
```

## Documentation

Read current development docs in this order:

1. `docs/CURRENT-STATE.md`
2. `docs/HANDOFF-2026-09-18-M1G-COMPLETE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/FUTURE-CLEANUP.md`
7. `docs/ARCHITECTURE.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. exact current source/CI

Older milestone/handoff documents preserve checkpoint history but do not override current records.
