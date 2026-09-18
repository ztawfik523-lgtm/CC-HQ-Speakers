# Server configuration

Updated: 2026-09-17

HQ Speaker does **not** decide how much storage a ComputerCraft computer has. ComputerCraft disks, mounts, and filesystem quotas remain ComputerCraft/server policy.

The settings here only limit disk space allocated by **HQ Speaker itself** for temporary staging and prepared encoded media under the Minecraft world/server files.

## Media storage safety limits

NeoForge `SERVER` config section:

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

Both values require a world/server restart before they take effect.

### `maxAssetMiB`

Safe default: **512 MiB**.

This is the maximum encoded size of one file that HQ Speaker will copy into its staging/prepared-media storage. The same policy value is used for the temporary ComputerCraft-visible staging mount so there is not a second hidden per-file limit.

Set it to `0` to disable HQ Speaker's own per-asset size limit.

This setting does **not** limit the source ComputerCraft filesystem.

### `maxTotalMiB`

Safe default: **2048 MiB (2 GiB)**.

This limits the total encoded bytes held by HQ Speaker's server-side prepared-media asset store. It protects the Minecraft server's disk from scripts which prepare many assets and keep references to all of them.

Unused assets are normally deleted when their final prepared/playback reference is released.

Set it to `0` to disable HQ Speaker's own total-store quota.

## What `0` means

`0` means “HQ Speaker does not impose this quota.” It does not make storage infinite and it does not change ComputerCraft or operating-system limits. The underlying filesystem still determines what can actually be written.

For the ComputerCraft-visible staging mount, HQ Speaker translates the unlimited sentinel to the largest capacity that CC:T's `WritableFileMount` can represent safely. CC:T internally adds its `MINIMUM_FILE_SIZE` accounting overhead to the supplied capacity, so passing `Long.MAX_VALUE` directly would overflow. This clamp is only arithmetic protection and is effectively unlimited for real storage; it does not introduce a practical hidden quota.

## Modern finite transport limits

Current implementation uses protocol **v6** with client-requested bounded encoded ranges. A selected future decoder/reanchor revision (likely v7) does not change the storage quota model described here.

Current implementation tuning is:

- maximum range response: **128 KiB**;
- client encoded sliding window: **512 KiB**;
- maximum outstanding range requests per player: **4**;
- maximum outstanding encoded bytes per player: **512 KiB**;
- server range IO workers: **2**;
- server range IO queue: **64**.

These are implementation safety/tuning values, not public API guarantees and not currently exposed as server config options.

Modern prepared playback does not push the complete file to a client. Encoded file size policy belongs to the server configuration/store; the wire carries total encoded size while actual transfer remains bounded by range requests/responses.

## Core listening/delivery radius

Modern finite M1G currently uses a **fixed 32-block core radius** for server relevance/delivery.

This is an owner-selected product rule for M1G:

- HQ finite volume changes gain/loudness, not the core radius;
- volume above 1 does not intentionally enlarge modern-finite server relevance;
- no dynamic volume-aware relevance config is being added in M1G;
- future Sound Physics Remastered compatibility owns intentional acoustic/range extension and matching transport relevance.

The 32-block value is currently an implementation constant, **not** a server config option. Do not document a range config until one actually exists.

If later SPR compatibility needs configurable delivery headroom/caps, design that with the acoustic integration rather than prebuilding a generic range knob now.

## Current storage hardening caveats

These are implementation issues, not configuration options:

- KI-054 and KI-064 were resolved by the post-M1G hardening pass. Shutdown cleanup is retryable/root-lock safe, import no-progress is bounded, and unsupported atomic rename has a same-root fallback.

KI-061 was resolved in M1G: whole-owner staging cleanup removes persistent leftovers after unmount/release. Quota settings remain independent of those lifecycle guarantees.
