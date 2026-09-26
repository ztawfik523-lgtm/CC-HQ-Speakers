# Architecture

Updated: 2026-09-26

## Product boundary

The mixin replaces the exposed peripheral of the normal CC:T speaker with `HQSpeakerCompositePeripheral` while retaining CC:T's real `SpeakerPeripheral` for native behavior.

One physical speaker remains one mono positional source.

## Ownership

One HQ continuous owner exists per physical endpoint:

- NONE
- RAW
- STAGED_FINITE
- STREAM

Replacement paths validate/admit before ending a valid current HQ source where possible. World/network commits which require Minecraft-thread affinity are committed on the server thread.

## Native CC:T

Standard note/sound/DFPWM calls are delegated to CC:T. Grouped/indexed helpers select endpoints but still call real CC:T speaker implementations.

## Finite

`HQMediaStaging` imports encoded bytes into immutable server `MediaAsset` storage. MP3/common-WAV analysis is server-derived.

`FinitePlaybackAuthority` owns canonical playback time/state. Multispeaker finite playback shares one authority/asset while each endpoint owns its physical source, listener membership, range transport, decoder/renderer/recovery, gain and mute.

Finite client transport is bounded/progressive. Each endpoint currently keeps its own decoder/render path; shared finite decode fan-out is intentionally deferred until profiling proves value.

## RAW

RAW is separate producer-fed signed-16 mono 48-kHz PCM. The composite owns admission/backpressure/group preflight; the legacy peripheral is the lower-level bounded queue/packet substrate.

When the client RAW stream exhausts its local queue, it returns no buffer rather than manufacturing silence. If later producer PCM arrives, the existing Minecraft channel is explicitly pumped again on the sound executor, mirroring the continuation strategy used by CC:T's own DFPWM stream.

## MP3/ICY radio

Direct radio creates one client decoder for one endpoint.

Grouped radio is strict-snapshot. Server membership is captured at command time. A client collects only packets received before the seal deadline. Sealing starts one shared decoder for that client-local group; accepted taps consume the same decoded PCM and ready endpoints are released together after prebuffering. Late packets are rejected for that group.

There is no expected-member count and no automatic membership. Rerunning the Lua command creates a new group.

HLS and MPEG-TS are removed.

## Movement

All HQ positional paths call `MovingSourcePosition`:

1. Sable Companion sublevel projection;
2. VS2 transform;
3. static block center.

Legacy RAW/radio client sources also resolve movement locally every tick, so movement is not dependent on a continuous server position packet.

## Built-in diagnostics

The production JAR contains a dormant `HQDiagnostics` subsystem plus client-side channel sampling.

When diagnostics are enabled:

- HQ/CC:T sound instances are associated with stable diagnostic source identities;
- NeoForge sound events expose the actual Minecraft `Channel`;
- a small accessor exposes the OpenAL source id needed for measurements;
- client ticks sample source state, position, gain, queued/processed buffers and playback offset/latency where available;
- finite/RAW/radio stream paths count PCM input/read, silence/starvation, wakeups and decoder/recovery events;
- sound-engine reload events invalidate old bindings and are counted;
- group metrics aggregate channel-start skew, logical playback drift and PCM feed spread;
- Sound Physics direct-filter state is sampled when the mod is loaded;
- Sable tracking compares requested movement with the live OpenAL source position.

Diagnostic state is shared directly with the integrated server in singleplayer. No diagnostic network payload was added; protocol v10 remains 9 payloads.

The Lua surface is `hqDiagEnable`, `hqDiagReset`, `hqDiagSnapshot`, `hqDiagCapabilities`.

## Radio URL/lifecycle

Blocking URL/DNS validation stays outside sensitive ownership locks. Starts are revision/lifecycle guarded and final world/network commit runs on the server thread. Client startup is cancellable; drained/failed client radio state is retired.

The URL policy is a safety filter, not a substitute for normal server/client network security policy.

## Storage/workers

Default prepared-media limits: 512 MiB per asset, 2048 MiB total, configurable server-side.

Finite range IO remains bounded: 2 workers, queue 64, max 128 KiB/response, 4 outstanding requests and 512 KiB outstanding bytes/player, 512 KiB client encoded window.

## Protocol

Protocol v10, exactly 9 payloads.

## Build

Current build/test/package baseline is NeoForge 21.1.247. The one artifact declares `[21.1,21.2)`; there is no second current 21.1.248 build.
