# Server configuration

Updated: 2026-09-28

HQ Speaker uses NeoForge's per-world **SERVER** config. Storage limits and audio tuning live in the same existing config file.

## Media storage limits

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

These two storage settings require a world/server restart.

`maxAssetMiB` is the maximum encoded size of one staged/prepared asset. `0` disables the HQ-specific per-asset quota.

`maxTotalMiB` limits total encoded bytes retained by the media asset store. `0` disables the HQ-specific total-store size limit.

They do not alter ComputerCraft's own filesystem quotas.

## Audio tuning

Protocol v11 resolves HQ logical volume on the server into independent source gain and audible range.

Default tuning:

```toml
[audio]
defaultVolume = 1.5
maxVolume = 3.0
allowRangeOverride = true
maxRange = 256.0

[audio.gain]
at0 = 0.0
at0_5 = 0.17
at1 = 0.34
at1_5 = 0.50
at2 = 0.67
at2_5 = 0.84
at3 = 1.0

[audio.range]
at0 = 0.0
at0_5 = 12.0
at1 = 29.0
at1_5 = 48.0
at2 = 70.0
at2_5 = 96.0
at3 = 132.0
```

The logical-volume anchor positions are fixed at `0, 0.5, 1, 1.5, 2, 2.5, 3`. Output values between neighboring anchors are linearly interpolated.

`1.5` is the normal reference volume. `3` is the normal maximum. The default explicit-range server ceiling is 256 blocks.

HQ Lua calls reject non-finite values and values outside the server's allowed volume/range limits with a Lua error. They are not silently clamped.

An explicit range is measured in blocks and overrides only the automatic volume-derived range. Gain still comes from the gain curve.

### Reload behavior

Audio tuning does **not** require a server restart.

A continuous HQ source snapshots the current audio profile when that source starts:

- an already-running finite/RAW/radio source keeps its original profile;
- the next new finite/RAW/radio source uses the reloaded profile;
- continuous RAW keeps one profile across all chunks until that RAW source ends or is stopped.

There is no per-computer or per-script config snapshot.

## Finite transport tuning

Current protocol: **v11**, still exactly 9 payload types.

Current implementation bounds include:

- maximum finite encoded-range response: 128 KiB;
- client encoded sliding window: 512 KiB;
- maximum outstanding finite range requests per player: 4;
- maximum outstanding encoded bytes per player: 512 KiB;
- server range IO workers: 2;
- server range IO queue: 64.

These are transport implementation values, not public audio-range settings.

## Audible range

HQ audible range is no longer a fixed 32-block server radius.

The server uses each source's resolved audible range for finite listener membership and RAW/radio delivery. The client applies the same resolved value as Minecraft/OpenAL linear attenuation distance, so `range` is the fade-to-zero distance in blocks.
