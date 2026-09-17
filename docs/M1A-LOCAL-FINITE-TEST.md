# M1A local finite-media runtime test — historical

> **Historical prototype-era acceptance plan. Do not use this as the current modern-prepared M1G test.**
>
> The transport described below used client cache files/chunk transfer and broad MP3/OGG/WAV prototype behavior which M1F/M1G replaced. Current acceptance lives in `TESTING.md` and `M1-RUNTIME-TEST.md`; current source/state lives in `CURRENT-STATE.md` and `KNOWN-ISSUES.md`.

This test validated the local-file path introduced after P0. It is intentionally preserved as evidence of the earlier staged/local-file prototype, not as present architecture.

## Historical path tested

At M1A the preferred finite-file prototype path was:

```text
ComputerCraft file
  -> fs.copy into the speaker's writable CC mount
  -> server-owned encoded file
  -> 256 KiB client-bound chunks
  -> encoded client cache file
  -> incremental decoder
  -> Minecraft streaming audio channel
```

That architecture is now superseded.

Current modern prepared playback instead uses:

```text
ComputerCraft file
-> temporary staging/import
-> reusable server MediaAsset
-> protocol v6 bounded client-requested encoded ranges
-> fixed-size sliding client encoded RAM
-> progressive MP3/common-WAV decode
-> bounded mono PCM
-> positional Minecraft AudioStream/SoundManager source
```

There is no modern complete client song cache file and current max range response is 128 KiB, not the historical 256 KiB chunk path.

## Historical fixtures

The original plan used:

1. short MP3;
2. OGG;
3. WAV;
4. MP3 or OGG larger than 8 MiB.

OGG no longer belongs to the modern prepared support surface. Current modern prepared formats are MP3 + supported common WAV. Use `TESTING.md` for current fixtures and gates.

## Historical script

The historical helper was:

`scripts/m1a_local_file_test.lua`

It exercised staging/client-observation behavior belonging to this prototype checkpoint. It is not a current M1G pass/fail gate.

`p0_cc_speaker_contract.lua` remains useful for standard CC:T singular-method compatibility, but it does not prove modern prepared playback.

## Historical checks

The original helper/manual plan covered:

- native `speaker.stop()` / `playNote` presence;
- staged local-file acceptance;
- observed client renderer;
- finite duration;
- pause/resume/seek/loop;
- exact-duration ending;
- `speakIsPlaying()` after terminal EOF;
- large-file behavior above the inherited 8 MiB byte-taking limit;
- F3+T, range leave/return, speaker replacement, and reconnect observations.

These are historical observations/plans only. Current M1G ownership is narrower in several areas: full late-entry/leave-return/resource-reload/general-underrun/final-VS2 lifecycle remains M1H.

## Current replacements for this test

Current M1G acceptance must use `hq.playFile()` / prepared MediaAssets and verify:

- MP3 + supported common WAV only;
- bounded range transport and bounded encoded/PCM memory;
- explicit decoder/reanchor revision after KI-053/KI-056/KI-057 are fixed;
- fixed 32-block modern-finite core radius with volume changing gain rather than range;
- global-volume-zero local hibernation while canonical server time advances;
- ordinary non-gapless replay after local EOF while authoritative looping remains enabled;
- rejected/failed replacement preserving valid current playback after KI-063 is fixed;
- no complete client song `.part/.media` file;
- standard CC:T behavior remaining intact.

See `TESTING.md` and `M1-RUNTIME-TEST.md` for the complete current matrix.

## Legacy boundary

Legacy byte-taking calls such as `speakMp3(bytes)` remain inherited compatibility surfaces and are not the recommended route for large ComputerCraft-local files. Multi-speaker synchronization remains later work.
