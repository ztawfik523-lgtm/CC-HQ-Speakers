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

Use for finite clock/EOF/loop/seek math, media/container parsing, WAV sample conversion/downmix, MP3 seek-anchor/pre-roll helpers, range bounds/request accounting, stale-generation/cancellation rules, bounded buffer/backpressure state, rate/outstanding request limiters, and asset lifetime/refcount behavior.

### 2. Component/state-machine

Use small Java components/fakes for server-authoritative state transitions, state snapshot generation/application, M1F async range lifecycle/stale completion, MP3 temporary-starvation-vs-real-EOF behavior, decoder cancellation, dynamic relevance/leave-return state, underrun/rejoin rules, and multispeaker sync-clock membership.

Do not create abstractions only for testing, but extract state when doing so makes races/lifecycle behavior deterministic and reviewable.

### 3. Focused Minecraft acceptance

Use actual CC:T peripherals/client SoundEngine for behavior pure tests cannot prove: standard CC:T signatures/defaults and `speaker_audio_empty`, actual sound, finite semantic controls, progressive start, late join/leave-return, underrun/rejoin, SoundEngine category/gain, F3+T, dimension/world changes, VS2, multi-client/multispeaker positional rendering, final FLAC if implemented, and shutdown/reconnect.

### 4. Final batched M1 acceptance

M1Q combines the already-tested pieces into one end-to-end pass with large files, multiple clients/speakers, memory/network observation, and tick/sound-thread stall checks. It is a regression/integration pass, not the first place individual features are tested.

## M1E proof

Pure Java M1E coverage includes deterministic natural EOF through `FinitePlaybackClock.reachedEnd()`, loop/non-loop end behavior, exact-end seek, pause/resume, and loop rebase.

Focused M1E runtime contract:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It checks immediate server `PLAYING`, position advancement without renderer authority, pause/resume, non-looping exact-duration END, and looping exact-duration wrap. Do not report M1E Minecraft runtime PASS until this script actually passes in-game on the target stack.

The older `scripts/m1d_media_analysis_test.lua` includes frozen-M1D renderer-`observed` assumptions and should not be treated as the active M1E semantic contract.

## Finite-streaming-specific proof

The replacement architecture particularly needs evidence that:

- server playback advances with zero renderer authority;
- state never remains PLAYING past known non-looping EOF;
- early canonical EOF closes temporary transfer before releasing its playback asset reference;
- stale range IO after replacement/seek is discarded safely;
- client RAM remains bounded independently of file size;
- temporary missing MP3 bytes are never exposed as permanent EOF;
- MP3 seek/rejoin pre-roll produces stable output;
- common WAV formats convert/downmix correctly;
- game/server/sound threads never block on large file IO or decoder refill;
- no persistent client song-cache files are created by the final path.

## Historical P0/M1D tests

Older P0/M1D scripts/tests remain useful evidence for the code they were written against. They do not define the final finite format/product scope after M1D. Do not weaken an existing CC:T compatibility guarantee merely to make an old test pass.

## Evidence recording

For a real-client acceptance run record exact commit, JAR SHA-256, NeoForge/CC:T versions, pass/fail by section, relevant client/server logs, whether full ATM10 or a reduced exact-stack instance was used, file format/size/sample details, and whether network compression was enabled when measuring throughput.

A failed broad test should produce a focused source diagnosis before another broad launch.
