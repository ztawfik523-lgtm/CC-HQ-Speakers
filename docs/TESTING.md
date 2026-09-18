# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, the tests which actually exist, and package structure. It does **not** prove audibility, positional attenuation, sound-engine lifecycle, reload behavior, loop behavior, or server-thread safety under blocking external operations.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Recorded checkpoints

M1E final hardening: `521d4323d9216c8a99e8ec60426997c3330c4068`, CI `34757923455`. Final focused Minecraft M1E acceptance was explicitly skipped.

M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`, CI `34763362365`. Source/test/CI/package and deterministic/component acceptance are complete; focused Minecraft M1F transport acceptance is unrecorded.

M1G final source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`. NeoForge 21.1.247 and 21.1.248 both passed build, deterministic tests, packaged-mod verification, and artifact upload.

## What is actually integrated now

M1G is complete at source/test/CI/package/component level.

Current source wires:

- protocol v7 MP3/common-WAV decode descriptor plus authoritative `decodeRevision`;
- STATE-only nonterminal transition authority; explicit STOP remains distinct;
- exact WAV and E1 MP3 anchors;
- starvation-aware `FiniteEncodedInputStream` and bounded sliding `FiniteRangeWindow`;
- bounded `FinitePcmQueue`;
- progressive common-WAV conversion;
- packaged-JLayer MP3 decode with earlier-anchor/pre-target discard;
- `FinitePcmReadAdapter` renderer-read policy used by `FinitePcmAudioStream`;
- positional `FiniteSpeakerSound` through BLOCKS;
- fixed 32-block live channel attenuation independent of HQ gain;
- global-volume-zero decoder/render/range hibernation;
- local renderer activation observation/rejoin behavior and `canStartSilent()`;
- simple ordinary replay after physical EOF while authoritative looping remains enabled;
- whole-owner staging leftover cleanup.

### Strong deterministic M1G coverage

The final suite materially covers:

- M1F range validation/read service/window/transport, including sliding and bounded memory;
- media-asset lifetime/retry behavior in active paths;
- common-WAV analysis/layout narrowing and conversion;
- decode descriptor and anchor selection;
- starvation-aware encoded input, cancellation and lost-wakeup protection;
- bounded PCM queue producer backpressure/nonblocking consumer states;
- progressive WAV decode;
- server finite state/authority behavior;
- pure `FiniteDecodeCoordinator` revision/stale-state/local-worker invalidation rules;
- a real synthetic mono 44.1 kHz MP3 fixture through packaged JLayer;
- MP3 initial starvation, repeated bounded refill, multiple encoded-window slides, non-silent PCM, and pre-target discard;
- renderer-facing DATA, bounded starvation silence, physical EOF, and cancellation through the pure read adapter used by `FinitePcmAudioStream`;
- whole-owner staging cleanup, including best-effort deletion of remaining entries after one deletion fails.

A direct `FinitePcmAudioStreamTest` is intentionally not used because NeoForge's ordinary JUnit source set does not expose Minecraft's client-only `AudioStream` interface. Its non-Minecraft read policy is tested in the shared pure adapter; the actual Minecraft adapter compiles and packages on both supported targets.

### Source-correctness closeout

The final re-audit confirms:

- semantic seek is self-describing through `decodeRevision`;
- cancellation invalidates the old local worker identity before waking it;
- stale revision STATE is rejected;
- ordinary same-revision STATE preserves a healthy decoder/window even when time-derived anchors move;
- same-anchor semantic seek still restarts codec state because revision, not anchor identity, controls restart;
- correctness no longer depends on SEEK CONTROL ordering;
- fixed attenuation and server relevance both remain 32 blocks;
- global HQ volume zero consumes no finite range/decode/render resources while server time advances;
- ordinary loop replay is implemented without claiming gapless behavior;
- persistent staging leftovers are reclaimed only at whole-owner cleanup.

### Runtime evidence boundary

Actual Minecraft SoundManager/OpenAL behavior is not proven by Gradle CI. The focused audible checklist below remains useful and KI-046 remains unrecorded.

M1G is nevertheless closed as the engineering milestone, consistent with the project's separate tracking of source/component completion versus focused runtime evidence.

## Cross-cutting safety/hardening tests

### KI-062 — blocking DNS must not stall server tick/cleanup monitor

The confirmed defect is a synchronized dynamic stream path which may call blocking DNS while holding the same composite monitor used by `tickOwnership()` and synchronized `cleanup()`.

A regression test should prove ownership ordering without holding the server-tick/lifecycle monitor across external DNS/I/O. Where direct resolver injection is practical, block the resolver deliberately and prove tick/cleanup progress does not depend on it.

Do **not** expand this test into an M3 live-stream functional rewrite. `audioPrepareStaged(...)` is not part of this synchronized monitor claim.

### KI-063 — replacement-before-admission

Cover both main forms:

- rejected RAW submission because capacity/admission fails must leave the existing valid HQ source alive;
- failing `audioPlayPrepared(...)` validation/start must leave the previous source alive unless replacement was actually admitted.

Also test successful replacement still performs the intended ownership transfer/stop exactly once.

### KI-054 — shutdown lock/retry failure

Add deterministic failure injection for both shutdown stages:

- completed-file deletion failure during `MediaAssetStore.close()` must have a defined retry/cleanup outcome;
- `FiniteRangeReadService.close()` failure before `store.close()` must not leave an unrecoverable root lock/registry entry across same-JVM integrated-server restart.

Next-start orphan pruning is not sufficient proof while an old lock remains held.

### KI-064 — MediaAsset import progress and rename fallback

Add tests for:

- a channel which returns zero repeatedly cannot spin forever;
- eventual progress after a bounded number of zero reads is handled correctly if that behavior is supported;
- pathological no-progress input fails deterministically;
- `AtomicMoveNotSupportedException` falls back to an appropriate same-filesystem non-atomic move without publishing a partial asset;
- failure cleanup still removes `.part`/unpublished files and preserves quota accounting.

### Practical media-bound hardening

The modern WAV parser currently accepts any positive `int` sample rate. Before release hardening, define and test a practical accepted sample-rate range so malformed/extreme WAV metadata cannot reach the Minecraft/OpenAL streaming path with absurd buffer/rate values. This is not the first M1G blocker.

## Lua/runtime scripts: current versus historical

Do not treat every script in `scripts/` as a current M1G gate.

- `p0_cc_speaker_contract.lua` remains useful for standard CC:T compatibility.
- `m1e_server_authority_test.lua` remains useful for server-authority behavior, but it is not an audibility test.
- `m1d_media_analysis_test.lua` is historical: it expects the old broad M1D prepared format surface and is **not** a current modern M1G acceptance script.
- `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern `hq.playFile()` / prepared path.
- `m0-smoke.lua` is broad legacy smoke coverage, not proof of M1G prepared playback.

Until dedicated modern scripts exist, use the focused manual matrix below and record the exact branch/commit/JAR.

## Focused Minecraft M1G acceptance

Before calling M1G audibly proven, record at least:

1. `hq.playFile()` MP3 becomes audible;
2. supported common WAV becomes audible;
3. playback starts after bounded prebuffer rather than complete-track transfer;
4. long-track encoded + decoded RAM remains bounded;
5. pause/resume audibly follows server state without gratuitous decoder restarts;
6. forward/backward/repeated seek audibly rejoins current server time and never dies from expected decoder cancellation;
7. MP3 seek works through E1 pre-roll without decoder corruption;
8. stop/replacement cancels old sound promptly, while rejected replacement leaves valid current playback alive;
9. temporary starvation/refill does not become permanent EOF;
10. sound is positional/attenuated from the physical `computercraft:speaker`;
11. live volume changes alter gain but do **not** enlarge the modern finite 32-block core range;
12. global volume zero stops local decode/render/range work while canonical server time continues, and unmute rejoins current time;
13. the fixed server relevance/channel attenuation boundary behaves coherently around 32 blocks;
14. ordinary loop replay plays the same media again after EOF; a normal restart gap is acceptable;
15. standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain compatible;
16. no complete client song `.part/.media` file is created;
17. low-level/interrupted staging leftovers are reclaimed when the speaker staging owner is destroyed/recreated.

Do not require sample-gapless MP3 or permanent-source loop continuity.

Record exact source commit, docs commit if relevant, JAR SHA-256, target versions, fixture facts, pass/fail sections, relevant client/server logs, test-instance type, and network compression state if throughput is measured.

## M1H evidence boundary

Do not require M1G to prove full late-entry/proactive-leave/return-rejoin/dimension/resource-reload/general-underrun/final-VS2 lifecycle.

For later moving-VS2 proof, remember modern STATE does not carry x/y/z. Test whichever M1H approach is actually selected: client-side ship transform from BEGIN block coordinates (mirroring the legacy path) or an explicit position-update protocol. Do not assume one before implementation.

## Current evidence language

```text
M1E source/test/CI: PASS
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: PASS
M1F focused Minecraft transport: unrecorded
M1G source/test/CI/package/component: PASS at fa679ffcb81a66fd99ab6be8e6d6b77895fbc542
M1G protocol v7 decoder/reanchor coordination: PASS at source/component level
M1G fixed-range volume/start behavior: PASS at source/component level
M1G real-MP3 progressive integration coverage: PASS
M1G renderer-read policy coverage: PASS
M1G staging cleanup: PASS
M1G ordinary replay: implemented
M1G audible runtime PASS: unrecorded (KI-046)
```


