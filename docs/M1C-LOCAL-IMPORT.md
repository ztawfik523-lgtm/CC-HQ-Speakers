# M1C — local ComputerCraft file import

> **Historical milestone record with still-active import/ownership concepts.** M1C established the staging -> immutable server MediaAsset flow, but several “current checkpoint”/transitional statements from 2026-09-13 are now obsolete. Current user-facing API is `LUA-API.md`; current implementation/state is `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, and exact source.

## Scope

M1C connected ComputerCraft-visible local files to the reusable server media-asset layer built in M1B.

The ownership model remains:

```text
CC file
  -> temporary writable staging
  -> reusable server MediaAsset UUID
  -> prepared owner and/or playback references
```

The writable mount is temporary import space. It is not the media library and is not a client playback cache.

## Recommended Lua surface

The bundled `hqspeaker` module keeps the simple convenience call:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Conceptually it:

1. validates the ComputerCraft-local file/size;
2. copies into the temporary writable HQ staging mount;
3. imports exact staged bytes into the server media-asset store;
4. analyzes the committed immutable asset;
5. starts playback from the resulting asset UUID;
6. releases the temporary prepared-owner reference.

Successful playback retains its own reference before the preparation reference is released, so the encoded asset remains alive for playback.

Reusable helpers remain:

- `hq.prepareFile(speaker, path)`
- `hq.preparedInfo(speaker, assetId)`
- `hq.playPrepared(speaker, assetId [, options])`
- `hq.releasePrepared(speaker, assetId)`
- `hq.playFile(speaker, path [, options])`

Low-level import/prepared capabilities remain documented in `LUA-API.md`.

## `audioPlayStaged()` — removal now implemented

`audioPlayStaged()` was this project's prototype direct-staging route, not original HQ Speakers compatibility.

The 2026-09-13 decision was to remove it when M1F replaced the direct staged/whole-file transport. That removal is now implemented.

Current programs use `hq.playFile()` or prepare -> play -> release. Do not restore a second direct-staging playback transport.

## Staging cleanup / current KI-061

High-level successful `hq.playFile()` normally consumes/removes its temporary staging copy.

However, the per-speaker writable mount is persistent under a random `hqspeaker/staging/<uuid>` path. Whole staging-owner cleanup currently unmounts/releases prepared references but does **not** remove arbitrary files still present in the mount.

Therefore low-level/interrupted staging can leave files unreachable after the speaker/staging owner is destroyed and recreated. This is KI-061.

Target fix:

- clear leftover mount contents on **whole staging-owner cleanup** after attached computers are unmounted;
- do not clear the shared mount on one computer's normal detach;
- surface/log cleanup failure without corrupting valid prepared MediaAsset ownership.

## Asset ownership

A successful prepare creates a prepared-owner reference associated with the ComputerCraft computer which prepared it.

Playback takes a separate reference.

Consequences remain:

- releasing preparation ownership while playing does not delete the active asset;
- releasing an unused final preparation reference removes the asset when deletion succeeds;
- detaching the preparing computer releases its prepared references;
- whole speaker cleanup releases prepared references;
- the encoded asset lives in the server-wide store and can be deliberately played from another speaker by UUID;
- another computer cannot release someone else's preparation ownership merely by knowing the UUID.

## Server-wide store lifetime

`ServerMediaAssets` owns one `MediaAssetStore` per running Minecraft server under:

```text
hqspeaker/media-assets
```

The M1B/M1C server-side immutable asset model remains active in M1G.

Current storage hardening issues are tracked separately:

- KI-054 — shutdown deletion retry/root-lock lifetime;
- KI-064 — repeated-zero-read no-progress and unsupported-atomic-move fallback.

## Transitional playback bridge — superseded

At the original M1C checkpoint, prepared playback still fed the old whole-file client sender beneath newer server-authority semantics.

That statement is historical.

M1F replaced transfer with bounded demand-driven range request/data. Modern prepared playback no longer pushes a complete encoded song to a client or creates a modern complete client cache file.

M1G additionally integrates progressive MP3/common-WAV decoding, bounded PCM, and positional rendering.

## Current format/playback boundary

Modern prepared/local support is MP3 + supported common WAV. Historical analyzer/legacy OGG/AIFF/AU surfaces do not define modern support.

Selected current M1G semantics also include:

- explicit decoder/re-anchor revision as the next protocol design;
- fixed 32-block core modern-finite range, volume as gain rather than radius;
- global-volume-zero local hibernation while canonical server time continues;
- ordinary non-gapless loop replay after local EOF.

Those decisions did not exist at the original M1C checkpoint; current docs override historical assumptions.

## Evidence boundary

The M1C milestone remains useful evidence for local-file import and ownership semantics. It is not evidence that current M1G audible playback, staging cleanup, shutdown hardening, or replacement-admission bugs are resolved.

Use `TESTING.md` and `M1-RUNTIME-TEST.md` for present acceptance work.
