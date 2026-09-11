# Server configuration

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

## Transport limits

The old staged-finite prototype used a hard-coded 512 MiB check in its begin packet. That policy check has been removed. The packet carries encoded size as a variable-length long and transfers data in bounded chunks; file-size policy belongs to the server configuration/store, not the wire packet.
