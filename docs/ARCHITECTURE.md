# Architecture

Updated: 2026-09-27

## Product boundary

The normal CC:T `computercraft:speaker` is upgraded through `HQSpeakerCompositePeripheral`, while the real CC:T `SpeakerPeripheral` remains the owner of native behavior.

One physical speaker remains one mono positional source.

## Ownership

One HQ continuous owner exists per endpoint: NONE, RAW, STAGED_FINITE or STREAM.

Finite multispeaker playback shares canonical authority/asset, while endpoints own source/listener/transport/decoder/renderer/gain/mute state.

## Finite transport

Prepared encoded media is stored server-side as immutable assets and fetched progressively in bounded ranges.

Current tuning:

- 128 KiB max range;
- 512 KiB client encoded window;
- 2 finite client requests may be in flight per endpoint;
- **4 outstanding requests / 512 KiB outstanding bytes per player server-side**;
- 2 server range IO workers;
- queue 64.

### Attempt-7 scale finding

Those limits are not currently composed correctly for multispeaker scale.

Eight endpoints can attempt up to 16 requests, while only four are admitted for the player. `FiniteRangeReadService.Submission.OVER_LIMIT` is not projected back to the client, so rejected requests remain client-pending until the 2-second request timeout expires.

That admission/retry mismatch, not decoder complexity, is the currently demonstrated cause of the eight-speaker late start.

The fix should preserve bounded memory/IO while providing prompt fairness/progress; do not simply remove all limits.

## Renderer ownership and scale

Finite/RAW/radio currently use Minecraft `SoundManager` / `Channel` paths rather than raw OpenAL ownership.

This keeps lifecycle/resource integration simple and is compatible with normal Minecraft sound processing, but Minecraft's streamed-source capacity may become relevant above eight simultaneous HQ sounds. That is a separate architecture question from the attempt-7 range bug and needs explicit measurement.

## RAW

RAW is producer-fed signed-16 mono 48-kHz PCM with bounded backpressure. Later PCM re-pumps an exhausted existing Minecraft/OpenAL channel rather than inserting fake silence.

8+ RAW still needs runtime evidence because attempt 7 never reached that half of C4.

## MP3/ICY radio

Grouped radio uses strict start-time membership and one shared client-local decoder/prebuffer feeding endpoint taps. Late speakers do not auto-join; rerunning creates a new group.

C3 runtime acceptance passed.

## Movement

All HQ positional paths call `MovingSourcePosition`: Sable Companion, then VS2, then static center.

Attempt 7 demonstrated Sable requested and actual OpenAL movement matching over ~52-53 blocks. The runner's C1 failure was caused by unrelated listener relevance history.

## Sound Physics Remastered

Current integration is intentionally light: HQ sounds travel through Minecraft channels, and diagnostics observe SPR environment application through a mixin.

Attempt 7 showed this observation is insufficient for final proof. `setEnvironment` observation does not establish that SPR's full `processSound` world/ray evaluation ran for the HQ source.

The next diagnostic must record actual SPR processing before architectural changes are chosen.

The historical `cchq-soundphysics-compat` project directly owned OpenAL sources and explicitly invoked/captured SPR processing. It is now a design reference, not a frozen requirement. Because the current HQ fork is ours, the new integration may change either side if that yields a simpler and more correct design.

## Built-in diagnostics

Diagnostics remain dormant in normal play and are enabled only by acceptance/debug workflows. They observe actual client channels, PCM/buffer state, source movement, recovery, sync and SPR hooks without adding a v10 network payload.

## Protocol/build

Protocol v10 remains exactly 9 payloads.

Build/test/package baseline remains NeoForge 21.1.247 with metadata `[21.1,21.2)`.
