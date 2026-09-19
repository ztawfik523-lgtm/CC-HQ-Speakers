# Server configuration

Updated: 2026-09-20

HQ Speaker does not control ComputerCraft filesystem quotas. These settings limit only HQ Speaker's own server-side staging/prepared-media storage.

## Media storage limits

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

Both require server/world restart.

`maxAssetMiB` is the maximum encoded size of one staged/prepared asset. `0` disables the HQ-specific per-asset quota.

`maxTotalMiB` limits total encoded bytes retained by the media asset store. `0` disables the HQ-specific total-store quota.

`0` does not bypass filesystem/ComputerCraft limits.

## Modern finite transport tuning

Current protocol: **v9**.

Current bounds:

- maximum range response: 128 KiB;
- client encoded sliding window: 512 KiB;
- maximum outstanding range requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are implementation values, not public configurable guarantees.

## Core range

Modern finite uses a fixed 32-block core server relevance/delivery radius.

Finite volume changes gain, not that core radius.

The 32-block value is not currently configurable.

## Shutdown/import hardening

Resolved implementation behavior includes retryable/root-lock-safe media shutdown cleanup, bounded import no-progress handling, same-root non-atomic rename fallback, and owner staging cleanup.
