# Current state

## Current checkpoint

The project is at an **M1E/M1F reevaluation hold**.

The main architecture remains accepted:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> bounded client-requested encoded ranges (M1F)
    -> progressive decode/PCM/rendering later (M1G)
```

However, the earlier labels `M1E finalized` and `M1F source/test/CI complete` are now superseded by `M1E-M1F-REEVALUATION-2026-09-13.md`.

Current Java/source code remains:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Current implementation branch:

`codex/m1f-demand-driven-finite`

No Java was changed during the reevaluation.

## Evidence status

M1E:

- server-authoritative normal-path semantics are still present by current source review;
- `FinitePlaybackClockTest` covers core clock math;
- previous both-target CI/package verification remains valid;
- historical runtime diagnostics support authority separation;
- the final focused Minecraft M1E PASS was skipped by explicit owner decision and remains unrecorded;
- deterministic `HQFiniteMediaServer` state-machine/failure-path coverage is still missing.

M1F:

- protocol-v5 bounded range transport is implemented;
- modern client whole-song `.part/.media` caching is removed;
- range-window/read-service unit tests and both-target CI/package verification are green;
- focused Minecraft M1F transport acceptance is not recorded;
- the original deterministic M1F acceptance matrix is only partially covered;
- the encoded window is re-anchorable but lacks the intended sliding consume/discard interface for clean progressive refill.

Therefore neither M1E nor M1F should currently be described as fully accepted/finalized.

## Reopened correctness issues

### 1. Client packet sends can escape authoritative transitions

`HQFiniteMediaServer` sends packets inline without isolating per-player runtime send failures. A client/network-send exception can therefore escape start/stop/control/state operations even though M1E's model says client delivery must not own canonical server truth.

This is a source-level robustness defect; it is not a recorded runtime failure.

### 2. `playPrepared()` rollback is not atomic

The new session is assigned before BEGIN/STATE delivery. If a later runtime exception occurs, the catch releases the retained asset but leaves the installed session/ownership flag in place.

At the composite layer, a thrown start does not set STAGED_FINITE ownership, so a ghost finite session is possible in that failure path.

### 3. Playback asset release forgets ownership before successful release

`releaseAssetReference()` clears its held flag before `MediaAssetStore.release()` succeeds.

If final file deletion fails, `MediaAssetStore` correctly preserves the reference, but the finite session has already forgotten it and cannot retry. This can retain file/quota state until shutdown.

M1F in-flight range-release cleanup has a related rare final-release retry gap.

### 4. `FiniteRangeWindow` is not a true sliding consumer window yet

It can request, accept, probe, copy, retry, cancel, and `reset()` at arbitrary encoded anchors. It cannot advance/discard consumed prefix bytes while keeping useful unread prefetched bytes.

M1G could reset only after consuming the full current 512 KiB window, but that creates hard refill boundaries. Resetting earlier discards useful prefetched bytes.

This does not require changing protocol v5, but the M1F/M1G buffer boundary should be strengthened before relying on it as a progressive decoder input.

## M1E server authority that still stands

Current source still implements:

- PLAYING / PAUSED / ENDED / ERROR;
- immediate server-clock start after successful prepared play;
- canonical pause/resume/seek/loop/volume;
- duration-driven natural EOF;
- non-looping `seek(duration)` -> ENDED;
- looping `seek(duration)` -> wrap to zero;
- client READY -> fresh STATE only;
- client ERROR -> diagnostic only.

The reevaluation did not find a reason to revert this semantic model.

## M1F transport that still stands

Current source still implements:

- protocol v5 range request/data;
- server-selected `anchorOffset` + `anchorTime` in STATE;
- first demand waits for authoritative STATE;
- max current range payload 128 KiB;
- 512 KiB one-source encoded byte window;
- bounded per-player outstanding requests/bytes;
- bounded two-thread server range-IO pool and queue;
- in-flight MediaAsset retain for accepted reads;
- off-thread encoded file reads;
- current generation/asset/player/relevance recheck before send;
- arbitrary encoded request offsets;
- no modern client `.part/.media` complete-song path;
- old modern finite CHUNK/END packets removed;
- `audioPlayStaged()` removed;
- encoded availability distinguishes DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE.

The reevaluation did not find a reason to restore the old whole-file bridge.

## M1F acceptance coverage gap

The original M1F contract required deterministic proof for more than the current two M1F-specific unit-test classes provide.

Current tests strongly cover:

- range window bounds/arbitrary offset/re-anchor/retry;
- missing-vs-EOF distinction;
- exact range bytes;
- range service accounting/outstanding limits;
- in-flight asset retain across owner release.

Still missing as dedicated deterministic component tests:

- full server source/generation/asset validation;
- relevance/dimension gating;
- stale completion after replacement/leave/disconnect;
- shutdown ordering and retry behavior;
- packet codec/bounds integration;
- client BEGIN/STATE anchor gating;
- sliding consume/discard progression;
- authoritative server state-machine failure/rollback behavior.

Some of these are visible by source review, but source review is not the deterministic acceptance proof the original contract asked for.

## M1G status

M1G has **not started**.

Do not start it until the owner chooses how to handle the reopened M1E/M1F issues.

M1G still owns:

- progressive MP3 decode;
- common WAV layout/conversion;
- temporary-starvation vs true EOF at decoder boundary;
- MP3 Layer III pre-roll;
- bounded mono PCM;
- decoder cancellation;
- actual positional Minecraft/OpenAL rendering.

## M1H boundary remains separate

Full late-entry / leave-range cleanup / return-rejoin / dimension-reload lifecycle remains M1H.

M1F currently revalidates relevance before serving/sending ranges, but it does not proactively destroy out-of-range client sessions or discover late entrants. That remains intentional and is not being reclassified as an M1F regression.

## Decision required before Java changes

Read `M1E-M1F-REEVALUATION-2026-09-13.md` for the three reasonable paths:

1. close all reopened M1E/M1F issues before M1G;
2. fix shared correctness now and make buffer/test completion explicit M1G entry work;
3. documentation-only defer, leaving M1E/M1F provisional.

Do not choose on the owner's behalf.

## Current read order

1. `M1E-M1F-REEVALUATION-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`
3. `CURRENT-STATE.md`
4. `KNOWN-ISSUES.md`
5. `TESTING.md`
6. `VERIFIED-FACTS.md`
7. `M1E-SERVER-AUTHORITY.md`
8. `M1F-IMPLEMENTATION-2026-09-13.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `ROADMAP.md`
11. `LUA-API.md`
12. exact current source/CI

Older completion/finalization documents remain useful historical evidence, but their old status labels do not override the reevaluation.
