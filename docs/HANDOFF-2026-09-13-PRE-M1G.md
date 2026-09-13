# CC:HQ Speakers — pre-M1G handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Preparation branch: `codex/m1g-preparation`

Preparation base: `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365`

## Stop condition

**M1G has not started. Do not implement M1G from this handoff until the owner resolves the decision gates in `PRE-M1G-PREPARATION.md`.**

This handoff is preparation/documentation only.

## Fresh verification

The old “docs-head .247 still running” note is closed.

Current documentation head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` was verified by run `34763711105`:

- NeoForge 21.1.247: PASS;
- NeoForge 21.1.248: PASS;
- build/tests/package verification/artifact upload passed.

At the owner's request, that run was started again on 2026-09-13. The fresh rerun produced successful 21.1.247 and 21.1.248 jobs again.

Do not confuse this with a focused Minecraft runtime acceptance. M1F focused real-client transport PASS remains unrecorded.

## Current project status

```text
M1E: source/test/CI complete
     final focused Minecraft acceptance skipped/unrecorded

M1F: source/test/CI/package complete
     deterministic/component acceptance complete
     focused Minecraft transport acceptance unrecorded

M1G: prepared, NOT STARTED

M1H: later dynamic listener/rejoin lifecycle
```

## Product framing

This is a programmable upgrade to the normal CC:T `computercraft:speaker`.

Lua decides application meaning: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc.

Java models technical source capabilities only.

One physical speaker = one mono positional source.

Do not create Java music/effect/notification roles and do not infer role from MP3/WAV.

Standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain compatibility requirements.

## What M1F now gives M1G

```text
server MediaAsset
-> server-authoritative finite clock/state
-> authoritative encoded anchor
-> bounded range requests
-> bounded background server reads
-> bounded sliding `FiniteRangeWindow`
```

`HQFiniteMediaClient` is currently transport-only. It is the modern client insertion point for M1G.

M1F must remain bounded and must not be replaced by a whole-song bridge.

## M1G target

```text
M1F sliding encoded window
-> cancelable progressive decoder/converter worker
-> bounded mono PCM queue
-> one positional Minecraft speaker renderer
```

Core prepared/local formats for M1G:

- MP3 / MPEG Layer III;
- common WAV subset.

FLAC remains M1I gated work.

## Rechecked source facts relevant to M1G

### MP3

The exact project already packages JLayer `1.0.1.4`.

Inherited live MP3 code demonstrates frame-by-frame JLayer use (`Bitstream`, `Decoder`, `SampleBuffer`) and mono downmix. This proves the dependency path, not the final finite architecture.

Temporary M1F `NEED_DATA` must never become `InputStream` EOF. The decoder input needs a worker-only starvation-aware bridge over `FiniteRangeWindow`.

### Old finite code

Do not use these as the new engine:

- `FileFiniteAudioStream`: complete local file based;
- `HQAudioStream` finite mode: whole finite payload -> whole retained decoded `FiniteAudioTrack`;
- `FiniteAudioTrack`: PCM RAM proportional to track duration.

They may remain for inherited/legacy paths until later migration milestones.

### WAV

Current server analyzer is broader than final product support. It currently accepts historical shapes including more-than-stereo, companded WAV, 64-bit float, and broader PCM widths.

Final M1G common WAV target is only:

- mono/stereo;
- unsigned 8-bit PCM;
- signed 16/24/32-bit PCM;
- 32-bit IEEE float;
- stereo -> mono;
- reject >2 channels/companded/compressed/unusual WAV.

Current `MediaMetadata` does not yet carry a final normalized WAV layout descriptor.

### Semantic seek

A new seek can require decoder restart even if the coarse server-selected byte anchor is unchanged. Do not equate “same encoded anchor” with “same codec state/audible target.”

## Decision gates — ask owner before M1G Java changes

Read the full tradeoffs in `PRE-M1G-PREPARATION.md`.

### A. Renderer path

Choose between:

- **A1:** Minecraft `AudioStream` + normal `SoundManager` positional sound;
- **A2:** direct Channel/OpenAL queued-buffer ownership.

A1 is smaller/more native; A2 gives more explicit buffer control but pulls significant OpenAL lifecycle work forward.

### B. WAV layout ownership

Choose between:

- **B1:** server analyzer normalizes WAV layout and sends it to clients (wire/schema change likely);
- **B2:** client progressively parses WAV layout from M1F bytes.

B1 centralizes validation/direct seeking; B2 avoids larger wire metadata but duplicates parsing and delays layout knowledge.

### C. Finite sample-rate policy

Choose between:

- **C1:** preserve source sample rate;
- **C2:** resample all finite PCM to 48 kHz.

C1 avoids a resampler; C2 standardizes PCM but adds quality/CPU/latency scope.

Do not pick these silently.

## M1G non-negotiable correctness rules

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

## M1G test plan

Deterministic/component proof should cover:

- starvation/wakeup/EOF/cancel semantics of encoded input;
- progressive MP3 across several M1F windows;
- MP3 seek/pre-roll and stale-output cancellation;
- exact common-WAV sample conversion vectors;
- stereo downmix and >2-channel rejection;
- PCM queue cap/backpressure;
- renderer-facing nonblocking reads and underrun != EOF;
- stop/seek/replacement cleanup;
- no complete-song encoded or decoded accumulation.

Focused Minecraft M1G acceptance must later prove audible MP3 + WAV, pause/resume/seek/loop/stop, bounded long-file behavior, positional attenuation, and preserved standard CC:T behavior.

## M1H boundary

Do not pull the full listener lifecycle into M1G.

M1H owns:

- late range entry;
- proactive leave cleanup;
- leave/return rejoin;
- dimension/resource reload recovery;
- robust underrun rejoin;
- final VS2 moving-listener lifecycle.

M1G should provide safe local cancellation/restart primitives that M1H can use later.

## Documentation drift note

Current final M1F source/finalization overrides two stale transitional statements in `ARCHITECTURE.md`:

- active max range is 128 KiB, not the older 256 KiB wording;
- modern prepared transport no longer uses whole-file server push/client `.part/.media` bridging.

Do not implement from those stale paragraphs.

## Read order before M1G implementation

1. `HANDOFF-2026-09-13-PRE-M1G.md`
2. `PRE-M1G-PREPARATION.md`
3. `M1F-FINALIZATION-2026-09-13.md`
4. `CURRENT-STATE.md`
5. `KNOWN-ISSUES.md`
6. `TESTING.md`
7. `VERIFIED-FACTS.md`
8. `ROADMAP.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `LUA-API.md`
11. exact current source and current CI

If a new correctness/design choice appears during implementation, stop and ask the owner before selecting among meaningful tradeoffs.
