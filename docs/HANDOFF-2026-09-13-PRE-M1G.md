# CC:HQ Speakers — pre-M1G handoff

> **Historical preparation checkpoint. M1G and the separate post-M1G Option A hardening pass are now complete; M1H is current. The decision gates below are historical.**
>
> Preserve this file as evidence of the pre-M1G reasoning, but do not follow its stop condition or ask the owner to re-choose A/B/C. A1/B1/C1/D1/E1 are locked, the progressive decoder/renderer is integrated in current source, and later scope is recorded in `M1G-SCOPE-DECISIONS-2026-09-14.md` / `CURRENT-STATE.md`.
>
> Current later M1G decisions also include explicit decoder/re-anchor revision, fixed 32-block core range with volume changing gain rather than radius, global-volume-zero local hibernation, and ordinary non-gapless replay. Current docs/exact source override this file.

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Preparation branch: `codex/m1g-preparation`

Preparation base: `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365`

## Historical stop condition

At this checkpoint, M1G had not started and Java changes waited on owner decision gates in `PRE-M1G-PREPARATION.md`. That condition is now resolved and must not be applied to current work.

This handoff was preparation/documentation only.

## Fresh verification at the checkpoint

The old “docs-head .247 still running” note was closed.

Documentation head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` was verified by run `34763711105`:

- NeoForge 21.1.247: PASS;
- NeoForge 21.1.248: PASS;
- build/tests/package verification/artifact upload passed.

At the owner's request, that run was started again on 2026-09-13. The fresh rerun produced successful 21.1.247 and 21.1.248 jobs again.

Do not confuse this with a focused Minecraft runtime acceptance. M1F focused real-client transport PASS remains unrecorded.

## Project status at this checkpoint

```text
M1E: source/test/CI complete
     final focused Minecraft acceptance skipped/unrecorded

M1F: source/test/CI/package complete
     deterministic/component acceptance complete
     focused Minecraft transport acceptance unrecorded

M1G: prepared, NOT STARTED at this checkpoint

M1H: later dynamic listener/rejoin lifecycle
```

Current status is newer; see `CURRENT-STATE.md`.

## Product framing

This is a programmable upgrade to the normal CC:T `computercraft:speaker`.

Lua decides application meaning: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc.

Java models technical source capabilities only.

One physical speaker = one mono positional source.

Do not create Java music/effect/notification roles and do not infer role from MP3/WAV.

Standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain compatibility requirements.

## What M1F gave M1G

```text
server MediaAsset
-> server-authoritative finite clock/state
-> authoritative encoded anchor
-> bounded range requests
-> bounded background server reads
-> bounded sliding `FiniteRangeWindow`
```

At this checkpoint `HQFiniteMediaClient` was transport-only. That is no longer true: modern progressive decode/render is now integrated. Do not use the historical statement as current architecture.

M1F must remain bounded and must not be replaced by a whole-song bridge.

## M1G target from the preparation checkpoint

```text
M1F sliding encoded window
-> cancelable progressive decoder/converter worker
-> bounded mono PCM queue
-> one positional Minecraft speaker renderer
```

Core prepared/local formats:

- MP3 / MPEG Layer III;
- common WAV subset.

FLAC remains M1I gated work.

This target has since been substantially integrated.

## Rechecked source facts relevant to M1G at the checkpoint

### MP3

The project packages JLayer `1.0.1.4`.

Inherited live MP3 code demonstrated frame-by-frame JLayer use (`Bitstream`, `Decoder`, `SampleBuffer`) and mono downmix. This proved the dependency path, not the final finite architecture.

Temporary M1F `NEED_DATA` must never become `InputStream` EOF. The decoder input needs a worker-only starvation-aware bridge over `FiniteRangeWindow`.

That bridge now exists in modern source.

### Old finite code

Do not use these as the new engine:

- `FileFiniteAudioStream`: complete local file based;
- `HQAudioStream` finite mode: whole finite payload -> whole retained decoded `FiniteAudioTrack`;
- `FiniteAudioTrack`: PCM RAM proportional to track duration.

They may remain for inherited/legacy paths until later migration milestones.

### WAV

At this checkpoint the server analyzer was broader than final product support.

Final M1G common WAV target was selected as:

- mono/stereo;
- unsigned 8-bit PCM;
- signed 16/24/32-bit PCM;
- 32-bit IEEE float;
- stereo -> mono;
- reject >2 channels/companded/compressed/unusual WAV.

Server-normalized WAV layout was later selected as B1 and implemented.

### Semantic seek

A new seek can require decoder restart even if the coarse server-selected byte anchor is unchanged. Do not equate “same encoded anchor” with “same codec state/audible target.”

Later scope refined this into the selected explicit server-authoritative decoder/re-anchor revision.

## Historical decision gates — resolved

The original preparation asked the owner to choose:

### A. Renderer path

- **A1:** Minecraft `AudioStream` + normal `SoundManager` positional sound;
- **A2:** direct Channel/OpenAL queued-buffer ownership.

**Resolved: A1.**

### B. WAV layout ownership

- **B1:** server analyzer normalizes WAV layout and sends it to clients;
- **B2:** client progressively parses WAV layout from M1F bytes.

**Resolved: B1.**

### C. Finite sample-rate policy

- **C1:** preserve source sample rate;
- **C2:** resample all finite PCM to 48 kHz.

**Resolved: C1.**

Later D1 narrow WAVEX and E1 coarse safe MP3 pre-roll were also locked. Do not pick these again.

## M1G non-negotiable correctness rules

These remain valid:

- server remains canonical PLAYING/PAUSED/ENDED/ERROR authority;
- decoder/renderer failures are local diagnostics only;
- no whole encoded file on the client;
- no whole decoded PCM track;
- encoded and decoded memory remain bounded independently of duration;
- decoder waits only on worker threads;
- renderer reads never block on network/disk/codec work;
- `NEED_DATA` is not EOF;
- seek/replacement/stop cancels stale encoded waits, decoder output, PCM, and renderer state;
- MP3 seek/rejoin includes Layer III pre-roll;
- local renderer joins current server time after delay rather than freezing canonical playback;
- one physical speaker remains one mono positional source.

## Historical M1G test plan

The original component proof list included:

- starvation/wakeup/EOF/cancel semantics of encoded input;
- progressive MP3 across several M1F windows;
- MP3 seek/pre-roll and stale-output cancellation;
- exact common-WAV sample conversion vectors;
- stereo downmix and >2-channel rejection;
- PCM queue cap/backpressure;
- renderer-facing nonblocking reads and underrun != EOF;
- stop/seek/replacement cleanup;
- no complete-song encoded or decoded accumulation.

Current testing gaps and selected behavior have evolved. Use `TESTING.md`, not this historical list, for present acceptance work.

## M1H boundary

Do not pull the full listener lifecycle into M1G.

M1H owns:

- late range entry;
- proactive leave cleanup;
- leave/return rejoin;
- dimension/resource reload recovery;
- robust underrun rejoin;
- final VS2 moving-source lifecycle.

The selected fixed 32-block M1G radius means dynamic volume-aware listener membership is not pulled forward.

M1H moving-source design may later mirror the legacy client-side VS2 transform from BEGIN block coordinates or add explicit authoritative position updates. That tradeoff is not selected here.

## Historical documentation drift note

The original handoff warned about transitional M1F text in `ARCHITECTURE.md`. Current architecture docs have since been reconciled and should be used instead.

## Current read order

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `NEXT-CHAT-HANDOFF.md`
7. `ARCHITECTURE.md`
8. `ROADMAP.md`
9. exact current source and current CI

This pre-M1G handoff is historical evidence only.
