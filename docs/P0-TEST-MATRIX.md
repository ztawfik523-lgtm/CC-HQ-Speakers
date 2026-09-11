# Regression test matrix

## Purpose

The repo should not require a full Minecraft launch to rediscover every basic lifecycle bug.

Use three layers:

1. pure/unit tests for deterministic codec/parser/cursor/state logic;
2. extracted state-machine/component tests for server/client lifecycle logic where Minecraft classes are not required;
3. consolidated Minecraft/Lua acceptance for behavior which depends on real CC:T/Minecraft networking and sound plumbing.

Do not keep adding tests for prototype concepts which the accepted roadmap deletes.

## Java unit tests present

### `FiniteAudioTrackTest`

Historical retained-M1 regression coverage:

- duration/frame-aligned reads;
- backward/clamped seek;
- exact seek-to-duration cursor state;
- loop cursor wrap;
- loop-disable cursor continuity;
- independent renderer forks;
- invalid PCM construction.

This remains useful while legacy finite byte APIs exist, but the retained whole-track PCM object is not the final large-file architecture.

### `HLSPlaylistParserTest`

Preserves parser facts for later stream work:

- live media playlist parsing;
- `EXT-X-MEDIA-SEQUENCE`;
- relative segment URL resolution;
- master variant resolution;
- audio-only codec classification.

This does not claim current HLS stream progression is correct.

### `FinitePlaybackClockTest`

Prototype/future semantic clock coverage:

- pause/resume;
- seek/clamping;
- exact-duration end behavior;
- loop wrap;
- loop-disable rebase.

The final M1E server-authoritative playback state should reuse or supersede these tested semantics rather than restoring renderer authority.

### `FiniteMediaPathTest`

Covers safe normalized staged-media paths and traversal rejection.

### `RawFeedLifetimeTest`

M1A pure RAW lifecycle coverage:

- accepted sample count converts to server-tick drain duration;
- successive RAW chunks accumulate duration;
- unsent outbound queue prevents source expiry;
- accepting new samples resets idle grace;
- the full idle grace is required before closure;
- state reset/clear;
- empty accepted chunks are rejected by the pure lifetime model.

This tests deterministic M1A server ownership timing without importing Minecraft packet/render classes.

## Why Minecraft packet tests are not ordinary unit tests

An initial P0 pass directly instantiated HQ packet classes. The normal Gradle `test` source set does not provide Minecraft's generated packet classes even though production `compileJava` has the NeoForge/Minecraft classpath.

Do not distort the build merely to make packet classes unit-testable. Extract protocol/state validation into pure classes when practical, or use a real NeoForge component/game-test harness only when it provides enough value.

## Lua/runtime acceptance

### `scripts/p0_cc_speaker_contract.lua`

Standard CC:T 1.120.0 contract acceptance:

- no idle `speaker_audio_empty` spam;
- standard `stop()` exists;
- `playNote("harp")` accepts omitted optional arguments;
- requested `playSound` is accepted through the native API;
- standard `playAudio` accepts signed 8-bit data;
- immediate second native buffer is backpressured;
- native `speaker_audio_empty` permits retry.

The script includes one server-tick separation after native `stop()`, matching the exact CC:T source where `stop()` sets a flag consumed by `SpeakerPeripheral.update()`.

M1A source now delegates these methods to CC:T, but the contract remains **runtime pending** until this script passes in Minecraft.

### `scripts/m1a_output_contract.lua [optional-small-mp3]`

M1A normal single-speaker extension acceptance:

- `speakMaxSamples()` reports `131072`;
- bounded HQ RAW queue eventually returns `false`;
- RAW status does not claim finite seek/loop capabilities;
- native notes remain callable during HQ RAW;
- native `playAudio` does not overlap active HQ continuous output;
- rejected RAW writer receives `hqspeaker_audio_empty` when queue capacity returns;
- `audioStop()` truthfully stops RAW and clears ownership;
- RAW may start again after stop;
- optional finite fixture verifies an incompatible HQ finite start replaces RAW rather than queuing behind it;
- native `playAudio` works again after HQ ownership is released.

### Historical/prototype finite scripts

`p0_finite_regression.lua`, `m1_player_test.lua`, and `m1a_local_file_test.lua` remain evidence for inherited/prototype behavior.

Do **not** treat the staged prototype's fixed-recipient/renderer-observation/client-authority behavior as final acceptance after M1B–M1I begin replacing it.

## Required tests by roadmap milestone

| Milestone / behavior | Best proof |
|---|---|
| M1A standard CC methods | `p0_cc_speaker_contract.lua` + source review against exact CC:T 1.120.0 |
| M1A HQ RAW backpressure | `RawFeedLifetimeTest` + `m1a_output_contract.lua` |
| M1A HQ latest-call-wins ownership | runtime ownership script; later extract owner state if it grows more complex |
| M1A speaker/world cleanup | source lifecycle review + Minecraft break/reload acceptance |
| M1B media asset lifetime/refcount | pure asset-manager/state tests |
| M1C staging/import path, quota, partial cleanup | filesystem/mount unit/component tests + CC Lua import runtime |
| M1D media metadata/duration | deterministic fixtures for MP3/OGG/WAV/etc.; unsupported-format tests |
| M1E server-authoritative playback | pure state-machine tests: pause/resume/seek/loop/EOF/no-listener/late-listener generation replacement |
| M1F range transfer | pure bounds/window/request tests + transfer component test; verify bounded memory/IO |
| M1G client cache | temp-directory tests: partial resume, atomic complete, corruption/size rejection, LRU cleanup |
| M1H incremental decode | bounded fixture decode tests, EOF/cancel/resource-reload distinction, asynchronous seek behavior |
| M1I dynamic range rendering | two-position/dimension/chunk runtime; entering late, leaving, stopping away, returning |
| M1J multispeaker sync | shared-clock tests + partial-visibility runtime; no expected-member barrier |
| M1K shared decode fan-out | bounded per-renderer buffer tests; slow tap drops stale decoded PCM only |
| M1L legacy finite migration | old public byte APIs regression against the same asset/playback engine |
| M1M RAW cleanup/future pause semantics | raw feed state-machine/runtime, only if semantics are extended beyond M1A |
| M1N sound category/F3+T/VS2 | consolidated Minecraft runtime |
| M1O lifecycle | block removal, Level unload, integrated restart, dedicated stop, detach, cache cleanup |
| M2 progressive finite playback | missing-range/prebuffer/seek-to-not-yet-downloaded tests |
| M3 SPR | positional/acoustic integration regression + many-source performance |
| M4 live streams | HLS sequence progression, bounded TS producer, unsupported-codec failure, reconnect lifecycle |

## Explicitly retired test targets

Do not spend new test effort proving correctness of these prototype concepts unless preserving a historical regression fixture requires it:

- renderer anchor failover as canonical finite authority;
- successful-renderer sets controlling the server clock;
- no-renderer timeout as the finite playback policy;
- fixed historical playback-recipient ownership;
- expected global sync-group/tap count;
- an unbounded whole-track decoder queue as the final finite engine.

The accepted architecture removes those concepts instead.

## CI expectations

Every pure Java test must run in the existing Java 21 matrix for:

- NeoForge 21.1.247;
- NeoForge 21.1.248.

`clean build` must remain green and packaged-resource verification must continue to assert the bundled ComputerCraft ROM module.

Do not add intentionally failing Java tests for runtime-known bugs to main CI. Keep runtime-pending assertions in Lua acceptance scripts until the corresponding logic is extractable.

## Runtime batching rule

Do not launch Minecraft for every source patch.

For a coherent milestone:

1. exact branch HEAD green on both CI versions;
2. relevant pure state tests green;
3. run the milestone's small Lua contract scripts;
4. run one consolidated Minecraft acceptance batch for sound/range/resource behavior;
5. only then label behavior runtime-proven.
