# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E source/test/CI complete; M1F acceptance/completeness still provisional; M1G not started.**

## Resolved by M1E final hardening

### KI-040 — authoritative finite packet delivery could escape canonical transitions

**Status: resolved at `521d4323d9216c8a99e8ec60426997c3330c4068`.**

Client/network projection is now best-effort. Runtime projection failure cannot abort canonical start/stop/control/state transitions. Delivery is also isolated per nearby player, so one failing recipient does not abort later recipients in the same broadcast.

### KI-041 — `playPrepared()` rollback could leave an installed ghost session

**Status: resolved.**

Throwable construction/snapshot work is completed before the new session is installed. Pre-install failure releases the retained playback reference through the retry-safe owner and leaves no canonical session installed. Work after installation is best-effort projection only.

### KI-042 — failed final MediaAsset releases could lose their retry owner

**Status: resolved for the active prepared/finite/range paths.**

`MediaAssetReleaseQueue` centrally owns deferred logical releases. Playback references, detached/rejected prepared assets, and M1F in-flight range references transfer retry responsibility instead of logging-and-forgetting a final deletion failure.

Retries are throttled. Server shutdown drains range IO before store cleanup.

### KI-045 — finite server ERROR could keep advancing position

**Status: resolved.**

`FinitePlaybackStateMachine.fail()` freezes the canonical position at the server failure instant.

## Evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Status: intentionally skipped by project owner; do not claim PASS.**

M1E source implementation, deterministic tests, and both-target CI/package verification are complete. The final focused Minecraft acceptance script was not run because the owner explicitly chose to skip it.

This no longer blocks the M1E source milestone; it remains an explicit runtime-evidence boundary.

### KI-037 — M1F Minecraft runtime transport acceptance not recorded

**Status: active M1F evidence gap.**

Range architecture and CI evidence exist, but a focused real client/server range-transport run is not recorded.

## Active M1F completion issues

### KI-043 — M1F encoded window is re-anchorable but not truly sliding/consumable

**Status: active M1F completeness issue.**

`FiniteRangeWindow` supports arbitrary reset/anchor, request, accept, probe, copy, retry, and cancel, but has no consume/discard/advance operation which drops a consumed prefix while retaining useful unread prefetched bytes.

The range protocol does not require redesign. The client buffer boundary should be strengthened before M1G relies on it as a progressive decoder input.

### KI-044 — original M1F deterministic acceptance matrix is only partially covered

**Status: active M1F acceptance issue.**

Current tests cover the range window/read service well, but dedicated deterministic/component proof is still incomplete for the complete server request identity/relevance/stale-completion path, shutdown integration, packet codec/bounds integration, client BEGIN/STATE anchor gating, and sliding consume/discard progression.

M1E state-machine/projection/release failure-path coverage is no longer part of this gap; that portion was closed by the final M1E hardening pass.

## Source-resolved by M1F and still valid

### KI-026 — modern prepared client required a complete local file

**Status: source-resolved in M1F.**

Modern prepared transport uses bounded encoded RAM instead of building a complete client `.part/.media` song.

### KI-027 — modern finite asset reads ran on the server tick

**Status: source-resolved in M1F.**

Prepared finite range reads use bounded `FiniteRangeReadService` workers. Server tick owns semantic timeline work only.

### KI-030 — no demand-driven finite protocol

**Status: source-resolved in M1F.**

Protocol v5 provides bounded client range request/data transport with source/generation/asset/offset identity.

### KI-031 — transport could not distinguish missing bytes from true EOF

**Status: source-resolved at the encoded-window API.**

`FiniteRangeWindow` distinguishes DATA_AVAILABLE, NEED_DATA, TRUE_ASSET_EOF, and CANCELLED_OR_STALE.

### KI-038 — project-prototype direct staged play created a second finite path

**Status: removed in M1F.**

`audioPlayStaged()` and direct staged playback are gone. Staging remains import-only.

### KI-039 — old modern finite CHUNK/END whole-file protocol remained active

**Status: removed in M1F.**

Modern finite transfer is range request/data.

## Active high priority — M1G progressive decoder/rendering

### KI-035 — old JavaSound/mp3spi MP3 bridge is not a correct seek/duration oracle

**Status: diagnosed; obsolete for modern M1F prepared transport; replacement remains M1G.**

Known evidence includes old first-PCM-read failure, mp3spi duration disagreement with the server analyzer, and incompatible seek/skip assumptions.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Status: M1G.**

M1F can request earlier encoded ranges; M1G must use sufficient earlier context to rebuild Layer III reservoir state before audible output.

### KI-033 — historical WAV acceptance is broader than final converter target

**Status: M1G.**

Final prepared/local WAV should be common mono/stereo PCM/float formats with exact layout metadata and mono downmix.

### KI-021 — active advertised finite format surface still needs final narrowing

**Status: M1G/M1L.**

Historical analyzer breadth does not define the final support promise. Core target remains MP3 + implemented common WAV.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1H still owns proactive out-of-range cleanup, late-entry discovery, return/rejoin at current server time, dimension/world/resource-reload recovery, underrun rejoin, and final VS2 movement lifecycle.

## Gated/later finite work

### KI-034 — FLAC desired but unproven

**Status: M1I gated.**

No native FLAC final analyzer/decode/seek/runtime path is proven.

### KI-018 — inherited multispeaker expected-member barrier can deadlock partial listeners

**Status: M1J.**

Replace with shared server sync clocks without global expected-tap barriers.

### KI-011 / KI-012 / KI-023 — inherited finite engine/APIs are still legacy

Legacy whole-decoded/one-shot finite state remains until M1L migration/removal.

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

- M1E final manual Minecraft PASS remains skipped/unrecorded.
- M1F is not audible by itself; silence is expected until M1G attaches the progressive decoder/renderer.
- Old `FileFiniteAudioStream` source may remain even though modern M1F no longer uses it.
- Historical scripts/docs remain evidence for their checkpoint and may intentionally describe old behavior.
