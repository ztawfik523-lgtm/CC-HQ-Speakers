# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation/tests/package structure. It does **not** prove audibility, renderer lifecycle, real network timing, SoundEngine integration, reload behavior, or physical positional audio.

Target matrix for release-facing source changes:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Current checkpoint

M1F final code/test head before documentation follow-up:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI run:

`34731827907`

Both target NeoForge jobs passed:

- build/tests;
- packaged-mod verification;
- candidate artifact upload.

M1F Minecraft runtime acceptance has not been recorded.

M1E's final manual runtime PASS was intentionally skipped by project-owner decision and remains unrecorded.

## Test layers

### Pure/unit

Use deterministic tests for:

- server finite clock/EOF/loop/seek math;
- media/container parsing;
- range bounds/accounting;
- encoded-window availability/re-anchor/retry behavior;
- stale/cancelled data handling;
- in-flight asset retention;
- WAV conversion/downmix when M1G lands;
- MP3 anchor/pre-roll helpers when M1G lands;
- bounded buffers/backpressure.

### Component/state-machine

Use small Java components/fakes for:

- authoritative server state transitions;
- asynchronous range submission/completion/cancellation;
- stale-generation/relevance behavior;
- temporary starvation versus real EOF;
- decoder cancellation;
- listener leave/rejoin state;
- multispeaker sync-clock logic.

### Focused Minecraft acceptance

Use the actual target stack when the behavior depends on Minecraft/client/network/audio integration:

- standard CC:T signatures/events;
- real finite controls;
- range transport under a real client/server connection;
- progressive/audible start after M1G;
- late join/leave-return after M1H;
- SoundEngine/OpenAL category/gain/attenuation;
- F3+T/resource reload;
- world/dimension/disconnect;
- VS2 movement;
- multiple clients/speakers.

## M1E evidence boundary

M1E source/test/package CI is implemented and its diagnostic runtime logs support server/client authority separation.

The final focused script exists, but the owner explicitly chose not to run the final manual M1E test. Therefore never report M1E Minecraft-runtime PASS.

## M1F deterministic proof

New M1F tests include:

### `FiniteRangeWindowTest`

Proves:

- a bounded RAM window can start at a non-zero encoded offset;
- returned bytes match the requested region;
- allocated window size stays bounded independently of full asset size;
- re-anchor drops obsolete old data/demand;
- stale responses are rejected;
- `NEED_DATA` is distinct from `TRUE_ASSET_EOF`;
- timed-out demand becomes requestable again;
- malformed/wrong-length response cannot permanently wedge demand.

### `FiniteRangeReadServiceTest`

Proves:

- exact arbitrary server byte ranges are read correctly;
- work is submitted through the dedicated range service rather than server tick transfer code;
- per-player outstanding request/byte accounting returns to zero;
- an in-flight read takes its own MediaAsset reference;
- releasing the original owner while read is queued does not delete the asset underneath that read;
- the in-flight reference is released after completion;
- invalid bounds are rejected;
- configured outstanding limits reject excess work.

Source review/CI additionally verifies:

- protocol v5 registers bounded range request/data packets;
- modern finite CHUNK/END packets are removed;
- modern client no longer contains `.part/.media` file transfer code;
- `audioPlayStaged()` is removed;
- server completion rechecks generation/asset/player/relevance before send;
- `ServerMediaAssets.closeServer()` stops/drains range IO before closing the asset store;
- first client range demand waits for authoritative STATE/anchor.

## M1F runtime scope if tested later

A focused runtime M1F check would prove transport integration, not audibility. Useful observations would be:

- `hq.playFile`/prepared playback starts canonical server state normally;
- client requests bounded ranges rather than receiving a complete pushed file;
- no new modern `.part/.media` client song is created;
- a seek/current-state anchor can cause non-zero encoded demand;
- stopping/replacing playback prevents stale ranges from reviving old transport state;
- server tick remains responsive while ranges are read.

Audible MP3/WAV is not an M1F acceptance requirement.

## M1G proof boundary

M1G must add deterministic/runtime proof for:

- progressive MP3 decode from the M1F window;
- missing network bytes are not decoder EOF;
- Layer III seek/rejoin pre-roll;
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
- fixture format/size/sample facts;
- exact pass/fail sections;
- relevant client/server logs;
- full ATM10 versus reduced exact-stack instance;
- network compression state when throughput is measured.

Never infer runtime PASS from CI alone.
