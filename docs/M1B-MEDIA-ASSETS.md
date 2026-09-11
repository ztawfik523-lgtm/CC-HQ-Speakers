# M1B — media asset storage foundation

## Scope

M1B begins separating finite encoded media from physical speakers.

The storage foundation on `codex/m1b-media-assets` introduces two pure Java types:

- `MediaAsset` — public UUID/size/source-name identity for encoded finite media;
- `MediaAssetStore` — disk/file lifetime owner for those assets.

This is deliberately below the speaker/playback layer. A media asset is not a speaker, playlist entry, renderer, or playback clock.

## Asset identity

Each import receives a generated UUID and a UUID-named encoded file:

```text
<uuid>.part   while the exact-size import is incomplete
<uuid>.media  after atomic completion
```

The original/source name is diagnostic metadata only and is never used to choose the filesystem path.

No speaker UUID is part of media identity. Future playbacks may therefore retain the same asset from several physical speakers without re-importing the encoded bytes.

## Atomic import

`MediaAssetStore.importAsset(...)` requires the expected encoded byte count.

Before copying it reserves that byte count against the total store limit. It then:

1. writes to a newly-created UUID `.part` file using a bounded 64 KiB copy buffer;
2. rejects an unexpectedly short source;
3. probes for an unexpectedly long source and rejects it;
4. forces the completed file to disk;
5. atomically renames `.part` to `.media` in the same directory;
6. publishes the `MediaAsset` only after the rename succeeds.

Failure before publication releases the reservation and removes the partial/completed file created by that failed attempt.

The import source channel remains caller-owned. `importFile(...)` is a convenience wrapper which owns/closes the file channel it opens itself.

## Quotas

The store constructor requires both:

- `maxAssetBytes` — maximum bytes in one encoded asset;
- `maxTotalBytes` — maximum bytes reserved/committed by the store.

M1B intentionally does **not** choose permanent mod-wide quota numbers. Storage policy is supplied by the future server integration layer instead of being hidden inside the asset primitive.

Quota is reserved before bytes are copied, so concurrent imports cannot each pass a pre-copy size check and collectively exceed the configured total.

Completed/reachable bytes and in-progress reserved bytes are accounted separately.

## Reference ownership

A successful import starts with one reference owned by the importer/prepared-asset owner.

`retain(assetId)` adds a reference for another owner/playback. `release(assetId)` removes one.

When the final reference is released:

- the `.media` file is deleted;
- the asset is removed from the store;
- committed-byte accounting is reduced.

If final file deletion fails, the bookkeeping/reference is retained and the error is surfaced. The store therefore does not claim quota was freed when the file still exists.

Callers opening an asset for reading must hold a reference for the lifetime of that read/playback.

## Crash/restart cleanup

M1B references are process-local. There is not yet a durable prepared-asset catalog which can restore external owners after a server restart.

Therefore a newly-created store prunes UUID-named `.part` and `.media` files left in its managed directory by an earlier process. Unrelated files are left untouched.

A root-level file lock prevents two live `MediaAssetStore` instances from sharing the same directory. This is important because startup orphan pruning would otherwise let a second store delete files still owned by the first.

Normal `close()` removes completed assets. If no import is active it also releases the root lock immediately. If an import is still active, shutdown marks the store closed but deliberately keeps the root lock until that import observes the closed store and removes its unpublished `.part`/`.media` file. Only then can a new store acquire the directory. A hard process crash is handled by next-start orphan pruning.

## Read path

`openRead(assetId)` opens a seekable encoded-file channel. This is the primitive later milestones can use for:

- server media analysis;
- bounded client range responses;
- seek/index construction.

It does not decode media and does not expose playback semantics.

## Automated coverage

`MediaAssetStoreTest` covers:

- successful exact-size import and encoded-byte readback;
- `.media` completion with no remaining `.part`;
- initial reference ownership;
- retain/release and final-reference file deletion;
- per-asset quota rejection;
- total quota rejection and quota reuse after release;
- concurrent reservation preventing quota overcommit;
- short-source rollback;
- long-source rollback;
- reservation cleanup after failed imports;
- startup pruning of managed crash leftovers while preserving unrelated files;
- rejection of a second live store on the same directory;
- shutdown during an active import while keeping the root locked until cleanup finishes;
- normal shutdown cleanup;
- empty-input rejection.

The project CI still runs the complete Java build/test/package matrix on NeoForge 21.1.247 and 21.1.248.

## Deliberately not implemented here

This storage foundation does **not** yet:

- replace the M1A staged finite prototype;
- expose prepare/play/release Lua methods;
- choose final server storage quota values;
- analyze MP3/OGG/WAV duration or media facts;
- own a finite playback clock;
- transfer asset bytes to clients;
- provide a persistent asset catalog across server restarts;
- decode audio;
- implement multispeaker playback.

Those belong to M1C and later milestones in `ROADMAP.md`.
