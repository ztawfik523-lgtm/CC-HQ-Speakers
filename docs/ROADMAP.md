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

## Active milestone — M1J modern finite multispeaker

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

### M1J implementation slices

1. Extract a thread-safe shared playback authority and route existing single-speaker prepared playback through it. One speaker is simply a playback with one endpoint.
2. Add explicit endpoint attach/detach ownership and transaction-safe group startup. Validate/reserve every target before destructive replacement.
3. Add modern prepared multispeaker API/dispatch from the ComputerCraft-visible speaker set. Do not copy the inherited expected-member sync barrier.
4. Move group pause/resume/seek/loop semantics onto the shared authority and define endpoint versus group volume behavior.
5. Move protocol to the minimum revision needed for shared playback identity/state revision, then make clients project one shared timeline per playback while preserving one renderer per physical speaker.
6. Add deterministic group lifecycle tests: partial listener visibility, endpoint removal/replacement, overlapping computer ownership, late listener admission, seek/recovery, and empty-group cleanup.

Focused Minecraft multispeaker acceptance comes after the source/component path is coherent; it is not required before starting M1J.

## Decision gate — multispeaker performance sharing

After M1J correctness, measure realistic 2/4/8+ speaker cases.

Only add shared encoded-range/decode fan-out if duplicate work is materially expensive. Do not pre-build shared decoder/window machinery merely because speakers share semantic playback.

## Engine/API convergence

After modern multispeaker is established:

- migrate worthwhile inherited finite APIs onto the modern engine;
- repair or remove misleading legacy `*All` / `*At` behavior;
- finish HQ RAW public semantics and truthful capability reporting;
- remove obsolete whole-file/whole-PCM finite paths only after their retained functionality has a replacement.

Cleanup follows replacement; it does not delete the old implementation before deciding which behavior survives.

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

- resolve license provenance mismatch;
- decide whether the separate `hqspeaker:hq_speaker` block remains;
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
