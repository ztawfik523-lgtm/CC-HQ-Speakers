# Testing

## Rule

Prefer deterministic tests first, then focused Minecraft acceptance, then the final batched runtime pass.

A green Gradle build proves compilation/tests/package structure. It does **not** prove audible behavior, client renderer lifecycle, range delivery, SoundEngine integration, resource reload, or physical positional audio.

Tests are not a late roadmap milestone. **Every implementation milestone must add the deterministic tests needed to prove its own contract.**

## Target matrix

Every source change intended for release must build/test on:

- NeoForge 21.1.247
- NeoForge 21.1.248
- Java 21
- CC:Tweaked 1.120.0

CI should remain green on both NeoForge targets after every milestone head.

## Test layers

### 1. Pure/unit

Use for logic which does not need Minecraft:

- finite clock/EOF/loop/seek math;
- media/container parsing;
- WAV sample conversion/downmix;
- MP3 seek-anchor/pre-roll helpers;
- range bounds and request accounting;
- stale-generation/cancellation rules;
- bounded buffer/backpressure state;
- rate/outstanding request limiters;
- asset lifetime/refcount behavior.

### 2. Component/state-machine

Use small Java components/fakes for:

- M1E server-authoritative state transitions;
- client status reports proving non-authoritative;
- state snapshot generation/application;
- M1F async range request lifecycle and stale-completion discard;
- MP3 temporary-starvation-vs-real-EOF behavior;
- decoder worker cancellation;
- dynamic relevance/leave-return state;
- underrun/rejoin rules;
- multispeaker sync-clock membership.

Do not create abstractions only for testing, but extract state when doing so makes races/lifecycle behavior deterministic and reviewable.

### 3. Focused Minecraft acceptance

Use actual CC:T peripherals/client SoundEngine for behavior pure tests cannot prove:

- standard CC:T signatures/defaults and `speaker_audio_empty`;
- actual notes/sounds/audio;
- finite audible start before full file transfer;
- pause/resume/seek/loop/volume;
- late join and leave/re-enter;
- local underrun/rejoin behavior;
- SoundEngine category/gain;
- F3+T/resource reload;
- dimension/world changes;
- VS2 movement;
- multi-client/multispeaker positional rendering;
- final FLAC playback if M1I is implemented;
- shutdown/reconnect.

### 4. Final batched M1 acceptance

M1Q combines the already-tested pieces into one end-to-end pass with large files, multiple clients/speakers, memory/network observation, and tick/sound-thread stall checks.

It is a regression/integration pass, not the first place individual features are tested.

## Finite-streaming-specific proof

The new architecture particularly needs evidence that:

- server playback advances with zero renderer authority;
- state never remains PLAYING past known non-looping EOF;
- early canonical EOF closes temporary transfer before releasing its only playback asset reference;
- stale range IO after replacement/seek is discarded safely;
- client RAM remains bounded independently of file size;
- temporary missing MP3 bytes are never exposed as permanent EOF;
- MP3 seek/rejoin pre-roll produces stable output;
- common WAV formats convert/downmix correctly;
- game/server/sound threads never block on large file IO or decoder refill;
- no persistent client song-cache files are created by the final path.

## Historical P0/M1D tests

Older P0/M1D scripts and tests remain useful evidence for the code they were written against. They do not define the final finite format/product scope after M1D.

Do not weaken an existing CC:T compatibility guarantee merely to make an old test pass.

## Evidence recording

For a real-client acceptance run record:

- exact commit;
- JAR SHA-256;
- NeoForge/CC:T versions;
- pass/fail by section;
- relevant client/server logs;
- whether the full ATM10 pack or a reduced exact-stack instance was used;
- file format/size/sample details for finite fixtures;
- whether network compression was enabled when measuring streaming throughput.

A failed broad test should produce a focused source diagnosis before another broad launch.
