# Roadmap

Updated: 2026-09-19

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning and policy; Java exposes truthful audio capabilities and playback mechanics.

One physical speaker remains one mono positional source. Do not add permanent Java music/effect/notification roles or a Java playlist manager.

Direct internet-radio/ICY/HLS/TS playback is not a core roadmap requirement. Spotify/YouTube/provider-backed playback is a possible future feature and must be evaluated separately against the provider's actual playback/API constraints.

## Completed foundation — M1A through M1H

M1A through M1D established CC:T compatibility, reusable server MediaAssets, ComputerCraft local-file import, and server media analysis.

M1E through M1G established the modern finite engine:

- server-authoritative finite time/state;
- demand-driven bounded encoded transport;
- progressive MP3/common-WAV decode;
- normal Minecraft SoundManager positional rendering;
- protocol v7 decoder/re-anchor revision;
- fixed 32-block modern core radius;
- bounded recovery, looping, staging, and ownership semantics.

M1G focused audible/core Minecraft acceptance passed on NeoForge 21.1.247 on 2026-09-19.

The post-M1G hardening pass closed KI-062, KI-063, KI-054, and KI-064.

M1H-1 through M1H-3 are complete at source/test/CI/package level:

- dynamic listener admission/leave/re-entry;
- renderer/resource/starvation recovery;
- Sable/Aeronautics and VS2 moving-source position handling without continuous position packets.

Focused M1H Minecraft checks remain in the runtime backlog. They are deferred for now, not rejected as future validation.

## Completed source milestone — M1J modern finite multispeaker

### Selected model

A ComputerCraft computer starts one prepared playback against the speakers selected at command time.

The server creates **one shared finite playback authority** for that play. It owns facts which must be identical:

- playback/media identity;
- one canonical clock;
- PLAYING/PAUSED/ENDED/shared-ERROR state;
- duration and looping;
- seek/re-anchor revision;
- shared state revision;
- playback asset lifetime.

Each physical speaker is an independent **endpoint** attached to that authority. The endpoint owns facts which can legitimately differ or fail independently:

- physical source UUID and block/world position;
- listener membership;
- moving-source resolution;
- endpoint gain/trim;
- range transport and client renderer/recovery state.

There is no expected-global-member barrier. The member set is a start-time snapshot: attaching another speaker to the computer later does not silently join the active playback.

Removing, replacing, or losing one endpoint must not stop the other endpoints. An empty authority releases its playback asset.

### M1J implementation status

First implementation checkpoint: `b557773b9c6f6b8029aec132a1706f0d8da914bd`, CI `35466635285`, green on both supported NeoForge targets.

Implemented:

1. thread-safe shared playback authority used by both one-speaker and multispeaker prepared playback;
2. shared asset lifetime plus independent endpoint attach/detach;
3. transaction-style prepared group start with no expected-member barrier;
4. modern `playPreparedAll` / `playFileAll`;
5. shared pause/resume/seek/loop/stop with endpoint-local volume/mute and all/indexed variants;
6. protocol v8 shared `playbackId` / `stateRevision` and one client-projected timeline per playback;
7. deterministic authority and shared-timeline projection tests.

Source/test/package closeout is complete. Focused real-Minecraft multispeaker acceptance remains deferred and must not be claimed as passed.

## Decision gate — multispeaker performance sharing

M1J source/test/package correctness is now implemented; focused Minecraft multispeaker acceptance is deferred for now. The next non-runtime task is to measure/estimate the duplicate-work surface and prepare realistic 2/4/8+ speaker profiling without precommitting to shared decode/network machinery.

Current static cost model:

- per audible endpoint, client encoded RAM is bounded at 512 KiB;
- per endpoint PCM queue is bounded at 32–256 KiB;
- server range transport is already bounded per player to 4 outstanding requests / 512 KiB total, regardless of endpoint count;
- the main duplicated cost that can still grow with speaker count is client codec work and endpoint-local PCM/render state.

Decision for now: do **not** add shared encoded/decode fan-out without runtime profiling. The server-side bounds already prevent unbounded range amplification, while shared decode would add multi-reader PCM lifetime, lagging-reader, endpoint-leave, seek/recovery, and renderer-pacing complexity. Revisit only if realistic 4/8+ speaker testing shows decoder CPU or memory is materially expensive.

## Completed non-runtime work — finite engine/API convergence

The finite convergence decision is complete:

- legacy-name MP3/WAV singular/all/indexed frontends route through the modern MediaAsset/progressive engine;
- OGG and generic whole-file aliases are retired from the normal upgraded CC:T speaker;
- the inherited complete-file finite server/client state, decoder, sync barrier, control/status payloads, and finite packet baggage are removed;
- the old legacy audio packet is now RAW/live-only;
- current network protocol is v9.

There is no second supported finite engine.

## Active non-runtime work — RAW/API and release-oriented cleanup

Next, finish the remaining non-finite public contract without mixing optional live-stream work into the core:

- make HQ RAW capability/backpressure semantics concise and truthful;
- remove or hide obsolete legacy helper names which no longer add supported behavior;
- keep direct radio/ICY/HLS/TS explicitly optional until separately justified;
- continue dead-code/dependency cleanup where removal does not break retained RAW/live behavior.

## Optional media-format expansion

Formats are evaluated individually, not as mandatory numbered milestones.

OGG has historical/product compatibility value but still needs a real progressive seek/rejoin implementation. FLAC is optional new breadth. AAC or other formats should be added only for a concrete use case.

No format is advertised until analysis, progressive decode, seek/rejoin, malformed-input bounds, packaging, and appropriate runtime proof exist.

## Integrated compatibility and hardening

Run realistic many-speaker/listener and lifecycle stress across:

- M1H listener/recovery/movement backlog;
- multispeaker start/stop/seek/replacement;
- Sable/Aeronautics and VS2;
- Sound Physics Remastered compatibility/performance;
- disconnect/reload/starvation;
- malformed/extreme media;
- bounded queues, memory, network, and worker lifetime;
- both supported NeoForge targets.

SPR should use the normal Minecraft SoundManager path unless a concrete runtime gap proves custom integration is needed.

## Release cleanup

Before public release:

- license provenance resolved: inherited MPL-2.0 retained and mod metadata corrected;
- inherited standalone `hqspeaker:hq_speaker` block removed; normal CC:T speaker is the sole block surface;
- remove dead/replaced code;
- freeze truthful public API/docs/capabilities;
- verify packaging and dependency boundaries;
- clean CI/default-branch/repository hygiene;
- run final integrated Minecraft acceptance.

## Future-feature bucket

These are possible features, not core release blockers:

- Spotify/YouTube/other provider-backed playback research;
- direct internet-radio URLs and ICY metadata;
- HLS/TS;
- formats skipped by the optional codec gate;
- native ordinary Create contraption lifecycle;
- gapless playback or other higher-end playback features.

Provider-backed playback must not be treated as equivalent to a direct HTTP audio URL; each provider needs its own technical/API/legal feasibility check.
