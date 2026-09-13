# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, tests which actually exist, and package structure. It does **not** by itself prove audibility, renderer lifecycle, live network timing, reload behavior, or positional audio.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Current M1E checkpoint

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

CI:

`34757923455`

Both target NeoForge jobs passed:

- build/compile;
- complete project tests;
- packaged-mod verification;
- artifact upload.

Baseline 21.1.247 JAR SHA-256:

`da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`

The final focused Minecraft M1E script was previously skipped by explicit owner decision, so there is no recorded final runtime PASS.

## Deterministic M1E evidence

### `FinitePlaybackStateMachineTest`

Tests the production semantic component used by `HQFiniteMediaServer` and proves:

- immediate PLAYING state and clock progression;
- pause freezes canonical position;
- resume continues from the paused position;
- non-looping exact-duration seek becomes terminal ENDED;
- looping exact-duration seek wraps to zero and remains active;
- natural EOF becomes canonical ENDED;
- terminal controls are rejected;
- server ERROR freezes the canonical position;
- error detail remains stable;
- volume clamps to the supported range and rejects non-finite inputs.

### `FinitePlaybackClockTest`

Separately proves core clock arithmetic:

- immediate progression without renderer handshake;
- pause/resume;
- loop rebasing;
- exact-end behavior;
- non-loop natural EOF detection;
- looping clocks never report natural EOF.

### `BestEffortProjectionTest`

Proves projection failures are contained rather than escaping canonical work, including the case where failure reporting itself throws.

Production `HQFiniteMediaServer` additionally isolates broadcast send work per recipient, so one failed nearby player does not abort later recipients.

### `MediaAssetReleaseQueueTest`

Proves:

- a failed logical asset release transfers ownership to the retry queue;
- retry ownership remains queued across repeated failure;
- successful later release clears the queued responsibility;
- a genuinely missing asset is treated as resolved rather than queued forever.

### Existing asset/range tests used by the M1E lifetime recheck

`MediaAssetStoreTest` and `FiniteRangeReadServiceTest` remain relevant to the shared ownership foundation. The source recheck also verified server shutdown ordering: range IO closes/drains before store cleanup, while speaker/peripheral cleanup happens before `ServerMediaAssets.closeServer()`.

## What M1E source review additionally proves

Some M1E behavior depends on Minecraft/CC:T objects and is not instantiated by a pure unit test. The final source review verified:

- all throwable prepared-start construction happens before canonical session installation;
- work after session installation is best-effort client/Lua projection;
- each nearby client send is isolated independently;
- each attached ComputerCraft state-event delivery is isolated;
- playback release responsibility always transfers to either the store or retry queue;
- terminal END/ERROR playback releases its playback asset reference;
- new prepared play can replace a terminal finite session safely;
- client READY requests fresh state only;
- client ERROR is diagnostic only;
- cleanup removes finite server registration and releases finite/staging ownership before store shutdown.

These are source-reviewed implementation facts. They are not being mislabeled as a real Minecraft failure-injection test.

## Focused Minecraft M1E acceptance boundary

`scripts/m1e_server_authority_test.lua` checks the normal user-facing semantic path, including immediate progression, pause/resume, exact-end behavior, generation replay, looping exact-end wrap, stop, and prepared release.

The owner explicitly chose not to run the final strengthened script.

Record this exactly as:

```text
M1E final manual Minecraft acceptance: skipped / no recorded PASS
```

Do not infer PASS from CI.

## Current M1F deterministic gap

M1F range transport remains provisional. Existing `FiniteRangeWindowTest` and `FiniteRangeReadServiceTest` cover important bounded-transport pieces, but M1F still lacks the complete promised deterministic matrix.

Still-open M1F proof includes:

- complete server source/generation/asset/relevance request-path component coverage;
- stale completion after replacement/leave/disconnect;
- packet codec/bounds integration;
- client BEGIN/STATE anchor gating;
- a true sliding consume/discard window progression test across data larger than one window;
- focused Minecraft range-transport acceptance.

The M1E state-machine/projection/release gaps previously listed here are now closed.

## M1G proof boundary

M1G has not started. It must later prove:

- progressive MP3 decode from bounded transport;
- temporary starvation is not decoder EOF;
- Layer III pre-roll;
- common WAV integer/float conversion;
- stereo-to-mono downmix and >2-channel rejection;
- bounded mono PCM queue;
- decoder cancellation/replacement;
- no sound-thread network/disk/decode blocking;
- actual positional audible Minecraft playback;
- pause/resume/seek/loop projection into the renderer.

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
