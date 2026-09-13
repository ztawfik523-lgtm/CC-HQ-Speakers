# M1E + M1F reevaluation — 2026-09-13

This document supersedes earlier **status labels** which called M1E finalized and M1F source/test/CI complete. It does not erase earlier commits, green CI, or runtime diagnostics.

No Java/source change was made during this reevaluation. Current implementation code remains:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

The documentation head before this reevaluation was:

`d748287fe2a5bfa38fe5a3f1bad2a864fc7fc591`

## Bottom line

The main architecture still holds:

```text
M1E: server owns finite playback truth
    -> M1F: client requests bounded encoded ranges from server assets
    -> M1G: decoder consumes those ranges into bounded PCM/rendering
```

The reevaluation did **not** find a reason to revert server authority, immutable MediaAssets, client-pulled range transport, protocol-v5 range identity, or the M1G milestone split.

It did find that the previous completion wording was too strong. There are shared M1E/M1F exception/lifetime correctness issues, a related asset-owner cleanup pattern in the supporting staging layer, missing M1F acceptance coverage, and an incomplete progressive-window consumer boundary.

Current status:

```text
M1E semantic design: implemented and still sound by source review
M1E implementation hardening/acceptance: reopened
M1E final manual Minecraft PASS: still skipped / not recorded

M1F range transport architecture: implemented and CI-green
M1F acceptance/completeness: reopened / provisional
M1F Minecraft runtime acceptance: not recorded

M1G: not started
```

Do not call either M1E or M1F fully accepted at this checkpoint.

## M1E reevaluation

### What still looks correct

Current `HQFiniteMediaServer` still preserves the M1E semantic model:

- successful prepared playback creates a new generation and starts the server clock immediately;
- no client READY/renderer handshake is required to start canonical time;
- canonical states remain PLAYING / PAUSED / ENDED / ERROR;
- pause/resume/seek/loop/volume mutate server-owned state;
- non-looping natural EOF is server-duration-driven;
- non-looping seek(duration) ends;
- looping seek(duration) wraps to zero;
- client READY requests fresh server state only;
- client ERROR is diagnostic only.

`FinitePlaybackClockTest` covers immediate progression, pause/resume, loop rebasing, exact-end seek behavior, and natural EOF math.

The 2026-09-12 runtime diagnostic still supports authority separation, but the strengthened final M1E Minecraft script was never run to PASS by explicit owner decision.

### Reopened correctness issue A — network sends are not isolated from canonical truth

`HQFiniteMediaServer.sendToRelevant()` calls `HQSpeakerNetwork.sendToPlayer()` directly and does not catch a per-player runtime send failure.

Several authoritative transitions call network send helpers inline:

- prepared start sends BEGIN/STATE;
- pause/resume/seek/volume/loop send control/state;
- stop sends STOP;
- natural/error notifications send STATE.

The M1E design says a client/render path must not own canonical server truth. The successful path follows that rule, but exception handling does not fully enforce it: a runtime packet-send exception can escape an authoritative operation.

This is a source-level robustness defect even though no such packet-send failure has been recorded in runtime evidence.

### Reopened correctness issue B — `playPrepared()` rollback can leave a ghost session

Current order is effectively:

```text
retain playback asset
-> create Session
-> assign session = next
-> send BEGIN/STATE
-> publish Lua event
```

The surrounding `catch (RuntimeException)` releases the retained asset, but it does not clear the already-installed session or mark its ownership flag false.

If a runtime exception occurs after `session = next`, `playPrepared()` throws while the finite server may still contain a PLAYING session which believes it owns a playback asset reference that the catch already released.

At the composite layer, `audioPlayPrepared()` only sets the STAGED_FINITE owner after a normal `true` return. A thrown start can therefore leave finite state installed while composite ownership remains unset: a ghost-session possibility.

This is not known to have occurred in Minecraft, but the source failure path is internally inconsistent.

### Reopened lifetime issue C — playback release forgets ownership before release succeeds

`releaseAssetReference()` currently sets `assetReferenceHeld = false` before calling `MediaAssetStore.release()`.

`MediaAssetStore.release()` intentionally keeps the entry/reference alive if final file deletion fails. If that IOException occurs, the finite session has already forgotten that it still owns the reference and has no retry path.

Result: the MediaAsset/quota can remain retained until later store shutdown even though the session believes it released it.

The ownership flag should represent a successfully released reference, not merely an attempted release.

### Related supporting-foundation pattern — detached prepared-owner release can also be forgotten

`HQMediaStaging.detach()` removes the computer's prepared-ownership set and then `releaseDetached()` attempts to release each asset. If a final release throws, the code logs the failure but no longer has an attached/prepared owner through which to retry it.

A similar branch exists if a computer detaches while `prepareAsset()` is finishing and the cleanup release fails.

This predates M1E/M1F, so it is not being mislabeled as an M1F regression. It is the same underlying lifetime rule: a failed `MediaAssetStore.release()` may leave a real reference behind, so callers need a retry-safe owner/cleanup strategy.

### Related M1F in-flight release edge

`FiniteRangeReadService.RangeTask` reports a release error and clears request accounting if its final `MediaAssetStore.release()` fails, but the task then exits with no surviving owner to retry a reference which the store deliberately kept.

Under the intended normal path a live playback usually owns another reference, so this edge is rare; it is still a lifetime-hardening gap when the in-flight retain becomes the final reference.

## M1E evidence reevaluation

The previous phrase `M1E source/tests/CI finalized` should be read more narrowly.

Actually proven:

- current server-authority source implements the intended normal-path semantics;
- deterministic `FinitePlaybackClockTest` covers clock math;
- CI/package verification passed on both NeoForge targets;
- historical runtime diagnostics support authority separation.

Not deterministically proven:

- the complete `HQFiniteMediaServer` state machine;
- start rollback/failure atomicity;
- packet-send failure isolation;
- playback-reference retry behavior after release failure.

And the final focused Minecraft M1E acceptance PASS remains unrecorded.

## M1F reevaluation

### What still looks correct

The M1F transport direction remains valid:

- protocol v5 range request/data identity includes source + generation + asset + offset;
- response size is bounded;
- server read work is bounded and off-thread;
- accepted reads retain the asset while queued/running;
- per-player outstanding requests/bytes are bounded;
- modern client `.part/.media` whole-song files are gone;
- old modern finite CHUNK/END packets are gone;
- direct prototype `audioPlayStaged()` is gone;
- client demand waits for authoritative STATE before the first request;
- server rechecks generation/asset/player relevance before sending completed range data;
- client encoded availability distinguishes missing data from physical EOF;
- arbitrary encoded re-anchors are possible.

These are meaningful improvements and should not be reverted.

### Reopened completeness issue D — encoded window is re-anchorable, not truly sliding/consumable

The original pre-M1F contract required a fake/test consumer to prove:

```text
request non-zero data
-> verify bytes
-> consume/discard it
-> jump far away
-> obsolete bytes gone
-> memory stays bounded
```

Current `FiniteRangeWindow` provides `reset(anchorOffset)`, `probe()`, `copy()`, request/accept/retry, and cancel. It does **not** provide a consume/discard/advance operation which slides the live window forward while preserving useful unread prefetched bytes.

M1G could technically wait until a full 512 KiB window has been consumed and then call `reset(windowEnd)`. That can progress without unbounded memory, but it creates hard refill boundaries. Re-windowing earlier throws away unread prefetched bytes and requires re-requesting them.

So the protocol is usable, but the M1F/M1G client-buffer API is not yet the clean progressive sliding boundary originally specified.

This should be closed before or as an explicit first part of M1G. It does **not** require a range-protocol redesign.

### Reopened acceptance issue E — deterministic tests cover only a subset of the original matrix

Current M1F-specific deterministic tests are:

- `FiniteRangeWindowTest`;
- `FiniteRangeReadServiceTest`.

They prove useful pieces: arbitrary offsets, exact bytes, bounded window allocation, re-anchor/stale response behavior, missing-vs-EOF, retry, read accounting, in-flight retain, invalid bounds, and outstanding limits.

The original M1F acceptance contract also explicitly required deterministic proof for at least:

- source/generation/asset validation;
- relevance/dimension checks;
- stale async completion after replacement/leave/disconnect;
- cancellation/ref/account cleanup;
- shutdown ordering with background reads;
- no game-tick file reads;
- bounded packet sizing;
- consume/discard behavior.

Those are currently supported partly by source review, not by the promised deterministic test coverage. There is also no dedicated deterministic `HQFiniteMediaClient` session/anchor-gating test or `HQFiniteMediaServer` component test in the current test tree.

Therefore `M1F source/test/CI complete` was too broad. A more accurate label is:

**M1F implementation candidate, CI-green on both targets, with acceptance/hardening reopened.**

## M1F runtime evidence

No focused real Minecraft M1F transport acceptance has been recorded.

CI proves compilation/tests/package shape; it does not prove:

- live client/server packet ordering;
- actual range traffic under Minecraft networking;
- no client song files in a real game directory;
- seek-triggered non-zero live demand;
- disconnect/leave/replacement stale completion behavior;
- shutdown interaction with real server lifecycle.

That runtime gap remains separate from the source findings above.

## What is intentionally not being reclassified as an M1F bug

Full late-listener discovery and leave/re-enter recovery remain M1H.

Current M1F does revalidate relevance before serving/sending ranges. It does not proactively destroy out-of-range client sessions or discover late entrants. That split was already intentional.

Likewise, M1F is still allowed to be silent. MP3/WAV decoding, PCM queues, pre-roll, and positional rendering remain M1G.

## Decision required before Java changes

The owner previously requested that substantive implementation findings be raised before code is changed. No Java was changed during this reevaluation.

Reasonable paths are:

1. **Close all reopened M1E/M1F issues before M1G.** Fix send/rollback/release exception safety, choose a retry-safe strategy for final asset releases (including detached/in-flight cleanup), add a real sliding/consume window operation, and fill the missing deterministic acceptance coverage. Cleanest milestone boundary, largest immediate hardening scope.

2. **Fix shared correctness first, move buffer/test completion into the opening M1G work.** Fix ghost-session/send isolation/reference-release ownership now; keep M1F provisional and make sliding-window + missing acceptance tests explicit M1G entry criteria. Smaller immediate patch, but M1F remains intentionally not accepted.

3. **Documentation-only defer.** Keep current Java unchanged, mark M1E/M1F provisional, and proceed later with known risks. Smallest immediate change, but M1G would build on known exception/lifetime gaps and a less-clean encoded-window boundary.

Do not silently choose one of these paths without owner direction.

## Current sequencing rule

Until that choice is made:

- do not call M1E fully finalized;
- do not call M1F source/test/CI complete;
- do not start M1G;
- preserve protocol v5 and the current range architecture unless new evidence requires otherwise;
- treat earlier M1E/M1F completion documents as historical checkpoint records whose status wording is superseded by this reevaluation.
