# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, the tests which actually exist, and package structure. It does **not** by itself prove audibility, renderer lifecycle, live network timing, reload behavior, or positional audio.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Current final checkpoints

### M1E

Code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

CI:

`34757923455`

The final focused Minecraft M1E script was explicitly skipped by the owner; no final M1E runtime PASS is recorded.

### M1F

Source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

CI:

`34763362365`

Both target NeoForge jobs passed build, all tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 candidate:

- artifact id `10319592968`;
- artifact ZIP SHA-256 `b971f027cad9c22265e3080f17725859fa447ce71cd1d2d0a14f2d1710b1c131`;
- JAR `hqspeaker-1.1.4-1.21.1-neoforge.jar`;
- JAR SHA-256 `2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`.

Focused Minecraft M1F transport acceptance is not recorded.

## M1E deterministic evidence

`FinitePlaybackStateMachineTest`, `FinitePlaybackClockTest`, `BestEffortProjectionTest`, and `MediaAssetReleaseQueueTest` cover canonical finite state/clock/error/projection/release behavior. `M1E-FINAL-HARDENING-2026-09-13.md` records the exact matrix.

## M1F deterministic/component evidence

### `FiniteRangeValidationTest`

Proves the pure rules used by the M1F wire/server path:

- non-null source/asset identity;
- positive generation;
- non-negative offsets;
- positive bounded range length;
- exact shared 128 KiB maximum;
- source/asset/generation matching;
- asset-end bounds;
- stale generation/asset completion rejection;
- same-dimension/not-removed/in-range relevance rule.

`HQFiniteMediaRangeRequestPacket` and `HQFiniteMediaRangeDataPacket` delegate their range sanity to this shared validator. The response codec additionally checks length before allocating the byte array.

### `FiniteRangeReadServiceTest`

Proves:

- exact arbitrary offset reads;
- completion runs on the dedicated `hqspeaker-range-io-*` worker rather than the submitting thread;
- per-player outstanding request/byte limits;
- accepted work takes a separate MediaAsset retain;
- original owner release does not invalidate queued work;
- invalid/over-budget work is rejected before IO;
- queued shutdown cancellation releases asset lease and player accounting;
- failed in-flight final release transfers to the shared retry owner;
- a later retry can finish that release;
- an initial shutdown timeout does not close the asset store and the service close can be retried.

### `FiniteRangeWindowTest`

Proves:

- a fresh window is unanchored and cannot request bytes before authoritative anchor/reset;
- arbitrary non-zero anchor demand;
- exact response acceptance;
- bounded allocation independent of full asset size;
- re-anchor discards stale data/demand;
- stale response rejection;
- temporary missing bytes are NEED_DATA, not TRUE_ASSET_EOF;
- request timeout/retry;
- malformed responses do not wedge demand;
- forward `advanceTo(...)` preserves unread overlap;
- consumed prefix is discarded;
- only whole still-useful in-flight requests survive a slide;
- new tail demand opens as the window advances;
- repeated advance/refill past more than one window remains bounded.

### `FiniteRangeTransportTest`

This is the original M1F fake-consumer proof as one component flow:

```text
2 MiB MediaAsset
-> non-zero bounded demand
-> actual async server read service
-> exact bytes into FiniteRangeWindow
-> repeated consume/advance/refill beyond one full window
-> distant jump/re-anchor
-> old region becomes stale
-> exact bytes still match
-> memory remains bounded
```

It intentionally does not instantiate Minecraft rendering/network objects.

## Source-reviewed M1F integration facts

Some M1F routing/lifecycle behavior depends on Minecraft objects and is therefore verified by source composition plus the pure rules above rather than a fake `ServerPlayer` unit test:

- wrong source is rejected by source routing and again by the shared request validator;
- request admission checks active non-terminal playback and current relevance;
- range completion rechecks active generation/asset, resolves the current player by UUID, and rechecks relevance before send;
- missing/disconnected players therefore discard completion;
- stale replacement completions discard before send;
- client BEGIN only creates an unanchored window and reports READY;
- authoritative STATE supplies the first anchor and unlocks demand;
- seek/state re-anchor invalidates obsolete window demand;
- modern client has no complete-song `.part/.media` write path;
- current source tree has no modern finite CHUNK/END packet classes;
- composite has no `audioPlayStaged()` command;
- server stop clears speaker/peripheral ownership before `ServerMediaAssets.closeServer()`;
- `ServerMediaAssets.closeServer()` drains range IO before store close.

These source facts are not being mislabeled as a real Minecraft network run.

## Focused Minecraft evidence boundaries

### M1E

`scripts/m1e_server_authority_test.lua` exists, but the owner explicitly chose not to run the final strengthened acceptance. Record:

```text
M1E final manual Minecraft acceptance: skipped / no recorded PASS
```

### M1F

No focused real client/server M1F transport PASS is recorded. A future runtime check may inspect range behavior, absence of modern client song files, seek/replacement stale-work behavior, and clean server shutdown, but source/test/CI completion does not depend on pretending that run already happened.

M1F audibility is not required.

## M1G proof boundary

M1G has not started. It must later prove:

- progressive MP3 decode from bounded M1F data;
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
