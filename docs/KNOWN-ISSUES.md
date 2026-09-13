# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E and M1F source/test/CI/package complete; M1G implementation in progress.**

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed both target NeoForge versions.

## Resolved by M1E final hardening

### KI-040 — authoritative finite packet delivery could escape canonical transitions

**Resolved.** Client/network projection is best-effort and per-recipient isolated.

### KI-041 — `playPrepared()` rollback could leave an installed ghost session

**Resolved.** Throwable construction/snapshot work completes before canonical session installation.

### KI-042 — failed final MediaAsset releases could lose their retry owner

**Resolved for active prepared/finite/range paths.** `MediaAssetReleaseQueue` retains retry responsibility.

### KI-045 — finite server ERROR could keep advancing position

**Resolved.** ERROR freezes canonical server position.

## Resolved by M1F finalization

### KI-043 — encoded range window was re-anchorable but not sliding/consumable

**Resolved** at final M1F candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`.

`FiniteRangeWindow` supports forward `advanceTo(...)`, discards consumed prefix data, preserves unread overlapping prefetched bytes, retains only whole still-useful in-flight requests, exposes new bounded tail demand, and shifts its active byte buffer in place.

The window starts unanchored, so BEGIN cannot independently create byte-zero demand before authoritative STATE supplies an encoded anchor.

### KI-044 — original M1F deterministic acceptance matrix was only partially covered

**Resolved at deterministic/component level.** See `M1F-FINALIZATION-2026-09-13.md`.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.**

M1F is intentionally silent until M1G, so lack of audible song output is not an M1F failure.

### KI-046 — M1G audible Minecraft acceptance is not recorded

**Expected while M1G is in progress.**

The current M1G checkpoint proves format/layout/anchor logic and bounded client primitives, not progressive decoder/render integration or actual sound.

## Source-resolved by M1F

### KI-026 — modern prepared client required a complete local file

**Resolved.** Modern prepared transport keeps bounded encoded RAM instead of a complete client song file.

### KI-027 — modern finite asset reads ran on the server tick

**Resolved.** Range reads use the bounded `FiniteRangeReadService` executor.

### KI-030 — no demand-driven finite protocol

**Resolved.** Protocol v5 carries bounded range request/data messages at the finalized M1F checkpoint.

### KI-031 — missing network data was not distinct from true EOF

**Resolved.** `FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

### KI-038 — project prototype `audioPlayStaged()` created a second finite path

**Resolved.** The direct-staged command is removed; staging is import plumbing only.

### KI-039 — old modern finite CHUNK/END whole-file protocol remained active

**Resolved.** Modern prepared transfer uses range request/data only.

## M1G issues partially resolved by the start checkpoint

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Anchor policy resolved; decoder integration still active.**

`FiniteDecodeAnchorSelector` now implements E1: approximately one second of safety margin followed by the newest existing valid MP3 seek point at/before that safe time. The future JLayer decoder still needs to decode silently from that earlier point and discard pre-target PCM.

### KI-033 — historical WAV acceptance is broader than final converter target

**Prepared/local acceptance and normalized layout are now narrowed; sample conversion still active.**

`ModernFiniteMediaAnalyzer` accepts MP3 or the chosen common-WAV subset only. `CommonWavAnalyzer` normalizes U8/S16/S24/S32/F32 mono/stereo layouts, supports narrow PCM/float WAVEX with equal valid/container width, respects RIFF bounds, and rejects unsupported shapes. Progressive sample conversion/downmix is not implemented yet.

### KI-021 — active advertised finite format surface still needs final narrowing

**Partially resolved.**

New `prepareAsset` imports now use the MP3/common-WAV modern gate. The modern finite wire still has historical `MP3/OGG/AUDIO_FILE` descriptor ambiguity and must be replaced by the M1G MP3/WAV descriptor before decoder integration.

### KI-047 — M1G client primitives are not yet wired into the modern finite session

`FiniteEncodedInputStream` and `FinitePcmQueue` are implemented/tested, but `HQFiniteMediaClient.Session` still owns only transport/window/anchor state. Range arrivals do not yet signal an active decoder input because no decode epoch is installed yet.

## Active high priority — M1G progressive decoder/rendering

### KI-035 — obsolete JavaSound/mp3spi complete-file bridge is not a correct modern MP3 engine

Known old problems include first-PCM-read failure, duration disagreement with the server analyzer, and incompatible seek/skip assumptions. M1G replaces this path rather than repairing it.

### KI-048 — progressive common-WAV sample conversion is not implemented

Need bounded conversion of U8/S16/S24/S32/F32 mono/stereo to mono S16 at source sample rate, with exact deterministic vectors and safe clamp/downmix behavior.

### KI-049 — progressive JLayer finite MP3 decoder is not implemented

Need JLayer frame decode over the starvation-aware M1F input, bounded PCM output, E1 pre-roll discard, cancellation and stale-epoch rejection.

### KI-050 — modern finite positional renderer is not integrated

Need A1 custom `AudioStream`/`SoundManager` rendering over the bounded PCM queue. Renderer reads must stay nonblocking, queued latency bounded, and seek/replacement must discard old renderer state.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1H still owns proactive out-of-range cleanup, late-entry discovery, return/rejoin at current server time, dimension/world/resource-reload recovery, robust underrun rejoin, and final VS2 movement lifecycle.

M1F validates current relevance for each range request/completion; M1G should supply safe local cancellation/restart primitives for M1H.

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
- M1G is now active but not audibly proven.
- Old `FileFiniteAudioStream` / whole-track finite classes may remain for inherited/legacy paths but are not the modern engine.
- Historical scripts/docs may intentionally describe behavior from their checkpoint.
