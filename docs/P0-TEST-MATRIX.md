# P0 regression test matrix

## Purpose

The repo should not require a full Minecraft launch to rediscover every basic lifecycle bug.

Use three layers:

1. pure/unit tests for deterministic codec/parser/cursor/state logic;
2. state-machine/component tests for server/client lifecycle logic as it becomes extractable;
3. one consolidated Minecraft/Lua acceptance pass for behavior which depends on real CC:T/Minecraft sound plumbing.

## Tests present after P0 prep

### Java unit tests

`FiniteAudioTrackTest`

Covers:

- duration/frame-aligned reads;
- backward/clamped seek;
- exact seek-to-duration cursor state;
- loop cursor wrap;
- loop-disable cursor continuity;
- independent renderer forks;
- invalid PCM construction.

Note: this does **not** prove the server logical clock is rebased when loop is disabled. That bug belongs to server/client state-machine tests plus runtime acceptance.

`HLSPlaylistParserTest`

Covers:

- live media playlist parsing;
- `EXT-X-MEDIA-SEQUENCE`;
- relative segment URL resolution;
- master variant resolution;
- audio-only codec classification.

This preserves the parser facts needed when stream progression is fixed. It does not claim current `StreamingAudioSource.streamHLS()` advances sliding windows correctly.

### Why packet tests are not in the ordinary unit source set

An initial P0 pass added direct unit tests for `HQSpeakerAudioPacket` and `HQSpeakerStatusPacket`. The normal Gradle `test` source set cannot compile those classes because Minecraft's generated `CustomPacketPayload` classes are not on its test compile classpath, even though production `compileJava` has the NeoForge/Minecraft classpath.

P0 deliberately does **not** distort the test configuration just to instantiate packet classes. Protocol validation should instead be tested after the pure validation/state rules are extracted from Minecraft-bound packet classes, or in a proper NeoForge component/game-test setup when such a harness is justified.

The first CI failure which exposed this limitation is part of the P0 evidence, not a production-code failure.

### Lua/runtime contract tests

`scripts/p0_cc_speaker_contract.lua`

Intended acceptance:

- idle speaker does not spam `speaker_audio_empty`;
- standard `stop()` exists;
- `playNote("harp")` works with omitted optional arguments;
- `playSound` uses a real requested sound and returns a boolean;
- standard `playAudio` accepts signed 8-bit data;
- immediate second raw buffer is rejected while first pending;
- `speaker_audio_empty` permits a retry.

At reviewed M1 HEAD this test is expected to expose known failures. Do not weaken the test to make the old implementation pass.

`scripts/p0_finite_regression.lua <mp3>`

Intended acceptance:

- loop wraps;
- disabling loop does not jump logical position to duration;
- seek exactly to duration reaches `ended`, not renderer-start error;
- `speakIsPlaying()` is false after end.

At reviewed M1 HEAD this is expected to expose known finite lifecycle defects.

## Required tests as implementation proceeds

| Issue | Best proof |
|---|---|
| standard playNote/playSound/stop | Lua runtime + focused server tests where practical |
| playAudio backpressure/event | extracted raw buffer state-machine unit test + Lua runtime |
| finite/raw collision | client/server state-machine test after D1 |
| stop/control range ownership | recipient policy component test after D2 + 2-position runtime |
| raw idle source cleanup | client lifecycle component/runtime |
| loop disable clock | finite semantic clock unit/component test |
| anchor failover | server finite state-machine test with two renderer UUIDs |
| generation promotion | server finite queue test with asymmetric renderer reports |
| no-renderer policy | server timeout/session test after policy chosen |
| exact-duration seek | client finite control test + Lua runtime |
| decoder backlog | executor saturation/cancellation test after D3 |
| packet/status validation | extracted pure validation test or NeoForge component test |
| stream double volume | unit test of gain ownership after refactor |
| HLS sliding window | playlist-sequence progression unit test |
| direct TS incremental output | demux/producer test with bounded synthetic stream |
| unsupported TS codec | unit test asserts explicit failure/no PCM |
| stream pause/reconnect | stream lifecycle component + runtime direct MP3 |
| partial sync | sync-group test after D4 |
| stale provider cache | lifecycle/cache test |
| F3+T | consolidated Minecraft runtime |
| VS2 motion | optional runtime only |
| SPR | later M3 regression suite |

## CI expectations

P0 pure Java tests must run in the existing `.247` and `.248` Gradle matrix because `clean build` executes tests.

Do not add intentionally failing Java tests for known current bugs to main CI.

Known-bug expectations which require future implementation should live in this matrix and runtime scripts until the relevant state logic is refactored into testable components.

## Runtime batching rule

Do not launch Minecraft for every patch.

After a coherent stabilization batch:

1. both CI builds green;
2. targeted Java tests green;
3. run `p0_cc_speaker_contract.lua`;
4. run `p0_finite_regression.lua`;
5. run the consolidated updated M1 test;
6. only then do stream/multi-client/sync/VS2 checks relevant to that batch.
