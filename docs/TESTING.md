# Testing

## Rule

Prefer deterministic tests first, then focused Minecraft acceptance, then the final batched runtime pass.

A green Gradle build proves compilation/tests/package structure. It does **not** prove audible behavior, client renderer lifecycle, range delivery, SoundEngine integration, resource reload, or physical positional audio.

Tests are not a late roadmap milestone. **Every implementation milestone must add the deterministic tests needed to prove its own contract.**

For the current preparation checkpoint, read `PRE-M1F-PREPARATION.md` and `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` before resuming implementation.

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

Use small Java components/fakes for server-authoritative state transitions, state snapshot generation/application, M1F async range lifecycle/stale completion, encoded-window demand/availability semantics, MP3 temporary-starvation-vs-real-EOF behavior, decoder cancellation, dynamic relevance/leave-return state, underrun/rejoin rules, and multispeaker sync-clock membership.

Do not create abstractions only for testing, but extract state when doing so makes races/lifecycle behavior deterministic and reviewable.

### 3. Focused Minecraft acceptance

Use actual CC:T peripherals/client SoundEngine for behavior pure tests cannot prove: standard CC:T signatures/defaults and `speaker_audio_empty`, actual sound, finite semantic controls, progressive start, late join/leave-return, underrun/rejoin, SoundEngine category/gain, F3+T, dimension/world changes, VS2, multi-client/multispeaker positional rendering, final FLAC if implemented, and shutdown/reconnect.

### 4. Final batched M1 acceptance

M1Q combines the already-tested pieces into one end-to-end pass with large files, multiple clients/speakers, memory/network observation, and tick/sound-thread stall checks. It is a regression/integration pass, not the first place individual features are tested.

## M1E proof

Exact M1E semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Exact source/test/package CI run for that checkpoint:

`34658958488` — success on NeoForge 21.1.247 and 21.1.248.

Later runtime-diagnostic Java commits moved the branch head without intentionally changing the server-authority architecture. The pre-preparation diagnostic head is:

`c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`

Latest CI for that head:

`34686003774` — success on NeoForge 21.1.247 and 21.1.248.

Pure Java M1E coverage includes deterministic natural EOF through `FinitePlaybackClock.reachedEnd()`, loop/non-loop end behavior, exact-end seek, pause/resume, and loop rebase.

Focused M1E runtime contract:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It checks immediate server `PLAYING`, position advancement without renderer authority, pause/resume, non-looping exact-duration END, and looping exact-duration wrap.

### 2026-09-12 diagnostic run

The latest captured runtime logs show the old client transfer reaching READY and then failing on the first PCM read, while authoritative server state/control traffic continues independently. This is useful evidence for authority separation, but the captured logs do not contain:

```text
M1E server-authority contract passed
```

Therefore **do not report M1E Minecraft runtime PASS yet**. The diagnostic run is documented in `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`.

The old JavaSound/mp3spi decoder is not an M1E acceptance oracle. Its runtime MP3 duration was clearly wrong for the tested fixture and its seek path is known to misuse mp3spi skip semantics. Decoder repair is deferred to M1G unless it becomes a direct blocker for server-authority testing.

The older `scripts/m1d_media_analysis_test.lua` includes frozen-M1D renderer-`observed` assumptions and should not be treated as the active M1E semantic contract.

## M1F proof requirements

M1F implementation has **not started** at the current preparation checkpoint.

When it starts, M1F uses a clean break for the modern prepared finite path. Its deterministic tests must prove at least:

- request offset/length bounds;
- active source/generation/asset validation;
- same-dimension/current-relevance validation;
- maximum outstanding request/byte accounting;
- safe in-flight asset lifetime while async IO runs;
- stale completion after replacement is discarded;
- stale completion after player leaves/disconnects is discarded;
- cancellation does not leak retained asset references or accounting;
- large asset reads do not run on the server tick;
- response packet size stays within the chosen bounded cap;
- exact returned bytes match the requested server asset region;
- arbitrary encoded offsets can be requested without downloading from byte zero;
- client encoded RAM remains bounded independently of asset size;
- the modern M1F path does not require `.part`, `.media`, completed client song files, LRU/sparse cache, or persistent resume.

M1F need not prove the final MP3/WAV decoder or audible finite rendering. Progressive decoder correctness is M1G.

However M1F must expose a bounded in-memory range/window contract which a future M1G decoder can consume without reconstructing a persistent local file.

A deterministic fake/test consumer is sufficient. It should be able to request a window, verify offset/content, consume/discard it, jump to a distant offset, and prove old data is no longer retained beyond the configured bounds.

### M1F availability semantics

Without implementing a codec decoder, M1F tests should preserve the semantic distinction between:

- encoded data available now;
- requested/needed data not arrived yet;
- true end of the server asset;
- cancelled/stale source/generation.

Exact enum/class names are not frozen by this document.

This distinction prevents M1G from being forced to treat temporary network starvation as permanent EOF.

## M1G proof boundary

M1G, not M1F, must prove:

- progressive MP3/common-WAV decode;
- temporary starvation is not EOF;
- MP3 seek/rejoin pre-roll produces stable output;
- common WAV formats convert/downmix correctly;
- bounded PCM queues;
- decoder cancellation;
- pause/resume/seek/loop interaction with the renderer projection;
- Minecraft SoundEngine integration and actual audible positional output;
- sound thread never blocks on network/disk/decoder refill.

## Finite-streaming-specific proof

Across M1E-M1G the replacement architecture ultimately needs evidence that:

- server playback advances with zero renderer authority;
- state never remains PLAYING past known non-looping EOF;
- early canonical EOF closes any obsolete transfer/read ownership before releasing its playback asset reference;
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
