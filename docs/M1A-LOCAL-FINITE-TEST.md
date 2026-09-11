# M1A local finite-media runtime test

This test validates the local-file path introduced after P0. It is intentionally focused on finite media already present in the ComputerCraft filesystem. Internet MP3/HLS/TS streaming is not part of this acceptance pass.

## What changed

The preferred finite-file path is now:

```text
ComputerCraft file
  -> fs.copy into the speaker's writable CC mount
  -> server-owned encoded file
  -> 256 KiB client-bound chunks
  -> encoded client cache file
  -> incremental decoder
  -> Minecraft streaming audio channel
```

The server never sends the whole encoded file in one HQ finite-media payload, and the new client path never expands the whole finite file into one decoded PCM byte array.

The staged-media ceiling is currently 512 MiB. This is a storage/safety policy, not a single-packet or decoded-RAM architectural ceiling.

## Before running Minecraft

Both CI matrix entries must pass:

- NeoForge 21.1.247
- NeoForge 21.1.248

The pure Java tests for `FinitePlaybackClock` and `FiniteMediaPath` must also pass.

## Files to test

Use at least:

1. a normal short MP3;
2. a normal OGG;
3. a normal WAV;
4. an MP3 or OGG **larger than 8 MiB**.

A larger-than-8-MiB file is required to prove the local-file path no longer depends on the inherited `SPEAKER_MAX_AUDIO` whole-packet limit.

## Run

The module is bundled in the mod's ComputerCraft ROM resources, so the test program uses:

```lua
local hq = require("hqspeaker")
```

Copy `scripts/m1a_local_file_test.lua` to a computer, then run:

```text
m1a_local_file_test /path/to/file.mp3
```

Run it once for each fixture above.

The helper verifies:

- standard `speaker.stop()` exists;
- standard `playNote("harp")` accepts omitted optional arguments;
- local file staging is accepted;
- the client renderer is actually observed;
- duration is finite and positive;
- pause freezes position;
- resume works;
- seek works;
- loop wraps;
- disabling loop after a wrap does not jump to EOF;
- seeking exactly to duration produces `ended` instead of renderer-start failure;
- `speakIsPlaying()` is false after terminal EOF.

Also run `scripts/p0_cc_speaker_contract.lua`. That specifically checks the standard CC:T speaker surface and `speaker_audio_empty` backpressure behavior.

## Manual observations

While the large file is transferring/playing:

- watch client and server memory; there should be no whole-track decoded-PCM-sized jump;
- verify the file starts only after the encoded transfer completes in this implementation;
- listen for correct pitch/speed after seeks;
- press F3+T during playback and verify the renderer recovers near the semantic position;
- walk beyond the speaker's original 32-block radius, return, and confirm a stop issued while away did not leave stale playback;
- remove and replace the speaker at the same block position and verify the new peripheral works rather than reusing stale state;
- shut down/rejoin and verify no cached playback continues.

## Known scope boundary

The new local-file path is the large-finite-media architecture. Legacy byte-taking calls such as `speakMp3(bytes)` remain present for compatibility while this branch is stabilized. They are not the recommended way to submit a large ComputerCraft file because constructing one giant Lua string/argument defeats the purpose of file staging even if network transport is later unified.

Multi-speaker asset sharing/synchronization is deliberately deferred until single-speaker local finite playback is proven.
