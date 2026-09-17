# M1B — media asset storage foundation

> **Historical M1B milestone record.** The core MediaAsset/MediaAssetStore design remains active, but this file describes the M1B checkpoint rather than all later hardening findings. Current storage issues KI-054, KI-061, and KI-064 are tracked in `KNOWN-ISSUES.md` / `VERIFIED-FACTS.md`.
>
> In particular, do not read the shutdown/import sections below as proof that every later failure path is resolved: current source still has a same-JVM root-lock shutdown failure path (KI-054), repeated-zero-read no-progress risk, and no unsupported-`ATOMIC_MOVE` fallback (KI-064).

## Scope

M1B began separating finite encoded media from physical speakers.

The storage foundation introduced:

- `MediaAsset` — UUID/size/source-name identity for encoded finite media;
- `MediaAssetStore` — disk/file lifetime owner for those assets.

This remains deliberately below the speaker/playback layer. A media asset is not a speaker, playlist entry, renderer, or playback clock.

## Asset identity

Each import receives a generated UUID and a UUID-named encoded file:

```text
<uuid>.part   while the exact-size import is incomplete
<uuid>.media  after completion
```

The original/source name is diagnostic metadata only and is never used to choose the filesystem path.

No speaker UUID is part of media identity. Playbacks may retain the same asset from several physical speakers without re-importing encoded bytes.

## Import model

`MediaAssetStore.importAsset(...)` requires the expected encoded byte count.

The M1B design:

1. reserves the byte count against total quota;
2. writes to a newly-created UUID `.part` file using a bounded 64 KiB buffer;
3. rejects unexpectedly short input;
4. probes for unexpectedly long input and rejects it;
5. forces the completed file to disk;
6. renames `.part` to `.media`;
7. publishes the `MediaAsset` only after completion.

Failure before publication releases the reservation and attempts to remove unpublished files.

The source channel remains caller-owned. `importFile(...)` is a convenience wrapper which owns/closes the channel it opens.

### Later import hardening finding — KI-064

Current source still retries a zero-byte `ReadableByteChannel.read(...)` indefinitely using `Thread.onSpinWait()` and has no bounded no-progress policy. It also currently calls `Files.move(..., ATOMIC_MOVE)` without falling back when atomic move is unsupported.

Those are later hardening gaps; they do not invalidate the M1B ownership model but must be fixed/tested before storage is called fully hardened.

## Quotas

The store receives:

- `maxAssetBytes` — maximum bytes in one encoded asset;
- `maxTotalBytes` — maximum bytes reserved/committed by the store.

Storage policy is supplied by server integration rather than hidden inside the asset primitive.

Quota is reserved before copy so concurrent imports cannot overcommit the configured total. Completed/reachable bytes and in-progress reserved bytes are accounted separately.

Current default server policy is documented in `SERVER-CONFIG.md`.

## Reference ownership

A successful import starts with one owner reference.

`retain(assetId)` adds a reference for another owner/playback. `release(assetId)` removes one.

When the final reference is released:

- the `.media` file is deleted;
- the asset is removed from the store;
- committed-byte accounting is reduced.

If final normal-path deletion fails, current source keeps bookkeeping/reference alive and surfaces the error so quota is not falsely freed.

Callers opening an asset for reading must hold a reference for the lifetime of that read/playback.

## Crash/restart cleanup

References are process-local; there is no durable prepared-asset catalog restoring owners after restart.

A newly-created store prunes managed UUID-named `.part` and `.media` leftovers from an earlier process while leaving unrelated files alone.

A root-level file lock prevents two live stores from sharing the same managed directory.

The intended shutdown model is to stop/drain range IO, close/delete active store state, and release the root lock, with active imports keeping the lock only until they observe closure and remove unpublished files.

### Later shutdown hardening finding — KI-054

Current shutdown has two important failure gaps not known/finalized at M1B:

- `MediaAssetStore.close()` clears completed-entry bookkeeping before deletion attempts, so failed shutdown deletion cannot be retried from retained entry state;
- `ServerMediaAssets.closeServer()` calls `FiniteRangeReadService.close()` before `MediaAssetStore.close()`. If range close throws, store close and registry removal are skipped, so the root lock can remain held in the same JVM and block a later integrated-server store on the same root.

Next-start orphan pruning does not solve a lock still held by the old JVM object.

## Read path

`openRead(assetId)` opens a seekable encoded-file channel. Modern later milestones use this for analysis and bounded client range reads. It does not decode media or own playback semantics.

## Automated coverage from the M1B milestone

`MediaAssetStoreTest` covered the milestone behaviors including exact-size import/readback, completion/rollback, reference lifetime, quotas/reservations, orphan pruning, second-store exclusion, shutdown during active import, normal shutdown cleanup, and empty-input rejection.

Later KI-054/KI-064 require additional failure-injection/no-progress/fallback coverage beyond the original M1B matrix.

## Historical M1B non-goals

At M1B this storage foundation did not yet:

- replace the staged finite prototype;
- expose final prepare/play/release Lua flow;
- own final media analysis/playback state;
- transfer bounded ranges to clients;
- decode audio;
- implement multispeaker playback.

Those later milestones have since progressed substantially. Current architecture/state is in `CURRENT-STATE.md` and `ARCHITECTURE.md`.
