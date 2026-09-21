# Testing

Updated: 2026-09-21

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does not prove Minecraft audibility, OpenAL/SoundManager lifecycle, moving-ship behavior, spatial synchronization or realistic performance.

Frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`.  
The source freeze passed build/tests/package verification/artifact upload on NeoForge 21.1.247 and 21.1.248.

Deterministic coverage includes finite analyzers/decoders/range transport/state machines/recovery/projection, storage/release limits, ordered multi-lock behavior, RAW feed lifetime, strict radio group sealing and URL policy.

## Runtime plan

Use `RUNTIME-ACCEPTANCE-V10.md` as the only current runtime sequence and record results in `RUNTIME-RESULTS-V10.md`.

Current scripts:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/p0_finite_regression.lua <mp3>`
- `scripts/v10_core_acceptance.lua <mp3> [wav]`
- `scripts/v10_raw_acceptance.lua`
- `scripts/v10_multispeaker_stress.lua <mp3> [cycles]`
- `scripts/v10_radio_acceptance.lua [radio-url]`
- `scripts/v10_runtime_observer.lua [seconds]`

The runtime pass covers native CC:T behavior, finite singular/multispeaker controls, listener/recovery, Sable/VS2 movement across finite/RAW/radio, RAW backpressure, strict MP3/ICY radio grouping, malformed/bounds/stress, Sound Physics Remastered, realistic 2/4/8+ performance and both NeoForge targets.

M0/M1 milestone scripts are historical unless the v10 runtime plan explicitly references them.
