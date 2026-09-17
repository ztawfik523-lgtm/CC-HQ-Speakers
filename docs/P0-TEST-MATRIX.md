# P0 regression test matrix — historical

> **Historical testing strategy record.** The general layered-testing principles remain useful, but the milestone mapping and prototype targets below were superseded as M1E/M1F/M1G evolved. `TESTING.md` is the authoritative current matrix; `M1-RUNTIME-TEST.md` is the current consolidated Minecraft guide.

## Testing principles which remain valid

The repo should not require a full Minecraft launch to rediscover deterministic lifecycle/codec/state bugs.

Use three layers:

1. pure/unit tests for deterministic parser/codec/cursor/state/storage logic;
2. extracted component/coordination tests where Minecraft classes are not required;
3. focused Minecraft/Lua acceptance for real CC:T/Minecraft networking, sound engine, attenuation, lifecycle, and mod interoperability.

Do not keep adding tests for prototype concepts which the accepted roadmap removed.

## Historical tests which remain useful in context

### `FiniteAudioTrackTest`

Legacy retained-whole-track coverage. Useful only while inherited finite byte APIs/classes remain; it does not validate the modern large-file prepared engine.

### `HLSPlaylistParserTest`

Preserves parser facts for later live-stream work. It does **not** prove current inherited HLS progression is correct; the refreshed-playlist `currentSegmentIndex` bug remains later M3 work.

### `FinitePlaybackClockTest` / `FinitePlaybackStateMachineTest`

Useful pure server-timeline/state semantics. Modern M1E server authority now owns canonical finite state/time.

### `FiniteMediaPathTest`

Safe staged-media path normalization/traversal coverage.

### `RawFeedLifetimeTest`

M1A RAW lifecycle/admission timing coverage. It does not prove audible client timing.

### Modern M1F/M1G tests added later

Current suite also includes bounded range/window/read-service transport tests, common-WAV analyzer/converter tests, decode-descriptor/anchor tests, starvation-aware encoded-input tests, bounded PCM queue tests, and progressive WAV tests. See `TESTING.md` for exact current coverage and gaps.

## Why many packet/render tests are not ordinary pure unit tests

Minecraft/NeoForge packet and sound classes are not always available/ergonomic in the normal test source set. Prefer extracting pure validation/coordination logic when practical and reserve a real component/game/runtime harness for behavior that genuinely needs Minecraft.

Do not distort build architecture merely to instantiate packet classes in a unit test.

## Runtime scripts — current interpretation

Useful compatibility/history scripts:

- `scripts/p0_cc_speaker_contract.lua` — standard singular CC:T compatibility;
- `scripts/m1a_output_contract.lua` — historical M1A RAW/ownership surface;
- `scripts/m1e_server_authority_test.lua` — server-authority semantics.

Historical/prototype scripts which are **not** modern M1G gates:

- `p0_finite_regression.lua`;
- `m1_player_test.lua`;
- `m1a_local_file_test.lua`;
- `m1d_media_analysis_test.lua` for its old broad prepared-format expectations.

Use `hq.playFile()` / prepared MediaAssets for modern M1G acceptance.

## Important later corrections to the old milestone map

The original P0 matrix used an early roadmap whose labels/architecture no longer match the project. In particular:

- modern M1F is bounded client-requested encoded transport, not a whole-file client cache;
- modern M1G is progressive MP3/common-WAV decode + positional renderer, already integrated in source;
- M1H owns full late-entry/leave-return/general-underrun/moving-source lifecycle;
- native FLAC remains gated M1I;
- modern client persistent song cache/LRU is **not** a target;
- dynamic volume-aware range is **not** current M1G policy;
- current M1G core radius is fixed at 32 blocks;
- looping is ordinary non-gapless replay after local EOF while authoritative looping remains enabled.

Do not use the original P0 milestone table as present planning guidance.

## Current deterministic priorities

Current source/evidence work is tracked in `TESTING.md`. Highest-value additions include:

- explicit decoder/reanchor revision coordination tests for KI-053/KI-056/KI-057;
- real MP3 fixture through progressive JLayer across range sliding/starvation/pre-roll;
- focused `FinitePcmAudioStream` tests;
- fixed attenuation / renderer-start / volume-zero tests;
- ordinary replay tests;
- KI-062 blocking-DNS/shared-monitor regression proof;
- KI-063 failed-replacement admission proof;
- KI-054 shutdown/root-lock failure injection;
- KI-064 zero-read progress and atomic-move fallback proof;
- KI-061 whole staging-owner cleanup proof.

## CI expectations

Pure Java tests remain in the Java 21 matrix for NeoForge 21.1.247 and 21.1.248. Packaged-resource verification remains required for source candidates.

CI is not Minecraft runtime proof.

The current workflow still runs the full two-target matrix for all pushes/PRs without docs-only path filtering or concurrency cancellation; that is later CI hygiene, not M1G correctness.

## Runtime batching rule

The original batching principle remains good:

1. coherent source patch/family;
2. deterministic tests + both target builds green;
3. focused Lua/component checks;
4. one consolidated Minecraft acceptance batch for behavior which needs real sound/network/lifecycle;
5. only then label behavior runtime-proven.

For the actual current gates and recording requirements, use `TESTING.md` and `M1-RUNTIME-TEST.md`.
