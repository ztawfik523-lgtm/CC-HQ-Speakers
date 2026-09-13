# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G prepared but not started.**

Preparation branch:

`codex/m1g-preparation`

Preparation base/current finalized-M1F documentation head:

`8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365` — both NeoForge targets passed build/tests/package verification/artifact upload.

Current docs-head verification run `34763711105` also passed both targets, and the owner-requested fresh rerun passed both target jobs again.

Focused Minecraft M1F transport acceptance is not recorded. Never convert CI into a runtime PASS.

Read before any M1G implementation:

1. `docs/HANDOFF-2026-09-13-PRE-M1G.md`
2. `docs/PRE-M1G-PREPARATION.md`
3. `docs/M1F-FINALIZATION-2026-09-13.md`
4. `docs/CURRENT-STATE.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ROADMAP.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/LUA-API.md`
11. exact current source and CI

Historical/pre-M1F documents do not override these records.

## M1G stop condition

**Do not start M1G Java/resource implementation until the owner chooses the three decision gates in `PRE-M1G-PREPARATION.md`.**

If a further meaningful correctness/design choice appears, present the options/tradeoffs and ask before choosing.

## Product rule

This is a programmable ComputerCraft speaker peripheral.

Lua decides application meaning/policy: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc. Java models technical capabilities only.

Do not create permanent Java music/effect/notification lanes or Java playlist policy. Do not infer application role from MP3/WAV/etc.

One physical speaker remains one mono positional source.

## Standard CC:T compatibility

The normal `computercraft:speaker` remains the product surface and exposed type `speaker`.

Preserve native/delegated:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW uses separate `hqspeaker_audio_empty` backpressure.

## Modern finite Lua/API

Recommended one-call path:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, path, { volume = 0.6 })
```

Reusable path:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

Modern finite controls remain:

```text
audioStatus
audioPause
audioResume
audioSeek
audioSetVolume
audioSetLooping
audioStop
```

`audioPlayStaged()` was a project prototype and remains removed. Temporary staging is import plumbing only.

## M1E authority — closed unless new evidence appears

Server owns finite generation/state/time/control/EOF. Client READY refreshes state; client ERROR is diagnostic. Client/render failures are not canonical playback authority.

Final M1E code: `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final focused Minecraft acceptance was explicitly skipped. Do not claim it passed.

## M1F transport — finalized contract

Explain it first as:

```text
server owns complete MediaAsset
-> authoritative STATE selects encoded anchor
-> nearby client requests bounded encoded pieces
-> server reads pieces off-thread
-> client keeps bounded sliding encoded RAM
-> M1G consumes that window
```

Preserve:

- protocol v5 bounded range request/data;
- no modern whole-file push or complete-song client file;
- first demand waits for authoritative STATE;
- source/asset/generation/range/relevance validation;
- current 128 KiB max range / 512 KiB client window as tuning values;
- bounded off-thread reads/outstanding work;
- in-flight asset retain/retry-safe release;
- stale completion discard;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- arbitrary re-anchor;
- forward sliding/discard preserving unread prefetch;
- range shutdown before store cleanup.

Do not restore the old CHUNK/END or `.part/.media` bridge.

## M1G starting facts

### Modern insertion point

`HQFiniteMediaClient` is transport-only. It owns the active BEGIN descriptor, M1F window, anchor state, range pumping, and terminal cancellation. It currently has no modern decoder, PCM queue, or renderer.

Build M1G there rather than routing the modern path back through inherited finite playback.

### Old finite classes are not the modern engine

- `FileFiniteAudioStream` requires a complete local file.
- `HQAudioStream` finite mode decodes a complete finite payload and retains the whole decoded track.
- `FiniteAudioTrack` stores track-length PCM.

They may remain for legacy code, but M1G must not use them as its modern prepared architecture.

### MP3

The project already packages JLayer `1.0.1.4`. Existing live MP3 code proves the exact dependency can be used frame-by-frame with `Bitstream`, `Decoder`, and `SampleBuffer`.

M1G needs a cancellation/starvation-aware worker input over `FiniteRangeWindow`. `NEED_DATA` must wait/refill and must never be exposed as `InputStream` EOF.

MP3 seek/rejoin must start from an earlier server-provided encoded anchor and silently pre-roll Layer III state to the audible target.

A semantic seek must restart decoder/pre-roll state even when the encoded anchor byte is unchanged.

### WAV

Current `FiniteMediaAnalyzer` accepts historical WAV shapes broader than final M1G support. Current `MediaMetadata` does not yet contain a normalized final common-WAV layout descriptor.

Final M1G prepared/local target is mono/stereo unsigned 8 PCM, signed 16/24/32 PCM, and 32-bit IEEE float. Reject >2 channels, A-law/mu-law, compressed/telephony WAV, 64-bit float, and unusual widths.

## M1G decision gates — owner must choose

### A. Renderer path

- A1: custom Minecraft `AudioStream` through normal `SoundManager` positional sound.
- A2: direct Channel/OpenAL queued-buffer ownership.

A1 is smaller/more native; A2 gives more direct underrun control but pulls substantial M1N/OpenAL lifecycle work forward.

### B. WAV layout ownership

- B1: server analyzer normalizes layout and carries it to clients, likely with a wire/protocol metadata change.
- B2: client progressively parses WAV layout from M1F bytes.

B1 centralizes validation/direct seek; B2 avoids larger wire metadata but duplicates parsing and delays layout knowledge.

### C. Finite sample-rate policy

- C1: preserve source sample rate.
- C2: resample all modern finite PCM to 48 kHz.

C1 avoids a resampler; C2 standardizes PCM but adds CPU/quality/latency scope.

Do not choose these silently.

## M1G non-negotiable correctness rules

- encoded memory stays bounded independently of duration;
- decoded PCM stays bounded independently of duration;
- decoder blocks/waits only on decoder workers;
- Minecraft/audio thread never performs network/disk/codec work;
- missing encoded data and empty PCM are not terminal EOF;
- seek/replace/stop invalidates stale encoded waits, decoder output, PCM, and renderer state;
- server M1E state remains canonical;
- renderer failure is local/diagnostic;
- one physical speaker remains one mono positional source.

## M1H remains separate

M1H owns complete late-entry/proactive-leave/return-rejoin/dimension/reload/robust-underrun/VS2 listener lifecycle. M1G should provide safe local cancellation/restart primitives for it.

## Documentation drift warning

Two old `ARCHITECTURE.md` transitional statements are superseded:

- current M1F max range is 128 KiB, not the older 256 KiB wording;
- modern prepared transport no longer uses whole-file server push/client `.part/.media` bridging.

Use current source/finalization/pre-M1G docs for those facts.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. current preparation/finalization/current-state records;
4. exact current CI/package evidence;
5. verified-facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. historical milestone/handoff docs.

Green CI is not Minecraft runtime proof.
