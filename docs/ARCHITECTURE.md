# Architecture

Updated: 2026-09-21

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

RAW is separate producer-fed signed-16 mono 48-kHz PCM. The composite owns admission/backpressure/group preflight; the legacy peripheral is only the lower-level bounded queue/packet substrate.

## MP3/ICY radio

Direct radio creates one client decoder for one endpoint.

Grouped radio is strict-snapshot. Server speaker membership is captured at command time. A client collects only packets it receives before the seal tick. Sealing starts one shared decoder for that client-local group; every accepted tap receives the same decoded PCM stream and all ready endpoints are released together after prebuffering. Late packets are rejected for that group.

There is no expected-member count and no automatic membership. Rerunning the Lua command creates a new group.

HLS and MPEG-TS are removed.

## Movement

All HQ positional paths call `MovingSourcePosition`:

1. Sable Companion sublevel projection;
2. VS2 transform;
3. static block center.

Legacy RAW/radio client sources also resolve movement locally every tick, so movement is not dependent on continuous server position packets.

## Radio URL/lifecycle

Blocking URL/DNS validation stays outside sensitive ownership locks. Starts are revision/lifecycle guarded and the final world/network commit runs on the server thread. Client startup is cancellable; drained/failed client radio state is retired.

The URL policy is a safety filter, not a substitute for normal server/client network security policy.

## Storage/workers

Default prepared-media limits: 512 MiB per asset, 2048 MiB total, configurable server-side.

Finite range IO: 2 workers, queue 64, max 128 KiB/response, 4 outstanding requests and 512 KiB outstanding bytes/player, 512 KiB client encoded window.

## Protocol

Protocol v10, 9 payloads.
