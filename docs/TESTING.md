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

M1G current green integrated source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`. Both target NeoForge versions passed build/tests/package verification/artifact upload.

Documentation work after that checkpoint does not change the implementation baseline.

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

## Selected M1G target behavior which is not implemented yet

Tests must distinguish current source from selected target behavior:

- explicit server-authoritative decoder/re-anchor revision, likely protocol v7;
- fixed **32-block** core modern-finite range, with HQ volume changing gain rather than radius;
- global HQ volume zero hibernates local decode/render/range requests while canonical server time continues;
- looping is ordinary local replay after physical EOF while authoritative state still says looping; a normal restart gap is acceptable;
- no gapless MP3/LAME padding work, permanent-source loop engineering, dynamic volume-aware range, or SPR integration in M1G.

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

## M1G correctness work before runtime acceptance

### Decoder epoch / authoritative re-anchor coordination — KI-053/KI-056/KI-057

Do not fix KI-053 in isolation.

Deterministic coverage must prove:

- semantic SEEK increments/uses the selected authoritative decoder-reanchor revision;
- cancelling a decoder invalidates the old worker token **before** cancellation can wake/report failure;
- expected old-worker cancellation cannot remove/fail the replacement session;
- ordinary authoritative STATE reconciliation does not tear down a healthy decoder merely because its time-derived WAV/MP3 anchor differs;
- an ordinary same-anchor STATE after the encoded window slid forward does not reset/rewind the active window;
- semantic seek always creates fresh codec state even when the selected encoded anchor byte is unchanged;
- authoritative seek/reanchor correctness does not depend on a CONTROL packet arriving before STATE;
- repeated seek/cancel/state ordering does not revive stale decoder/PCM/renderer epochs;
- stale/out-of-order revision state is rejected or reconciled deterministically.

One implementation choice remains: finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets may survive only as latency hints, or STATE may become the sole transition authority. Tests must match whichever shape is chosen, but correctness may not depend on duplicate packet ordering.

A small pure coordination/state object extracted from `HQFiniteMediaClient` could make these cases easier to prove without booting Minecraft; that is a structure/testing option, not a currently implemented component.

### Real MP3 progressive integration — KI-055

`ProgressiveMp3DecoderTest` currently verifies helper arithmetic/downmix behavior but does not run a known real MP3 fixture through the full packaged JLayer decode loop.

Add coverage which proves:

- real encoded MP3 produces non-silent PCM;
- decode begins from bounded range data rather than a complete file;
- decoding continues across multiple window slides/refills;
- temporary encoded starvation blocks only the decoder worker and later resumes;
- true EOF differs from cancellation;
- output PCM stays bounded;
- E1 earlier-anchor decode suppresses pre-target PCM;
- same-anchor semantic seek creates fresh codec state through the revision model.

True codec-level gapless behavior is not an M1G test requirement.

### Renderer adapter — KI-055/KI-060

There is currently no focused `FinitePcmAudioStreamTest` in the client test tree.

Add pure deterministic coverage for:

- bounded/frame-aligned reads;
- STARVED -> short nonblocking silence, not EOF;
- true queue EOF -> terminal stream EOF;
- cancellation/close behavior;
- no network/disk/codec blocking from renderer reads.

Renderer-start coordination must prove that a failed/deferred `SoundManager.play(...)` does not leave a permanent `rendererStarted=true` latch with no active sound.

Actual Minecraft SoundManager/channel behavior still requires runtime acceptance.

### Fixed-range volume/attenuation — KI-058/KI-059/KI-060

The range choice is no longer open for M1G. The selected core contract is fixed 32 blocks.

Deterministically cover where possible:

- finite volume changes logical sound gain without restarting a healthy decoder epoch;
- the modern finite live channel installs/retains the selected fixed attenuation distance rather than using volume to enlarge it;
- volume >1 does not change modern finite server relevance or the fixed channel distance;
- volume zero triggers the selected local hibernation path rather than continued decode/range consumption;
- unmute rebuilds/reanchors to current canonical time rather than replaying stale buffered audio;
- a client-local MASTER/BLOCKS mute does not alter server transport policy;
- locally silent renderer creation/recovery does not get permanently latched off.

Minecraft runtime coverage should exercise volume below/at/above one while confirming that the fixed core boundary stays fixed.

### Ordinary replay looping — KI-051

The architecture choice is no longer open. Test simple replay only:

- physical decoder EOF while authoritative `looping=true` causes a fresh local decoder/render iteration from the beginning;
- a normal restart gap is acceptable;
- no local replay occurs after loop has been disabled;
- seek/replacement/stop/revision change supersedes stale loop-restart work;
- loop replay does not introduce a second client wall-clock authority;
- WAV and MP3 replay both work without requiring gapless metadata/prefetch/permanent-source machinery.

### Staging lifecycle cleanup — KI-061

Create leftover files through the writable staging mount, then destroy/cleanup the whole `HQMediaStaging` owner and prove those files are removed rather than becoming unreachable under the old random staging ID.

Coverage must distinguish whole-owner cleanup from ordinary computer detach: one attached computer detaching must not erase a staging mount still shared with another attached computer. Cleanup failure should be surfaced/logged without corrupting prepared MediaAsset ownership.

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
M1G integrated source/tests/package: PASS at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G fixed-range volume/start correctness: open KI-058/KI-060; KI-059 policy selected
M1G staging lifecycle cleanup: open KI-061
Cross-cutting synchronized-DNS/main-thread safety: open KI-062
Cross-source replacement admission safety: open KI-063
Storage import progress/rename hardening: open KI-064
Shutdown lock/retry hardening: open KI-054
M1G real-MP3 progressive integration coverage: incomplete
M1G focused renderer-adapter coverage: incomplete
M1G ordinary replay policy: selected, not implemented
M1G audible runtime PASS: unrecorded
```
