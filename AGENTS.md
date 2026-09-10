# CC:HQ Speakers fork — agent guide

## Mission

Improve the existing CC:HQ Speakers NeoForge port into a polished ComputerCraft audio-player mod for ATM10.

The project exists to fix user-visible problems:
- 8 MiB finite-media limitation;
- no proper pause/resume/seek/position/duration controls;
- broken/incomplete loop semantics;
- weak playback status;
- lack of productized Sound Physics Remastered support.

## Exact stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- SPR 1.21.1-1.5.1 compatibility target

Baseline fork commit:
`d1a592351c866f9a28ceef00b59e591ee773f3d5`

## Working rule

This is a fork-improvement project, not a clean-room rewrite.

Preserve working inherited codec/streaming behavior unless a concrete defect justifies change.

Prefer:
- bug fixes;
- player-visible features;
- bounded resource improvements;
- compatibility;
- tests.

Avoid:
- speculative frameworks;
- replacing working systems for elegance;
- re-running already-settled HighAudio/SPR research.

## Source-grounded inherited facts

Before proposing architecture, remember:

- finite encoded media is capped at 8 MiB in both `HQSpeakerPeripheral` and `HQSpeakerAudioPacket`;
- finite content is currently sent as one encoded `byte[]` packet;
- `setLooping` only changes a server boolean;
- packets carry no loop state;
- `HQSpeakerSound` sets Minecraft looping to false;
- `speakIsPlaying()` reports server queue/stream state, not actual finite client playback;
- `HQAudioStream` already decodes finite files on `HQSpeaker-Decoder`;
- finite OGG/JavaSound decoding still materializes the whole decoded result;
- decoded finite PCM is capped at 64 MiB.
- the inherited NeoForge build uses the Forge-suffixed CC:T artifact `cc.tweaked:cc-tweaked-1.21.1-forge:1.113.1`; during M0 confirm the correct 1.120.0 coordinate instead of assuming a blind version-string swap.

Do not rediscover these from scratch unless source changes.

## Evidence order

1. exact target-stack runtime evidence
2. exact current source
3. `docs/VERIFIED-FACTS.md`
4. `docs/CURRENT-STATE.md`
5. architecture/roadmap
6. historical research

## HighAudio

Reuse only transferable facts/techniques:
- sound-thread behavior;
- PCM carry/alignment;
- STBVorbis experiments;
- source-count experiment;
- testing/provenance discipline.

Do not automatically import HighAudio's ContentId/upload/session/transport architecture.

## SPR

`ztawfik523-lgtm/cchq-soundphysics-compat` is existing project work.

Frozen V7.1 acoustics are not to be casually retuned.

## Testing

Follow `docs/TESTING.md`.

Batch manual Minecraft sessions. Do not ask for repeated tiny manual tests when automated/source evidence can narrow the work first.

## Choices

When two approaches have meaningful tradeoffs, present both concretely and let the user choose.

Handle routine implementation details yourself.
