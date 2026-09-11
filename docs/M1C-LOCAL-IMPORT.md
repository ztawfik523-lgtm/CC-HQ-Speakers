# M1C — local ComputerCraft file import

## Scope

M1C connects ComputerCraft-visible local files to the reusable server media-asset layer built in M1B.

The important ownership change is:

```text
CC file
  -> temporary writable staging
  -> reusable server MediaAsset UUID
  -> prepared owner and/or playback references
```

The writable mount is now temporary import space. It is no longer the conceptual owner of the media file, and the finite playback sender no longer owns the mount.

M1C does **not** replace the prototype client transfer/rendering system. Prepared assets temporarily feed the existing begin/chunk/end sender until M1E/M1F replace client-authoritative playback and fixed-recipient server push.

## Lua surface

The bundled `hqspeaker` module keeps the simple convenience call:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

It now implements that as:

1. validate the CC-local file and its size;
2. `fs.copy` it into the speaker's temporary writable staging mount;
3. import the staged bytes into the server-wide media asset store;
4. start playback from the resulting asset UUID;
5. release the temporary prepared-owner reference.

The playback has already retained its own reference before `playFile` releases the preparation reference, so the encoded asset remains alive for the active playback.

Lua also gets lower-level preload/reuse helpers:

```lua
local asset = hq.prepareFile(speaker, "/music/song.mp3")

-- No playback has started yet.

assert(hq.playPrepared(speaker, asset, { volume = 0.6 }))

-- This releases only the preparation reference. An active playback owns its own reference.
hq.releasePrepared(speaker, asset)
```

The matching peripheral capabilities are:

- `audioPrepareStaged(path [, consume]) -> assetId`;
- `audioPlayPrepared(assetId [, volume]) -> boolean`;
- `audioReleasePrepared(assetId) -> boolean`;
- `audioMountPath()`;
- `audioMaxStagedBytes()`.

The historical `audioPlayStaged` method is temporarily retained for prototype compatibility but is no longer what `hqspeaker.playFile` uses.

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
- detaching the preparing computer releases the prepared references it still owns;
- removing/cleaning the speaker also releases prepared references through normal peripheral cleanup;
- the encoded asset itself lives in the server-wide M1B store and can be played by another speaker when Lua deliberately passes that asset UUID to it.

Prepared-reference release remains tied to the computer/speaker which prepared it. A random or foreign computer cannot call `audioReleasePrepared` and decrement another computer's preparation reference merely by knowing the UUID.

## Server-wide store lifetime

`ServerMediaAssets` owns one `MediaAssetStore` per running `MinecraftServer`.

The current M1C safety defaults are centralized there:

- maximum one asset: 512 MiB;
- maximum committed/reserved shared asset store: 2 GiB.

These are interim implementation safety defaults, not the final user-facing storage-policy decision. They can later be moved to configuration without changing the `MediaAssetStore` primitive.

The store directory is under the current world/server root at:

```text
hqspeaker/media-assets
```

On server shutdown, speaker/peripheral cleanup runs first so prepared/playback references are released, then the server media store closes and performs its final file cleanup.

## Transitional playback bridge

`HQFiniteMediaServer.playPrepared` currently bridges a prepared asset into the old staged finite sender:

- it validates the asset UUID;
- retains a playback reference;
- opens the shared `.media` file;
- uses the asset UUID as the session/media ID;
- transfers the encoded bytes with the existing prototype begin/chunk/end packets;
- releases the playback reference on stop, terminal end/error, exact-end seek, or transfer failure.

`audioStatus()` includes `assetId` for prepared-asset sessions.

This bridge intentionally does **not** make the old transport final architecture. It still has the prototype limitations:

- fixed recipients captured at playback start;
- server-pushed whole-file transfer;
- client READY/STARTED/ENDED affecting the canonical clock;
- renderer-observation timeout;
- no dynamic late join from server state.

M1E replaces playback authority; M1F replaces transfer.

## Threading

Large asset import is performed from the ComputerCraft call path, not from the Minecraft server tick. M1C therefore avoids copying hundreds of MiB on the server tick thread.

The old prototype sender still reads/sends chunks from its server tick. That is a known prototype behavior scheduled for replacement by bounded transfer workers in M1F.

## Runtime acceptance

The dedicated M1C script is:

```text
scripts/m1c_local_import_test.lua <path-to-mp3/ogg/wav>
```

It checks:

- prepare without immediate playback;
- releasing an unused prepared asset;
- a released asset cannot subsequently start;
- prepared playback identifies the same `assetId` in `audioStatus`;
- releasing the preparation reference does not stop an active playback;
- `hqspeaker.playFile` uses the prepared-asset path.

A green Java/CI build is not Minecraft runtime proof. Do not report this script as passed until it has actually been run in the target game stack.
