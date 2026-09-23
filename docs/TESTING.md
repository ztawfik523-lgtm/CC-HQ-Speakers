# Testing

Updated: 2026-09-22

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does not prove Minecraft audibility, OpenAL/SoundManager lifecycle, moving-ship behavior, spatial synchronization or realistic performance.

Frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`.  
Source-freeze CI: `35655973164`.  
Runtime-prep scripts/docs: `a234ba02b80532daf32f6849061b76f23c0eb4d3` / CI `35657182389`.  
Historical CI verified both NeoForge 21.1.247 and 21.1.248. Current CI intentionally builds/tests/packages only once against 21.1.247; that artifact targets the supported `[21.1,21.2)` NeoForge range.

Deterministic coverage includes finite analyzers/decoders/range transport/state machines/recovery/projection, storage/release limits, ordered multi-lock behavior, RAW feed lifetime, strict radio group sealing and URL policy.

## Runtime plan

Use `RUNTIME-ACCEPTANCE-V10.md` as the only current runtime sequence and record results in `RUNTIME-RESULTS-V10.md`.

Current user-facing runner:

- `scripts/v10_acceptance.lua <mp3> <wav> [direct-mp3-or-icy-url]`

It combines deterministic API/bounds/control tests with built-in real-client/OpenAL diagnostics. Smaller scripts remain developer isolation tools if this master runner identifies a concrete failure.

The target runtime pass covers native CC:T behavior, finite singular/multispeaker controls, listener/recovery, Sable movement, RAW backpressure/continuity, strict MP3/ICY radio grouping, malformed/bounds/stress, Sound Physics Remastered, and realistic 2/8+ speaker behavior. Dedicated-server/multiplayer and VS2 are outside the selected acceptance scope. One NeoForge 21.1.247-built artifact is used for the supported 21.1.x range.

M0/M1 milestone scripts are historical unless the v10 runtime plan explicitly references them.
