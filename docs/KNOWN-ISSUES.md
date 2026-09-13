# Known issues / product gaps

Severity here is project priority, not a security claim. Source/CI success is not Minecraft runtime proof.

Current checkpoint: **M1F source/test/CI complete; M1G not started.**

## Evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Status: intentionally skipped by project owner; do not claim PASS.**

M1E source/tests/CI and supporting diagnostic logs exist, but the final focused Minecraft script was not run to a recorded PASS. This is no longer a sequencing gate by owner decision, but the evidence gap remains historical fact.

### KI-037 — M1F Minecraft runtime transport acceptance not recorded

**Status: source/test/CI complete; runtime pending/unperformed.**

M1F deterministic tests and both target CI jobs pass. A real client/server run has not yet been recorded to prove live range traffic/client lifecycle.

## Source-resolved by M1F

### KI-026 — modern prepared client required a complete local file

**Status: source-resolved in M1F.**

`HQFiniteMediaClient` no longer builds a complete `.part/.media` song before decoding. The modern path now keeps a bounded encoded RAM window.

Old development cache files may still exist on a user's disk from older builds; active M1F code does not use them.

### KI-027 — modern finite asset reads ran on the server tick

**Status: source-resolved in M1F.**

Prepared finite range reads now run through bounded `FiniteRangeReadService` workers. Server tick owns canonical timeline work only.

### KI-030 — no demand-driven finite protocol

**Status: source-resolved in M1F.**

Protocol v5 adds bounded client range request/data packets with generation/asset/offset identity, current relevance validation, outstanding caps, and stale completion discard.

### KI-031 — transport could not distinguish missing bytes from true EOF

**Status: source-resolved at the M1F encoded-window boundary.**

`FiniteRangeWindow` distinguishes `DATA_AVAILABLE`, `NEED_DATA`, `TRUE_ASSET_EOF`, and `CANCELLED_OR_STALE`.

M1G still must consume this distinction correctly in the decoder worker.

### KI-038 — project-prototype direct staged play created a second finite path

**Status: removed in M1F.**

`audioPlayStaged()` and the direct staged playback path are gone. Staging remains import-only; modern local files use server MediaAssets.

### KI-039 — old modern finite CHUNK/END whole-file protocol remained active

**Status: removed in M1F.**

`HQFiniteMediaChunkPacket` and `HQFiniteMediaEndPacket` are removed from source/registration. Modern finite transfer is range request/data.

## Active high priority — M1G progressive decoder/rendering

### KI-035 — old JavaSound/mp3spi MP3 bridge is not a correct seek/duration oracle

**Status: diagnosed; no longer used by the modern M1F prepared client; replacement remains M1G.**

Known evidence:

- old first PCM read could end immediately after seek;
- mp3spi reported `322.584 s` while the server analyzer reported `161.304 s` for the tested MP3;
- old seek mixes decoded-byte assumptions with compressed-stream skip semantics;
- old renderer restart can seek twice.

Do not repair this obsolete bridge merely for temporary audibility.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Status: M1G.**

M1F can request arbitrary earlier encoded ranges and STATE supplies a server-selected anchor. M1G must choose/use enough earlier context to rebuild Layer III reservoir state before audible output.

### KI-033 — historical WAV acceptance is broader than final converter target

**Status: M1G.**

Final prepared/local WAV should be mono/stereo common PCM 8/16/24/32 plus float32. M1G must add exact PCM layout/time-to-byte metadata and conversion/downmix.

### KI-021 — active advertised finite format surface still needs final narrowing

**Status: M1G/M1L.**

Frozen M1D historically analyzed OGG/AIFF/AU. Final core product promise remains MP3 + implemented common WAV. Do not advertise broader historical analyzer capability as final support.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1F revalidates player relevance before accepting/sending range data, so stale async completions are dropped.

M1H still must provide:

- proactive client cleanup when leaving range;
- late-entry discovery;
- return/rejoin at current server time;
- dimension/world/resource reload recovery;
- underrun rejoin;
- final VS2 movement lifecycle.

Do not call M1F's server-side relevance checks a complete M1H solution.

## Gated/later finite work

### KI-034 — FLAC desired but unproven

**Status: M1I gated.**

No native FLAC final analyzer/decode/seek/runtime path is proven. MP3/WAV completion does not wait for FLAC.

### KI-018 — inherited multispeaker expected-member barrier can deadlock partial listeners

**Status: M1J.**

Replace with shared server sync clocks without global expected-tap barriers.

### KI-011 / KI-012 / KI-023 — inherited finite engine/APIs are still legacy

Legacy `HQAudioStream`, whole decoded PCM, one-shot byte APIs, and related state remain until M1L migration/removal. Do not model new finite work on them.

## RAW/live/OpenAL/release issues

### KI-013 — live stream volume can be applied twice

Target later live/OpenAL cleanup.

### KI-014 / KI-015 / KI-016 / KI-017 / KI-019 — inherited HLS/TS/live lifecycle problems

Target M3.

### KI-022 — separate `hqspeaker:hq_speaker` registration duplicates product direction

Target release cleanup after considering existing-world registry compatibility.

### KI-025 — license metadata mismatch

Top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0. Resolve actual provenance before public release; do not silently relicense.

## Runtime/cleanup reminders

- M1F is not audible by itself; silence is expected until M1G attaches a decoder/renderer.
- Old `FileFiniteAudioStream` source may remain even though modern M1F no longer references it; remove/review dependencies only after replacement decode is proven.
- Historical scripts/docs remain evidence for their milestone and may intentionally describe old behavior.
