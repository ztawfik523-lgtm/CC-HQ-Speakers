# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E and M1F source/test/CI/package complete; M1G not started.**

## Resolved by M1E final hardening

### KI-040 — authoritative finite packet delivery could escape canonical transitions

**Resolved** at M1E final hardening. Client/network projection is best-effort and per-recipient isolated.

### KI-041 — `playPrepared()` rollback could leave an installed ghost session

**Resolved.** Throwable construction/snapshot work completes before canonical session installation.

### KI-042 — failed final MediaAsset releases could lose their retry owner

**Resolved for active prepared/finite/range paths.** `MediaAssetReleaseQueue` retains retry responsibility.

### KI-045 — finite server ERROR could keep advancing position

**Resolved.** ERROR freezes canonical server position.

## Resolved by M1F finalization

### KI-043 — encoded range window was re-anchorable but not sliding/consumable

**Resolved** at final M1F candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`.

`FiniteRangeWindow` now supports forward `advanceTo(...)`, discards consumed prefix data, preserves unread overlapping prefetched bytes, retains only whole still-useful in-flight requests, exposes new bounded tail demand, and shifts the active byte buffer in place to avoid full-window allocation churn.

The window also starts unanchored, so BEGIN cannot independently create byte-zero demand before authoritative STATE supplies an encoded anchor.

### KI-044 — original M1F deterministic acceptance matrix was only partially covered

**Resolved at deterministic/component level.**

Coverage now includes:

- source/generation/asset/bounds validation;
- same-dimension/current-range relevance rule;
- stale completion identity;
- per-player limits;
- exact arbitrary ranges;
- off-thread production read execution;
- in-flight asset lifetime;
- queued cancellation cleanup;
- deferred release retry ownership;
- retryable shutdown timeout without premature store close;
- anchor gating;
- missing-data vs true EOF;
- sliding/refill beyond one window;
- integrated fake server-reader -> client-window transport.

`M1F-FINALIZATION-2026-09-13.md` contains the complete matrix.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

M1E source/test/CI/package work is complete.

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.**

M1F source/test/CI/package work is complete, but there is no recorded focused real client/server transport PASS. Do not infer one from CI/component tests.

M1F is intentionally silent until M1G, so lack of audible song output is not an M1F failure.

## Source-resolved by M1F

### KI-026 — modern prepared client required a complete local file

**Resolved.** Modern prepared transport keeps bounded encoded RAM instead of a complete client song file.

### KI-027 — modern finite asset reads ran on the server tick

**Resolved.** Range reads use the bounded `FiniteRangeReadService` executor.

### KI-030 — no demand-driven finite protocol

**Resolved.** Protocol v5 carries bounded range request/data messages.

### KI-031 — missing network data was not distinct from true EOF

**Resolved.** `FiniteRangeWindow` has DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

### KI-038 — project prototype `audioPlayStaged()` created a second finite path

**Resolved.** The direct-staged command is removed; staging is import plumbing only.

### KI-039 — old modern finite CHUNK/END whole-file protocol remained active

**Resolved.** Modern prepared transfer uses range request/data only.

## Active high priority — M1G progressive decoder/rendering

### KI-035 — obsolete JavaSound/mp3spi complete-file bridge is not a correct modern MP3 engine

Known old problems include first-PCM-read failure, duration disagreement with the server analyzer, and incompatible seek/skip assumptions. M1G replaces this path rather than repairing it.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

M1G must decode sufficient earlier context to rebuild reservoir state before audible target output.

### KI-033 — historical WAV acceptance is broader than final converter target

M1G should implement the agreed common mono/stereo PCM/float subset with exact layout metadata and safe mono downmix.

### KI-021 — active advertised finite format surface still needs final narrowing

Historical analyzer/legacy breadth does not define final prepared/local support. Core target remains MP3 + implemented common WAV.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1H still owns proactive out-of-range cleanup, late-entry discovery, return/rejoin at current server time, dimension/world/resource-reload recovery, underrun rejoin, and final VS2 movement lifecycle.

M1F only validates current relevance for each range request/completion.

## Gated/later work

### KI-034 — FLAC desired but unproven

M1I gated. No final native FLAC analyzer/decode/seek/runtime path is proven.

### KI-018 — inherited multispeaker expected-member barrier can deadlock partial listeners

M1J.

### KI-011 / KI-012 / KI-023 — inherited finite engine/APIs are still legacy

M1L migration/removal.

## RAW/live/OpenAL/release issues

- KI-013: live stream volume can be applied twice — later live/OpenAL cleanup.
- KI-014 / KI-015 / KI-016 / KI-017 / KI-019: inherited HLS/TS/live lifecycle problems — M3.
- KI-022: separate `hqspeaker:hq_speaker` registration duplicates product direction — release cleanup after world-compatibility review.
- KI-025: top-level LICENSE is MPL-2.0 while NeoForge metadata declares LGPL-3.0 — resolve provenance before public release; do not silently relicense.

## Reminders

- M1E final manual Minecraft PASS remains skipped/unrecorded.
- M1F focused Minecraft transport PASS remains unrecorded.
- M1F is not audible by itself; M1G owns decoding/rendering.
- Old `FileFiniteAudioStream` may remain for legacy code even though modern prepared M1F does not use it.
- Historical scripts/docs may intentionally describe behavior from their checkpoint.
