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

### Attempt-7 scale finding and current fix

Attempt 7 exposed a composition bug: eight sources could independently submit up to 16 requests while the server admitted four requests / 512 KiB per player. Silent over-limit drops then waited for the 2-second client request timeout.

The current client now treats those server limits as one shared player budget. It may still pipeline two requests for a source when capacity is free, but the total local pending count never intentionally exceeds the server request cap, and request opportunities are distributed toward endpoints which have issued fewer requests.

This preserves bounded IO/memory and protocol v10 while directly removing the demonstrated admission stampede. Runtime C4 remains the authority on whether the fix is sufficient.

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

SPR remains an **optional client-side acoustic system**. The HQ server, protocol and playback authority do not depend on it.

Current custom HQ finite/RAW/radio sounds already use normal Minecraft streaming channels under `SoundSource.BLOCKS`. We therefore keep Minecraft channel ownership and first measure what upstream SPR already does.

The branch now has client-only diagnostic integration which:

- observes the exact SPR 1.21.1-1.5.1 `processSound` call;
- scopes observations only to `hqspeaker:hq_audio_source`;
- records the processed position/category/sound id and reflected-position return;
- correlates the environment write to the same active HQ process, preventing unrelated sounds or recycled OpenAL ids from being mistaken for HQ evidence.

No acoustic behavior is currently overridden.

C2 uses a normal-world wall and compares a continuously-playing source against a restarted source. If that proves long-running HQ acoustics are stale, the next architecture step is a small client-only refresh mechanism for active HQ sounds. If upstream SPR already refreshes correctly, nothing is added.

Native Minecraft/CC:T sounds remain SPR's responsibility. We do not special-case SPR policy choices such as its treatment of RECORDS sounds.

Sable-wall geometry is a separate future compatibility problem and is not part of the present SPR integration phase.

## Built-in diagnostics

Diagnostics remain dormant in normal play and are enabled only by acceptance/debug workflows. They observe actual client channels, PCM/buffer state, source movement, recovery, sync and SPR hooks without adding a v10 network payload.

## Protocol/build

Protocol v10 remains exactly 9 payloads.

Build/test/package baseline remains NeoForge 21.1.247 with metadata `[21.1,21.2)`.
