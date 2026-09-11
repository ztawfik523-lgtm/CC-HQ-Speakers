# CC:HQ Speakers fork — agent guide

## Mission

Improve CC:HQ Speakers into a **high-quality programmable ComputerCraft speaker peripheral** for the exact ATM10 target stack.

This is not a built-in music player, sound-effect player, notification system, or HighAudio 2. The Java side exposes truthful audio capabilities; Lua programs decide what those capabilities are used for.

Core rule:

> Distinguish audio by technical properties, not by application meaning.

- finite media (MP3/OGG/WAV/supported files): known timeline, so duration/position/seek/loop/pause/resume/EOF are meaningful;
- raw/feed audio (`playAudio`, `speakPCM`): open-ended producer-fed PCM, so backpressure/stop/volume and possibly pause are meaningful, but finite duration/seek are not;
- live network streams (MP3/HLS/TS): open-ended remote sources, so no fake finite duration/seek; pause/resume, when implemented, means stop/suspend then reconnect to the current live point.

## Exact stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- SPR 1.21.1-1.5.1 compatibility target

Untouched fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

Reviewed M1 reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

M1A local-file prototype reference:

`69e34a5346f6ce47580f49ed867c9951bfd338bc`

Do not rewrite prototype history to pretend later redesign choices were already implemented there.

## Compatibility is a hard requirement

The Mixin upgrades CC:Tweaked's normal `speaker` peripheral and still reports type `speaker`.

Therefore standard CC:T speaker methods are a compatibility contract, not optional inspiration.

Read `docs/CC-T-COMPATIBILITY-CONTRACT.md` before changing:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- `speaker_audio_empty`

Prefer delegating standard behavior to CC:T's actual `SpeakerPeripheral` rather than cloning it.

## Accepted architecture

The old P0 D1-D4 choices are no longer unresolved. `docs/ROADMAP.md` is the implementation target.

- Java does **not** own playlists. A new incompatible HQ continuous playback replaces the previous HQ continuous playback; Lua owns sequencing/queue policy.
- Do not keep a permanent historical listener list. The server owns playback state and nearby/tracking clients dynamically render the current state.
- Finite media is a reusable **asset**, separate from a playback and separate from the physical speaker.
- Large finite transfer is client-pulled in bounded byte ranges. Encoded asset transfer remains complete/reliable; only stale decoded PCM may later be dropped at renderer taps to preserve real-time sync.
- The server owns finite playback state/timeline. Client renderer READY/STARTED/ENDED must not become canonical playback authority.
- Multispeaker finite playback shares asset/timeline work where possible but keeps one positional renderer per audible physical speaker for correct spatial audio and eventual SPR integration.
- Finite sync must not wait for an expected global speaker/tap count.
- Local CC files and large finite media are higher priority than Internet/live streaming.

Two choices remain open until implementation evidence warrants deciding them:

- whether a first playback starts the server clock immediately or waits for initial nearby readiness;
- progressive playback while an asset is still downloading (M2) versus M1 full encoded cache before playback.

## Current source model

There are currently overlapping inherited/prototype paths:

1. standard CC:T behavior delegated by the composite peripheral;
2. legacy HQ raw/finite/stream code in `HQSpeakerPeripheral`/`HQAudioStream`;
3. the M1A staged finite prototype in `HQFiniteMediaServer`/`HQFiniteMediaClient`.

The staged prototype proved staging, chunking, disk cache, and incremental decoding. Its fixed recipient set, renderer observation timeout, client-authoritative status transitions, and per-speaker media ownership are scheduled for replacement. Do not spend cleanup effort polishing those concepts.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior for architectural cleanliness alone.
- Preserve inherited behavior when useful and compatible, but do not preserve a proven bug.
- Prefer exact source/runtime evidence over assumptions.
- Keep server semantic state separate from client renderer observation.
- Never invent duration/seek for open-ended inputs.
- Do not classify MP3/WAV/PCM as music/effects/notifications.
- Do not build a Java playlist/priority system; Lua is the policy layer.
- Avoid fixing implementation details inside concepts explicitly scheduled for deletion.
- Keep SPR V7.1 acoustics frozen unless explicitly retuning them.
- Batch real Minecraft testing after source/automated work has narrowed the unknowns.
- Do not use `git add .`.

## Evidence order

1. exact target-stack runtime evidence;
2. exact current source;
3. `docs/VERIFIED-FACTS.md`;
4. `docs/CURRENT-STATE.md`;
5. `docs/CC-T-COMPATIBILITY-CONTRACT.md`;
6. accepted architecture/roadmap;
7. historical research/handoffs.

Recommendations must not be written into `VERIFIED-FACTS.md` as facts.

## Testing

Read `docs/P0-TEST-MATRIX.md`, `docs/TESTING.md`, and the current milestone section in `docs/ROADMAP.md`.

Current Java CI is useful but does not prove Minecraft/client lifecycle behavior. Runtime scripts are acceptance tests, not substitutes for source/state-machine tests.
