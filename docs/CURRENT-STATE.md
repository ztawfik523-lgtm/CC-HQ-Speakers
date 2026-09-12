# Current state

## Current checkpoint

The project is at the **final M1E acceptance checkpoint**. M1F has not started.

Read `M1E-FINALIZATION-2026-09-13.md` first. It records the final recheck, the exact code/test candidate, the strengthened runtime acceptance harness, and the gate that must pass before M1F begins.

The older `PRE-M1F-PREPARATION.md` remains useful for the clean-break/decoder decisions but is no longer the current status document.

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`
- 2026-09-12 runtime-diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- M1E finalization code/test candidate before documentation-only follow-up: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`
- active branch: `codex/m1e-server-authoritative-finite`

Known green CI anchors:

- M1D run `34635484316` — NeoForge 21.1.247 and 21.1.248 passed;
- original M1E run `34658958488` — both targets passed;
- diagnostic head run `34686003774` — both targets passed;
- preparation head run `34721674149` — both targets passed.

The finalization candidate adds only acceptance coverage: one deterministic clock test and a stronger runtime script. It does not change M1E server semantics, M1F transport, or the decoder.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

CC:HQ Speakers upgrades the normal CC:T speaker into a programmable audio peripheral. Lua owns application policy such as playlists, alarms, notifications, speech, ambience, and sequencing. Java exposes truthful technical source capabilities.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a server-owned timeline;
- live/open-ended network streams later.

Do not add Java concepts such as music/effect/notification roles or a playlist manager.

## Final finite direction

Core target formats:

- MP3 / MPEG Layer III;
- common WAV.

Wanted but separately gated:

- native FLAC, only after its analyzer/decoder/seek/package path is proven.

Not final product requirements:

- OGG Vorbis;
- Ogg-FLAC;
- AIFF/AIF;
- AU/SND;
- exotic/compressed/telephony WAV variants;
- >2-channel finite input.

One physical speaker renders one mono positional source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

The final finite path remains:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> authoritative server timeline/state
    -> bounded client-requested encoded ranges
    -> bounded temporary client encoded RAM
    -> progressive decoder/converter worker
    -> bounded mono PCM
    -> positional Minecraft/OpenAL renderer
```

There is no final persistent client song cache, `.part` library, completed media library, sparse cache, LRU database, or cross-restart resume.

## M1A/M1B/M1C/M1D retained foundation

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`. HQ RAW retains separate bounded `hqspeaker_audio_empty` pacing and one continuous-source ownership boundary.

`MediaAssetStore` provides server-side UUID media identity, exact disk-backed import, quotas, retain/release lifetime, final-reference deletion, and seekable reads. This is server storage, not a client cache.

Prepared local files follow:

```text
ComputerCraft file
    -> temporary HQ staging mount
    -> immutable server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Frozen M1D analyzes committed bytes and provides server metadata including scanned MP3 duration and bounded seek hints. Its historical OGG/AIFF/AU surface does not define the final format promise.

## M1E — server-authoritative finite playback

M1E's server-authority semantics are implemented and were rechecked before finalization.

Current guarantees:

- semantic states are `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- there is no canonical server `LOADING` state for client buffering;
- successful finite play starts the canonical server clock immediately;
- the clock does not wait for READY or any renderer handshake;
- `successfulRenderers`, canonical `observed`, and the no-renderer timeout are gone;
- server duration/clock determines natural non-looping EOF;
- looping uses wrapped server position;
- non-looping `seek(duration)` ends immediately at duration;
- looping `seek(duration)` wraps to zero;
- client READY only requests a fresh state snapshot;
- client ERROR is diagnostic only and cannot globally fail playback;
- canonical EOF closes the temporary transfer before releasing the playback asset reference;
- replay after terminal END creates a new generation while the prepared owner remains valid;
- STOP returns the finite server state to idle.

Protocol v4 carries authoritative mutable truth in `HQFiniteMediaStatePacket`. BEGIN remains temporary setup for the old bridge.

## M1E runtime evidence and remaining gate

The 2026-09-12 diagnostic logs show the expected server sequence through PLAYING, PAUSED, resume, exact-end ENDED, replay, loop enable, and exact-end loop wrap near zero. The old MP3 decoder fails locally, but the server timeline continues independently.

That is strong supporting runtime evidence, but the captured logs do not preserve the ComputerCraft terminal PASS result.

The final focused script is now:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav> [result-file]
```

It verifies the original authority contract plus replay generation/asset identity and STOP -> idle. It writes PASS/FAIL to `m1e_server_authority_result.txt` by default so the final verdict is auditable.

**M1F must not start until the final M1E candidate actually produces PASS in that script/result file.**

## Temporary decoder finding

The current prepared finite client still downloads the complete encoded asset to `.part/.media` and uses `FileFiniteAudioStream`.

The MP3 bridge is known unreliable:

- first PCM read can end immediately after its seek path;
- MP3SPI reported `322.584 s` for a roughly 2:45 fixture while the server analyzer reported `161.304 s`;
- the MP3 seek helper mixes decoded-byte assumptions with mp3spi compressed frame/byte skip behavior;
- it can report the requested target after incomplete positioning;
- renderer restart currently performs a redundant second seek.

These defects remain documented and deferred. Do not repair the bridge merely to make M1E/M1F audible.

Agreed rule:

**keep decoder needs in mind architecturally, but do not expect the temporary decoder to work correctly before M1G.**

## M1F boundary — agreed clean break

M1F has not started.

After M1E runtime PASS, M1F should make a clean break for the modern prepared finite path rather than maintain the old whole-file prepared bridge in parallel.

M1F owns:

- client-requested bounded encoded byte ranges;
- server-selected stream/seek anchors;
- generation/asset/range/relevance validation;
- bounded outstanding work/rate controls;
- bounded off-thread server asset reads with safe retained lifetime;
- stale async completion discard;
- bounded temporary client encoded RAM/window;
- arbitrary encoded offsets;
- no persistent client song files/cache.

M1F does not require audible finite playback, PCM decoding, MP3 pre-roll, WAV conversion, or a final renderer. Those are M1G.

Its client encoded-data layer must nevertheless distinguish temporary missing data from true asset EOF so M1G can consume it progressively without redesigning the transport boundary.

## M1G boundary

M1G owns:

- progressive MP3 and common-WAV decode/conversion;
- temporary-starvation-vs-real-EOF handling;
- MP3 earlier-anchor pre-roll for Layer III reservoir state;
- bounded mono PCM queues;
- decoder cancellation;
- actual Minecraft/OpenAL audible finite rendering;
- final active-branch MP3/common-WAV format narrowing.

## Parked cleanup

Do not mix unrelated legacy cleanup into M1E finalization or M1F unless it directly blocks the milestone.

`FUTURE-CLEANUP.md` tracks the old file decoder/cache, transitional packets, legacy finite engine, byte APIs, multispeaker bypasses, old format surfaces, dependency review, diagnostic logging, custom HQ block, live/HLS/TS issues, OpenAL cleanup, tests/docs, packaging, and license metadata.

## Read order

1. `M1E-FINALIZATION-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `VERIFIED-FACTS.md`
4. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`
5. `ARCHITECTURE.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-FINITE-STREAMING-DESIGN.md`
8. `ROADMAP.md`
9. `KNOWN-ISSUES.md`
10. `TESTING.md`
11. `FUTURE-CLEANUP.md`
12. exact current branch source and current CI
