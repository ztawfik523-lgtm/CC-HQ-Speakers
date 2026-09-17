# Transferable HighAudio findings

Updated project-context note: 2026-09-17

> **Research evidence only.** Keep the expensive findings; do not import the whole architecture and do not let this file override the current CC:HQ implementation/state documents.
>
> Current authority is `../CURRENT-STATE.md`, `../M1G-SCOPE-DECISIONS-2026-09-14.md`, `../KNOWN-ISSUES.md`, `../TESTING.md`, `../VERIFIED-FACTS.md`, and exact current source.

## Useful

- `AudioStream.read()` exact-stack sound-thread observation.
- one returned buffer -> one OpenAL upload/queue path in the inspected runtime.
- never drop PCM tails; preserve complete frame alignment.
- STBVorbis incremental decode/seek/length/error/close results.
- strong native encoded-input lifetime through close.
- vanilla 8-stream measurement and proven total-preserving 16-stream rebalance.
- batched manual-test policy.

## Do not transplant by default

- custom file upload protocol;
- SHA-256 ContentId store;
- HighAudio server session framework;
- custom content transfer protocol;
- GenericSource product architecture;
- full HighAudio milestone system.

Rule: reuse a HighAudio fact/technique when it directly solves a CC:HQ bug.

## Current CC:HQ boundary

The current modern CC:HQ path already has its own bounded server MediaAsset/range transport, progressive MP3/common-WAV decode, bounded PCM queue, and normal Minecraft positional renderer. Do not use old HighAudio architecture as a reason to replace those working project decisions.

Current M1G choices which this research does not override include:

- normal Minecraft `AudioStream` / `SoundManager` rendering;
- explicit future decoder/re-anchor revision rather than anchor-change-as-intent;
- fixed 32-block M1G core range with volume changing gain rather than radius;
- global-volume-zero local hibernation;
- ordinary non-gapless loop replay;
- later SPR integration rather than M1G acoustic/range expansion.

## MP3 seek/duration caveat

HighAudio research identified MP3 as a harder finite-media case than Ogg for truthful duration/seek behavior.

For MP3, exact duration and efficient seeking may depend on optional VBR metadata such as Xing/VBRI or require scanning/indexing the stream. Do not assume every MP3 can cheaply provide exact total duration or random seek.

By contrast, the tested Ogg/STBVorbis path exposed total sample count and exact sample seek without full PCM predecode.

Current CC:HQ M1G chose a deliberately simpler MP3 seek model: conservative E1 pre-roll from an earlier analyzed seek point, progressive JLayer decoding forward, and discard of pre-target PCM. `FiniteDecodeAnchorSelector.Anchor` contains only encoded offset and seconds; do not invent richer hidden frame/skip metadata from this older research.

True MP3 gapless loop metadata handling is explicitly **not** an M1G requirement. Ordinary replay may include encoder padding/gap and that is acceptable at the current scope.
