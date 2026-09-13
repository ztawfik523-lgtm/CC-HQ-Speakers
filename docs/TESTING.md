# Testing

## Rule

Prefer deterministic tests first, then focused Minecraft acceptance, then the final batched runtime pass.

A green Gradle build proves compilation/tests/package structure. It does **not** prove audible behavior, client renderer lifecycle, range delivery, SoundEngine integration, resource reload, or physical positional audio.

Tests are not a late roadmap milestone. Every implementation milestone must add the deterministic tests needed to prove its own contract.

A project decision may intentionally skip a manual runtime check. When that happens, record it as **skipped/unverified**, never as PASS.

Current checkpoint: documentation/preparation only. M1F implementation has not started.

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

Use actual CC:T peripherals/client SoundEngine for behavior pure tests cannot prove: standard CC:T signatures/defaults and `speaker_audio_empty`, finite semantic controls, progressive start, late join/leave-return, underrun/rejoin, SoundEngine category/gain, F3+T, dimension/world changes, VS2, multi-client/multispeaker positional rendering, final FLAC if implemented, and shutdown/reconnect.

A milestone only needs the runtime behavior in its own contract. M1E did not require the temporary MP3 decoder to be the final correct renderer.

### 4. Final batched M1 acceptance

M1Q combines the already-tested pieces into one end-to-end pass with large files, multiple clients/speakers, memory/network observation, and tick/sound-thread stall checks. It is a regression/integration pass, not the first place individual features are tested.

## M1E evidence

Original semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Original semantic source/test/package CI:

`34658958488` — success on NeoForge 21.1.247 and 21.1.248.

2026-09-12 diagnostic Java head:

`c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`

Diagnostic-head CI:

`34686003774` — success on both target NeoForge versions.

M1E finalization code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

Finalization CI:

`34725651930` — success on both target NeoForge versions with tests/package verification.

Pure Java M1E coverage includes:

- immediate clock progression after `start()` without any renderer handshake;
- natural EOF for non-looping tracks;
- no natural EOF while looping;
- exact-end non-looping seek;
- exact-end looping wrap;
- pause/resume;
- loop-disable rebase.

### Prepared focused M1E runtime contract

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav> [result-file]
```

The script checks immediate canonical PLAYING/progression, pause/resume, non-loop exact-end END, replay generation/asset identity, loop exact-end wrap, STOP -> idle, and prepared release.

The final manual run was **not performed**. The project owner chose to skip it and move forward later.

Therefore:

- do not report M1E Minecraft-runtime PASS;
- keep the 2026-09-12 diagnostic logs as supporting evidence only;
- do not treat the missing final manual run as a mandatory sequencing gate unless that project decision changes.

### 2026-09-12 supporting diagnostic evidence

The captured runtime logs show the old client transfer reaching READY and then failing on the first PCM read while the server continues through authority/control transitions consistent with the focused script, including exact-end END and loop-wrap behavior.

The old JavaSound/mp3spi decoder is not an M1E acceptance oracle. Its runtime MP3 duration was clearly wrong for the tested fixture and its seek path is known to misuse mp3spi skip semantics. Decoder repair remains M1G work unless a future issue directly blocks the modern architecture.

## M1F proof requirements

M1F has **not started**.

When explicitly started, it uses a clean break for the modern prepared finite path. Its deterministic tests must prove at least:

- request offset/length bounds;
- active source/generation/asset validation;
- same-dimension/current-relevance validation;
- maximum outstanding request/byte accounting;
- safe in-flight asset lifetime while async IO runs;
- stale completion after replacement is discarded;
- stale completion after player leaves/disconnects is discarded;
- cancellation does not leak retained asset references or accounting;
- background reads are stopped/drained/cancelled before server media-store shutdown;
- large asset reads do not run on the server tick;
- response packet size stays within the chosen bounded cap;
- exact returned bytes match the requested server asset region;
- arbitrary encoded offsets can be requested without downloading from byte zero;
- client encoded RAM remains bounded independently of asset size;
- the modern path does not require `.part`, `.media`, completed client song files, LRU/sparse cache, or persistent resume;
- `audioPlayStaged()` is removed from the modern API/transport path rather than preserved as a second prototype transport.

M1F need not prove the final MP3/WAV decoder or audible finite rendering. Progressive decoder correctness is M1G.

A deterministic fake/test consumer is sufficient for the range/window layer. Its availability contract must distinguish:

- data available now;
- data needed but not arrived yet;
- true asset EOF;
- cancelled/stale state.

Recommended synthetic transport proof:

```text
request a bounded range at a non-zero offset
-> verify exact bytes
-> consume/discard it
-> jump to a distant offset
-> verify old bytes are no longer retained
-> prove memory remains bounded
```

## M1G proof boundary

M1G, not M1F, must prove:

- progressive MP3/common-WAV decode;
- temporary starvation is not EOF;
- MP3 seek/rejoin pre-roll produces stable output;
- common WAV formats convert/downmix correctly;
- bounded PCM queues;
- decoder cancellation;
- pause/resume/seek/loop interaction with the local renderer projection;
- Minecraft SoundEngine integration and actual audible positional output;
- sound thread never blocks on network/disk/decoder refill.

## API/documentation proof

When a milestone adds/removes/changes Lua-facing functions or events:

- update `LUA-API.md` in the same milestone;
- keep the bundled `hqspeaker.lua` comments/examples accurate;
- mark prototype/legacy surfaces explicitly instead of presenting them as recommended API;
- update README/current-state/handoff references when the recommended workflow changes.

## Evidence recording

For a real-client acceptance run record exact commit, JAR SHA-256, NeoForge/CC:T versions, pass/fail by section, relevant client/server logs, whether full ATM10 or a reduced exact-stack instance was used, fixture format/size/sample details, and whether network compression was enabled when measuring throughput.

A skipped test must be recorded as skipped, not inferred from source/CI.

A failed broad test should produce a focused source diagnosis before another broad launch.