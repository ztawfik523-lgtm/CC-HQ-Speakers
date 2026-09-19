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

M1G final source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`. Post-M1G hardening checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`, CI `35406595856`.

M1H-1 listener-membership source checkpoint: `84e7bab99009e5871934a960908945ceb00a10a9`, CI `35410830197`. Both NeoForge 21.1.247 and 21.1.248 passed build, deterministic tests, packaged-mod verification, and artifact upload.

M1H-1 artifacts:

- 21.1.247 artifact `10574726822`, SHA-256 `62e8f090ddfe5466db3807dd1d78fd738c355887e6e938f811834d9630078cc9`;
- 21.1.248 artifact `10573826903`, SHA-256 `2538111207be632a1253a762f6a45218b6d7ca4b24c2739359b4f56c8e648555`.

M1H-2 recovery checkpoint: `aa72f0d2fc9f8cde53cd956389beca1743d06165`, CI `35411480844`. Both NeoForge targets passed build, deterministic tests, packaged-mod verification, and artifact upload.

M1H-2 artifacts:

- 21.1.247 artifact `10574298203`, SHA-256 `536399fbb03f1b8009f4abc0c1260a116e5376234f125d4ccd43a25c3263e370`;
- 21.1.248 artifact `10573757634`, SHA-256 `c496b3871cb7dae32d323dbdc3d10dd44efb8f9c012d18043a5a075285293790`.

M1H-3 moving-source checkpoint: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`, CI `35451236630`. Both NeoForge targets passed the existing deterministic suite, source compilation, packaged-mod verification, and artifact upload.

M1H-3 artifacts:

- 21.1.247 artifact `10586971196`, SHA-256 `ad336ca13f243aa19251f5e4649ab59066e60c91c40a4de6aeab49f5ca4d8a5c`;
- 21.1.248 artifact `10587375399`, SHA-256 `31e553d2ed35380958bceb23ecf2479d46b03ad5bcdf8eed82440477b5b760f8`.

Package verification explicitly checks that Sable Companion 1.6.0 is embedded in the candidate JAR.

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

Actual Minecraft SoundManager/OpenAL behavior is not proven by Gradle CI. A separate focused **M1G audible/core runtime PASS was recorded on 2026-09-19** using NeoForge 21.1.247 integrated singleplayer, resolving KI-046. NeoForge 21.1.248 remains CI/package verified rather than manually runtime-verified.

The broader checklist below also contains post-M1G hardening and M1H-adjacent checks. Those remain useful release evidence but do not keep KI-046 or the M1G core milestone open.

## Cross-cutting safety/hardening tests

### Post-M1G hardening evidence

KI-062/063/054/064 are resolved at source/component level at hardening checkpoint `3d30ce4564de749f32171666df65de739b08ad77`.

Deterministic coverage added/retained includes:

- bounded media-import zero-read no-progress failure;
- temporary zero-read recovery;
- unsupported-`ATOMIC_MOVE` fallback;
- failed close deletion retaining bookkeeping/quota/root-lock state until a later successful retry;
- nonblocking range-service `beginClose()` immediately rejecting new work before final drain;
- existing range shutdown retry tests.

Exact-source re-audit additionally verifies:

- URL DNS validation executes outside both the ownership monitor required by `tickOwnership()`/cleanup and the command-order lock; validated single-speaker stream commit rejoins the short command lock only after DNS returns, and a newer playback/control command invalidates the older pending stream start;
- prepared finite replacement is fully admitted/retained/constructed before current ownership is stopped;
- RAW replacement validates/converts before current ownership is stopped;
- the same stopping server cannot reopen media services after shutdown begins;
- a later server instance retries old failed closing services before opening the media-store root.

## M1H-1 listener membership evidence

Deterministic tests now cover the membership transitions used by the server:

- outside -> inside produces one join;
- remaining inside produces no new transition;
- inside -> outside produces one leave;
- returning produces a new join;
- a failed BEGIN or STOP projection remains retryable instead of being silently forgotten;
- disconnect/dimension-style disappearance prunes the missing listener without disturbing listeners that remain;
- stop/replacement/terminal cleanup clears all membership.

The server implementation uses those transitions every server tick. A late entrant gets BEGIN + current STATE immediately. The client's normal READY reply may then cause one extra STATE, which is intentionally accepted because same-revision STATE does not restart healthy playback.

### Focused Minecraft M1H-1 acceptance still required

CI does not prove the live client/audio behavior. In a real NeoForge 21.1.247 Minecraft run, verify:

1. start prepared playback while the player is farther than 32 blocks away: no sound/client admission;
2. walk inside 32 blocks: playback joins near the song's current time, not from zero;
3. remain inside for several seconds: no repeated restart or audible BEGIN spam;
4. walk outside: sound stops promptly and client range/decode/render work stops;
5. walk back inside: playback rejoins the current server time cleanly;
6. change dimension while admitted: old playback is cleaned up; returning to the relevant dimension/range can rejoin;
7. stop or replace playback while admitted: old sound is cleaned up;
8. let non-looping playback end naturally: client playback disappears cleanly.

## Lua/runtime scripts: current versus historical

Do not treat every script in `scripts/` as a current M1G gate.

- `p0_cc_speaker_contract.lua` remains useful for standard CC:T compatibility.
- `m1e_server_authority_test.lua` remains useful for server-authority behavior, but it is not an audibility test.
- `m1d_media_analysis_test.lua` is historical: it expects the old broad M1D prepared format surface and is **not** a current modern M1G acceptance script.
- `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern `hq.playFile()` / prepared path.
- `m0-smoke.lua` is broad legacy smoke coverage, not proof of M1G prepared playback.

A dedicated external runtime kit was used for the 2026-09-19 M1G pass. Keep the matrix below as the broader regression/release checklist and record exact branch/commit/JAR for future reruns.

## Focused Minecraft M1G acceptance

**Recorded core audible PASS (2026-09-19, NeoForge 21.1.247 integrated singleplayer):** modern WAV/MP3 audibility, pause/resume, repeated seek/reanchor, MP3 seek, prepared-asset lifetime, float32 WAV, natural EOF, ordinary loop replay, positional attenuation, fixed 32-block range behavior, volume >1 not extending range, stop, and loop-safe global-volume-zero hibernation/unmute. The focused mute/unmute retest kept the authoritative loop in `playing` state at volume 0 for more than five seconds, was manually confirmed silent, then returned to audible playback after unmute.

The remaining matrix is broader than the core KI-046 gate and should still be used for future regression/release work:

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

### M1H-1 — listener membership

Deterministic tests should prove:

1. outside at playback start is not admitted;
2. entering range during active playback admits exactly once;
3. leaving range removes membership and targets client cleanup;
4. re-entry bootstraps current authoritative position, not zero;
5. disconnect/removal and dimension mismatch prune membership;
6. unchanged membership does not spam BEGIN/STOP;
7. stop/replacement/natural terminal clears membership coherently.

This focused Minecraft walk-in/walk-out/return test was deferred by the owner on 2026-09-19 and remains in the backlog.

### M1H-2 — recovery

**Source/test/CI/package PASS at `aa72f0d2fc9f8cde53cd956389beca1743d06165`, CI `35411480844`.**

Deterministic coverage now proves:

- continuous PCM starvation becomes a recovery condition after the selected timeout and real PCM clears it;
- recovery READY retries at a bounded interval until authoritative STATE arrives;
- normal STATE snapshots do not hide ongoing starvation;
- pause/restart/hibernate can clear starvation deliberately;
- nanoTime negative values/wraparound do not break retry timing;
- the same server decode revision rebuilds correctly after the local decoder was discarded.

Exact-source review additionally verifies the SoundEngine-close race: an externally closed renderer stream requests recovery instead of reporting a fatal decoder failure.

Focused Minecraft resource reload, renderer-loss, and long-starvation recovery remain in the runtime backlog.

### M1H-3 — moving source

**Source/CI/package PASS at `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`, CI `35451236630`.**

The chosen implementation deliberately stays small:

- Sable Companion resolves Sable/Aeronautics sublevel block positions into world space;
- existing VS2 transform support remains as the other moving-world path;
- the modern finite client updates the sound position locally every client tick;
- the server resolves the same position for listener membership;
- no position packet or protocol change exists;
- the packaged JAR is checked for the embedded Sable Companion dependency.

A plain JUnit test that tried to load the jar-in-jar service provider was removed because the plain test runtime does not reproduce NeoForge's jar-in-jar loading. The correct deterministic evidence here is normal source compilation plus explicit package-content verification; no extra test-only dependency setup was added just to make that artificial test work.

Focused real-Minecraft Sable/Aeronautics and VS2 movement acceptance is deferred to the runtime backlog.

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
M1G focused audible/core runtime PASS: recorded 2026-09-19 on NeoForge 21.1.247 (KI-046 resolved)
M1H-1 listener membership source/test/CI/package: PASS; focused Minecraft test deferred to backlog
M1H-2 recovery source/test/CI/package: PASS; focused Minecraft reload/loss/starvation test deferred to backlog
M1H-3 Sable/VS2 moving-source source/CI/package: PASS; focused Minecraft movement test deferred to backlog
```


