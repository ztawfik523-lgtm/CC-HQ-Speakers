# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, the tests that actually exist, and package structure. It does **not** prove behaviors which are not exercised by those tests, and it does not prove audibility, renderer lifecycle, real network timing, reload behavior, or positional audio.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Current checkpoint after reevaluation

Current Java/source head remains:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI `34731827907` passed both target NeoForge jobs, including build/tests, packaged-mod verification, and artifact upload.

This remains valid evidence.

The interpretation changed:

- M1E final focused Minecraft PASS was skipped/unrecorded;
- M1F focused Minecraft transport acceptance is unrecorded;
- the original M1F deterministic acceptance matrix is only partially covered by current tests;
- source review found shared exception/lifetime failure-path defects not covered by current tests.

Therefore do not describe M1E as fully finalized or M1F as fully source/test/CI accepted.

## What current deterministic tests actually prove

### `FinitePlaybackClockTest`

Proves core finite-clock math:

- immediate progression after start;
- pause freezes position;
- resume continues from paused position;
- loop rebasing;
- non-loop exact-duration seek stability;
- loop exact-duration wrap;
- natural EOF detection for non-looping clocks.

It does **not** instantiate/test `HQFiniteMediaServer` itself.

### `FiniteRangeWindowTest`

Proves:

- bounded byte-array allocation independent of full asset size;
- non-zero/arbitrary initial encoded offset;
- request/accept exact bytes;
- re-anchor drops old data/demand;
- stale response rejection;
- NEED_DATA differs from TRUE_ASSET_EOF;
- request timeout/retry;
- malformed/wrong-length response recovery.

It does **not** prove a sliding progressive consume/discard operation because `FiniteRangeWindow` currently has no such operation.

### `FiniteRangeReadServiceTest`

Proves:

- exact arbitrary server byte range read;
- per-player outstanding request/byte accounting;
- accepted queued read takes its own MediaAsset retain;
- original owner can release while queued read keeps the asset alive;
- normal successful in-flight retain release;
- invalid bounds rejection;
- configured outstanding-limit rejection.

It does **not** currently prove:

- shutdown timeout/retry ordering against a live store close;
- final-reference deletion failure/retry semantics;
- stale playback generation/relevance discard, because that logic lives in `HQFiniteMediaServer` rather than the read service.

## Missing deterministic coverage reopened by the audit

The pre-M1F contract said M1F tests must prove at least request identity/bounds, relevance, outstanding limits, in-flight lifetime, stale completion discard, cancellation cleanup, shutdown ordering, no tick-thread reads, packet sizing, arbitrary offsets, bounded client RAM, no client whole-song files, availability semantics, and removal of the direct-staged path.

Current tests cover only part of that list.

Add dedicated proof for:

### M1E/server authority failure paths

- `HQFiniteMediaServer` start transition under successful delivery;
- packet-send failure cannot roll back/corrupt canonical server truth;
- `playPrepared()` failure rollback leaves no installed ghost session;
- stop/cleanup remains reliable when client delivery fails;
- playback reference is retried/preserved when final `MediaAssetStore.release()` fails.

### M1F server/request path

- source/generation/asset mismatch rejection;
- offset/length bound rejection at packet/server boundary;
- same-dimension/current-relevance gating;
- stale completion after replacement;
- stale completion after player leave/disconnect;
- server-side error only for authoritative asset/read failure, not client renderer failure.

### M1F lifecycle

- shutdown drains/cancels range work before store close;
- queued cancellation releases accounting and retains;
- retry after an initial shutdown timeout/failure;
- range-task final-reference release failure has an explicit recovery policy.

### M1F client boundary

- BEGIN alone does not issue byte-zero demand;
- authoritative STATE unlocks first demand;
- seek/re-anchor invalidates obsolete demand;
- client buffer can advance/discard consumed prefix while preserving useful unread data;
- repeated advance/refill remains bounded through data larger than one window;
- packet codec maximums match the configured range limit.

## Source-review facts versus test facts

Current source review supports the following, but they are not substitutes for the missing deterministic coverage:

- protocol v5 registers bounded range request/data packets;
- modern finite CHUNK/END packets are removed;
- modern client has no `.part/.media` whole-song path;
- `audioPlayStaged()` is removed;
- range reads are submitted to a dedicated bounded executor;
- completion rechecks current session/player relevance;
- `ServerMediaAssets.closeServer()` invokes range-service close before store close;
- first client range demand waits for STATE/anchor.

Keep these labeled as **source-reviewed**, not fully acceptance-tested.

## Focused Minecraft acceptance boundaries

### M1E

The existing script `scripts/m1e_server_authority_test.lua` checks normal-path immediate progression, pause/resume, exact-end behavior, generation replay, looping exact-end wrap, stop, and prepared release.

The owner explicitly chose not to run the final strengthened M1E script, so there is no recorded PASS.

Even if run later, it would not by itself inject Java packet-send/file-delete failures; deterministic failure-path tests are still needed for the reopened issues.

### M1F

A focused real-client/server check should observe:

- `hq.playFile`/prepared play starts canonical server state;
- client asks for bounded ranges rather than receiving a pushed complete file;
- no new modern `.part/.media` song appears in the game directory;
- initial request waits for server STATE;
- non-zero anchor/seek can cause non-zero encoded demand;
- replacement/stop prevents stale ranges from reviving old transport state;
- server remains responsive during reads;
- server stop drains range activity without media-store race.

M1F audibility is still not required.

## M1G proof boundary

M1G has not started and should not start until the owner chooses how to handle the reopened M1E/M1F work.

When started, it must add proof for:

- progressive MP3 decode from the bounded transport;
- temporary missing bytes are not decoder EOF;
- Layer III pre-roll;
- common WAV integer/float conversion;
- stereo-to-mono downmix and >2-channel rejection;
- bounded mono PCM queue;
- decoder cancellation/replacement;
- no sound-thread network/disk/decode blocking;
- actual positional audible Minecraft playback;
- pause/resume/seek/loop interaction with renderer projection.

## Evidence recording

For meaningful runtime acceptance record:

- exact commit;
- JAR SHA-256;
- NeoForge/CC:T versions;
- fixture facts;
- exact pass/fail sections;
- relevant client/server logs;
- full ATM10 versus reduced exact-stack instance;
- network compression state when throughput is measured.

Never infer runtime PASS from CI alone.
