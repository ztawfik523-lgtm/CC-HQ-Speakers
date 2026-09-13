# M1C — local ComputerCraft file import

## Scope

M1C connects ComputerCraft-visible local files to the reusable server media-asset layer built in M1B.

The ownership model is:

```text
CC file
  -> temporary writable staging
  -> reusable server MediaAsset UUID
  -> prepared owner and/or playback references
```

The writable mount is temporary import space. It is not the media library and is not the final client playback store.

For the current user-facing programming reference, read `LUA-API.md`.

## Recommended Lua surface

The bundled `hqspeaker` module keeps the simple convenience call:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

It implements that as:

1. validate the ComputerCraft-local file and size;
2. copy it into the temporary writable HQ staging mount;
3. import the staged bytes into the server-wide media asset store;
4. analyze the committed immutable asset;
5. start playback from the resulting asset UUID;
6. release the temporary prepared-owner reference.

The playback has already retained its own reference before `playFile` releases the preparation reference, so the encoded asset remains alive for the active playback.

Lua also gets lower-level preload/reuse helpers:

```lua
local asset = hq.prepareFile(speaker, "/music/song.mp3")
local info = hq.preparedInfo(speaker, asset)

assert(hq.playPrepared(speaker, asset, { volume = 0.6 }))

-- Releases preparation ownership only. Active playback has its own reference.
assert(hq.releasePrepared(speaker, asset))
```

Recommended helper functions:

- `hq.prepareFile(speaker, path)`
- `hq.preparedInfo(speaker, assetId)`
- `hq.playPrepared(speaker, assetId [, options])`
- `hq.releasePrepared(speaker, assetId)`
- `hq.playFile(speaker, path [, options])`

The matching low-level peripheral capabilities are documented in `LUA-API.md` and currently include:

- `audioMountPath()`
- `audioMaxStagedBytes()`
- `audioPrepareStaged(path [, consume])`
- `audioPreparedInfo(assetId)`
- `audioPlayPrepared(assetId [, volume])`
- `audioReleasePrepared(assetId)`

Most programs should use the module helpers instead of manually managing staging.

## `audioPlayStaged()` provenance and removal decision

The historical `audioPlayStaged()` direct-play command is **not** an original HQ Speakers compatibility API.

It was introduced by this project during the earlier staged/local-file prototype so a file sitting in the temporary writable mount could be played directly.

That design has been superseded by the reusable asset flow:

```text
staging/import
    -> immutable server MediaAsset
    -> prepared playback
```

Project decision on 2026-09-13:

**remove `audioPlayStaged()` when M1F implementation begins.**

Do not carry a second direct-staging playback transport into M1F. New programs use `hq.playFile()` or prepare/play/release.

At the current documentation checkpoint the command still exists in source; its removal has not been implemented yet.

## Staging cleanup

The Lua helper checks file size before copying.

`fs.copy` is wrapped so a failed copy attempts to delete any partial destination it created.

After a successful asset import, staging deletion is best-effort. Failure to remove temporary staging must **not** invalidate an already-valid shared asset. The Java staging layer logs a failed deletion and the Lua helper retries deletion from the mounted filesystem.

## Asset ownership

A successful prepare creates one prepared-owner reference associated with the ComputerCraft computer which prepared it through that speaker.

A playback takes a separate reference before playback starts.

Consequences:

- releasing the prepared reference while the asset is playing does not delete the encoded file;
- releasing an unused prepared asset removes it when that was its final reference;
- detaching the preparing computer releases prepared references it still owns;
- removing/cleaning the speaker also releases prepared references through normal peripheral cleanup;
- the encoded asset lives in the server-wide M1B store and can be deliberately played by another speaker when Lua passes that asset UUID to it.

Prepared-reference release remains tied to the computer which prepared it. Knowing the UUID alone does not let another computer release someone else's preparation reference.

## Server-wide store lifetime

`ServerMediaAssets` owns one `MediaAssetStore` per running Minecraft server.

The store directory is under the current world/server root at:

```text
hqspeaker/media-assets
```

M1C established disk-backed immutable assets, size/quota policy, retain/release ownership, and seekable reads.

The later M1F transport must keep this server-side media ownership model while changing how relevant clients obtain encoded bytes.

## Transitional playback bridge

Current prepared playback still feeds the old whole-file sender underneath the modern server-authority semantics:

- prepared asset is validated;
- playback retains a separate reference;
- the server opens the shared encoded file;
- playback uses the asset UUID as media identity;
- the old bridge pushes the encoded file to the client;
- playback releases its reference on stop/end/error.

This bridge is not final architecture.

M1E replaced playback authority. M1F replaces transfer.

## Current checkpoint note

M1E source/tests/CI are finalized, but the project owner skipped the final manual Minecraft M1E test. Do not call M1E runtime-verified.

M1F implementation has not started. This document update is documentation-only.