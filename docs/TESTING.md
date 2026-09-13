# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, the tests which actually exist, and package structure. It does **not** prove audibility, positional attenuation, sound-engine lifecycle, reload behavior, or loop-boundary quality.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Recorded checkpoints

M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`, CI `34757923455`. Final focused Minecraft M1E acceptance was explicitly skipped.

M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`, CI `34763362365`. Source/test/CI/package and deterministic/component acceptance are complete; focused Minecraft M1F transport acceptance is unrecorded.

M1G current green integrated source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`. Both target NeoForge versions passed build/tests/package verification/artifact upload.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both targets.

## What is actually integrated now

M1G is no longer a primitive-only/pre-integration checkpoint. Current source wires:

- protocol v6 MP3/common-WAV decode descriptor;
- exact WAV and E1 MP3 anchors;
- `FiniteEncodedInputStream` into `HQFiniteMediaClient` decode epochs;
- `FinitePcmQueue` into progressive WAV/MP3 decode;
- progressive common-WAV conversion;
- progressive JLayer MP3 decoding with pre-target discard;
- `FinitePcmAudioStream` and positional `FiniteSpeakerSound`;
- pause/resume/volume projection;
- seek/replacement/stop decoder/PCM/renderer cancellation.

Any older statement that the decoder/renderer is “not integrated yet” is obsolete.

## Strong existing deterministic coverage

Current tests materially cover:

- M1F range validation/read service/window/transport, including sliding and bounded memory;
- media-asset lifetime/retry behavior in active paths;
- common-WAV analysis/layout narrowing;
- common-WAV sample conversion;
- decode descriptor and anchor selection;
- starvation-aware `FiniteEncodedInputStream`, cancellation and lost-wakeup protection;
- bounded `FinitePcmQueue`, producer backpressure and nonblocking consumer states;
- progressive WAV decode;
- server finite state/authority behavior;
- storage-limit overflow protection;
- CC/path/config helper behavior.

## M1G deterministic gaps still open

### Real MP3 progressive integration

`ProgressiveMp3DecoderTest` currently verifies helper arithmetic/downmix behavior, but does not run a known real MP3 fixture through the full packaged JLayer decode loop.

Add coverage which proves:

- real encoded MP3 produces non-silent PCM;
- decode begins from bounded range data rather than a complete file;
- decoding continues across multiple window slides/refills;
- temporary encoded starvation blocks only the decoder worker and later resumes;
- true EOF differs from cancellation;
- output PCM stays bounded;
- E1 earlier-anchor decode suppresses pre-target PCM;
- same-anchor semantic seek still creates fresh codec state.

### Renderer adapter

There is currently no focused `FinitePcmAudioStreamTest` in the client test tree.

Add pure deterministic coverage for:

- bounded/frame-aligned reads;
- STARVED -> short nonblocking silence, not EOF;
- true queue EOF -> terminal stream EOF;
- cancellation/close behavior;
- no network/disk/codec blocking from renderer reads.

Actual Minecraft SoundManager/channel behavior still requires runtime acceptance.

### KI-053 regression

Add a test for the audited same-anchor STATE/window-reset hazard:

1. establish a decoder epoch;
2. slide the encoded window forward;
3. apply an ordinary STATE carrying the same coarse anchor;
4. prove the existing decoder cursor/window are not rewound or invalidated;
5. separately prove a semantic SEEK with the same anchor still restarts decoder state.

### KI-054 shutdown deletion failure

Add deterministic failure injection for `MediaAssetStore.close()` so a failed completed-file deletion has a defined retry/cleanup outcome rather than relying only on next-start orphan pruning.

## Lua/runtime scripts: current versus historical

Do not treat every script in `scripts/` as a current M1G gate.

- `p0_cc_speaker_contract.lua` remains useful for standard CC:T compatibility.
- `m1e_server_authority_test.lua` remains useful for server-authority behavior, but it is not an audibility test.
- `m1d_media_analysis_test.lua` is historical: it expects the old broad M1D prepared format surface and is **not** a current modern M1G acceptance script.
- `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs (`speakMp3`/`speakOgg`/etc.), not the modern `hq.playFile()` / prepared path.
- `m0-smoke.lua` is broad legacy smoke coverage, not proof of M1G prepared playback.

Until dedicated modern scripts exist, use the focused manual matrix below and record the exact branch/commit/JAR.

## Focused Minecraft M1G acceptance

Before calling M1G audibly proven, record at least:

1. `hq.playFile()` MP3 becomes audible;
2. supported common WAV becomes audible;
3. playback starts after bounded prebuffer rather than complete-track transfer;
4. long-track encoded + decoded RAM remains bounded;
5. pause/resume audibly follows server state;
6. forward/backward seek audibly rejoins current server time;
7. MP3 seek works through E1 pre-roll without decoder corruption;
8. stop/replacement cancels old sound promptly;
9. temporary starvation/refill does not become permanent EOF;
10. sound is positional/attenuated from the physical `computercraft:speaker`;
11. standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain compatible;
12. no complete client song `.part/.media` file is created.

Loop-wrap acceptance is blocked until the owner chooses KI-051 L1/L2/L3 and that policy is implemented.

Record exact source commit, docs commit if relevant, JAR SHA-256, target versions, fixture facts, pass/fail sections, relevant client/server logs, test-instance type, and network compression state if throughput is measured.

## M1H evidence boundary

Do not require M1G to prove full late-entry/proactive-leave/return-rejoin/dimension/resource-reload/general-underrun/final-VS2 lifecycle. Those remain M1H.

## Current evidence language

```text
M1E source/test/CI: PASS
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: PASS
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: PASS at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G real-MP3 progressive integration coverage: incomplete
M1G focused renderer-adapter coverage: incomplete
M1G loop-wrap policy: owner choice required
M1G audible runtime PASS: unrecorded
```
