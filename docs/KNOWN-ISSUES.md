# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E/M1F reevaluation hold; M1G not started.**

`M1E-M1F-REEVALUATION-2026-09-13.md` supersedes earlier `M1E finalized` / `M1F source-test-CI complete` wording.

## Reopened correctness / acceptance issues

### KI-040 — authoritative finite packet delivery is not exception-isolated

**Status: active shared M1E/M1F correctness issue.**

`HQFiniteMediaServer` currently performs client packet sends inline during start/stop/control/state paths without isolating per-player runtime send failures.

Normal-path semantics are server-authoritative, but a runtime packet-send exception can still escape an authoritative operation. Client/network delivery should be best-effort projection of server truth, not a possible owner of canonical success/failure.

No runtime incident is recorded; this is a source-level failure-path defect found during reevaluation.

### KI-041 — `playPrepared()` rollback can leave an installed ghost session

**Status: active shared M1E/M1F correctness issue.**

Current start flow assigns `session = next` before sending BEGIN/STATE. The surrounding runtime-exception catch releases the retained playback asset but does not remove that installed session or repair its ownership flag.

If a runtime exception occurs after session assignment, the finite server can retain a PLAYING session whose asset-ownership bookkeeping no longer matches the store. At the composite layer, a thrown start does not set STAGED_FINITE ownership, creating a ghost-session possibility.

### KI-042 — failed final MediaAsset releases can lose their retry owner

**Status: active shared asset-lifetime issue.**

`MediaAssetStore.release()` deliberately preserves the entry/reference when final file deletion fails.

Current callers do not all preserve ownership until release succeeds:

- `HQFiniteMediaServer.releaseAssetReference()` clears `assetReferenceHeld` before calling the store;
- a `FiniteRangeReadService` task can exit after a failed final release with no surviving retry owner;
- `HQMediaStaging.releaseDetached()` logs a failed release after the detached computer's prepared-ownership set has already been removed;
- the detach-during-prepare cleanup path has the same rare log-and-forget pattern.

The staging cases predate M1E/M1F, but they reveal the same foundational rule: **release attempted is not the same as release succeeded**.

A retry-safe owner/cleanup strategy is needed for these rare failure paths.

### KI-043 — M1F encoded window is re-anchorable but not truly sliding/consumable

**Status: M1F completeness issue.**

`FiniteRangeWindow` supports `reset(anchorOffset)`, request/accept/probe/copy/retry/cancel, but it has no consume/discard/advance API which drops a consumed prefix while retaining useful unread prefetched bytes.

A future decoder could wait until an entire 512 KiB window is consumed and then reset at the old end, but that creates hard refill boundaries. Resetting earlier discards useful prefetched bytes.

The range protocol itself does not need redesign; the client buffer boundary needs strengthening before it is treated as a clean progressive M1G input.

### KI-044 — original M1F deterministic acceptance matrix is only partially covered

**Status: acceptance reopened.**

Current M1F-specific unit tests cover the encoded window and range read service well, but not all items the pre-M1F contract said `M1F tests must prove`.

Missing dedicated deterministic/component coverage includes:

- complete server source/generation/asset validation;
- relevance/dimension gating;
- stale async completion after replacement/leave/disconnect;
- shutdown ordering/retry behavior;
- packet codec/bounds integration;
- client BEGIN/STATE anchor gating;
- consume/discard progression;
- complete authoritative server state-machine rollback/failure behavior.

Some are supported by source review, but that is not the same as deterministic acceptance proof.

## Evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Status: intentionally skipped by project owner; do not claim PASS.**

M1E normal-path source semantics, clock tests, both-target CI/package evidence, and supporting diagnostic logs exist. The final focused Minecraft script was not run to a recorded PASS.

The reevaluation additionally found KI-040/041/042, so the old `M1E finalized` label is no longer current even at source-hardening level.

### KI-037 — M1F Minecraft runtime transport acceptance not recorded

**Status: implementation/CI evidence exists; runtime unperformed.**

The range architecture is implemented and both target CI jobs are green, but a real client/server transport run has not been recorded to prove live request/data integration, seek-driven non-zero demand, real game-directory cache behavior, stale completion lifecycle, or shutdown behavior.

## Source-resolved by M1F and still valid

### KI-026 — modern prepared client required a complete local file

**Status: source-resolved in M1F.**

`HQFiniteMediaClient` no longer builds a complete `.part/.media` song before decoding. The modern path uses bounded encoded RAM.

Old development cache files may still exist from older builds; active M1F code does not use them.

### KI-027 — modern finite asset reads ran on the server tick

**Status: source-resolved in M1F.**

Prepared finite range reads run through bounded `FiniteRangeReadService` workers. Server tick owns canonical timeline work only.

### KI-030 — no demand-driven finite protocol

**Status: source-resolved in M1F.**

Protocol v5 provides client range request/data identity with source/generation/asset/offset/length, bounded work, and current-session/relevance revalidation before send.

### KI-031 — transport could not distinguish missing bytes from true EOF

**Status: source-resolved at the encoded-window API.**

`FiniteRangeWindow` distinguishes `DATA_AVAILABLE`, `NEED_DATA`, `TRUE_ASSET_EOF`, and `CANCELLED_OR_STALE`.

M1G still must consume these states correctly.

### KI-038 — project-prototype direct staged play created a second finite path

**Status: removed in M1F.**

`audioPlayStaged()` and direct staged playback are gone. Staging remains import-only; modern local files use server MediaAssets.

### KI-039 — old modern finite CHUNK/END whole-file protocol remained active

**Status: removed in M1F.**

`HQFiniteMediaChunkPacket` and `HQFiniteMediaEndPacket` are removed from source/registration. Modern finite transfer is range request/data.

## Active high priority — M1G progressive decoder/rendering

M1G is currently blocked on the owner choosing how to handle KI-040 through KI-044.

### KI-035 — old JavaSound/mp3spi MP3 bridge is not a correct seek/duration oracle

**Status: diagnosed; no longer used by modern M1F prepared client; replacement remains M1G.**

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

M1H still owns:

- proactive client cleanup when leaving range;
- late-entry discovery;
- return/rejoin at current server time;
- dimension/world/resource reload recovery;
- underrun rejoin;
- final VS2 movement lifecycle.

Do not reclassify the intentional M1H split as an M1F regression.

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
- Old `FileFiniteAudioStream` source may remain even though modern M1F no longer references it.
- Historical scripts/docs remain evidence for their checkpoint and may intentionally describe old behavior.
