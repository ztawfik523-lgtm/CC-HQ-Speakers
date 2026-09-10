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

M1 reviewed reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

## Compatibility is a hard requirement

The Mixin replaces CC:Tweaked's normal `speaker` peripheral and still reports type `speaker`.

Therefore standard CC:T speaker methods are a compatibility contract, not optional inspiration.

Read `docs/CC-T-COMPATIBILITY-CONTRACT.md` before changing:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- `speaker_audio_empty`

HQ methods may extend this contract, but must not silently make normal CC speaker programs incorrect.

## Current source model

Do not infer "music" or "effect" roles.

The current implementation contains three technically distinct paths:

1. raw PCM/feed;
2. finite encoded media with M1 retained decoded PCM and generation state;
3. live URL streaming.

The exact current behavior and known defects are in:

- `docs/CURRENT-STATE.md`
- `docs/KNOWN-ISSUES.md`
- `docs/VERIFIED-FACTS.md`

## P0 decisions

Four architecture choices intentionally remain unresolved. Do not silently choose them.

Read `docs/P0-DESIGN-DECISIONS.md`:

- heterogeneous raw/finite submission semantics;
- stop/control packet recipient ownership;
- decoder queue cancellation strategy;
- partial multi-speaker sync behavior.

When a decision has meaningful tradeoffs, present the options and let the user choose.

## Working rules

- Improve the inherited fork; do not rewrite for architectural cleanliness alone.
- Preserve inherited behavior when it is useful and compatible, but do not preserve a proven bug.
- Prefer exact source/runtime evidence over assumptions.
- Keep server semantic state separate from client renderer observation.
- Never invent duration/seek for open-ended inputs.
- Do not classify MP3/WAV/PCM as music/effects/notifications.
- Do not import HighAudio's full upload/session/content architecture unless a concrete CC:HQ problem requires it.
- Keep SPR V7.1 acoustics frozen unless explicitly retuning them.
- Batch real Minecraft testing after source/automated work has narrowed the unknowns.
- Do not use `git add .`.

## Evidence order

1. exact target-stack runtime evidence;
2. exact current source;
3. `docs/VERIFIED-FACTS.md`;
4. `docs/CURRENT-STATE.md`;
5. `docs/CC-T-COMPATIBILITY-CONTRACT.md`;
6. architecture/roadmap;
7. historical research/handoffs.

Recommendations must not be written into `VERIFIED-FACTS.md` as facts.

## Testing

Read `docs/P0-TEST-MATRIX.md` and `docs/TESTING.md`.

Current Java CI is useful but does not prove Minecraft/client lifecycle behavior. Runtime scripts are acceptance tests, not substitutes for source/state-machine tests.
