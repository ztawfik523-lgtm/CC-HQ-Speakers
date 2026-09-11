# M1D — server finite-media analysis

## Goal

Make the server know what a prepared finite file actually is **before** playback starts.

The file extension is not trusted. A file called `song.bin` containing a real supported MP3 may be accepted; a file called `song.mp3` containing arbitrary bytes must be rejected.

M1D does not decode the whole track to PCM. It scans encoded container/frame metadata with a reusable 64 KiB window.

For the prepared-file path, analysis is performed on the **committed immutable server asset**, not the writable ComputerCraft staging file. Preparation therefore cannot attach metadata for one staging-file version to different bytes copied a moment later.

## Current analyzed formats

The prepared/local-file path currently accepts:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- WAV formats which match the current JavaSound client conversion path;
- uncompressed AIFF/AIF within the current JavaSound reader's supported shape;
- AU/SND encodings supported by the current JavaSound reader.

OGG files using another codec such as Opus are rejected by the current finite path. Compressed AIFC is also rejected.

The exposed finite-format list no longer advertises MP2, MP4, M4A, or AAC because this repository does not currently have exact finite-decoder evidence for them.

## Facts recorded per asset

`MediaMetadata` records:

- actual encoded format;
- duration in seconds;
- sample rate;
- channel count;
- encoded bits per sample where the container exposes a meaningful fixed value;
- bounded coarse encoded-file seek hints where practical.

Lua may inspect a prepared asset with:

```lua
local hq = require("hqspeaker")
local asset = hq.prepareFile(speaker, "/music/song.mp3")
local info = hq.preparedInfo(speaker, asset)

print(info.format)
print(info.duration)
print(info.sampleRate)
print(info.channels)
```

`audioStatus()` for the M1C prepared-file bridge uses the same server-derived format/duration/rate/channel facts immediately. It no longer waits for a client renderer to supply duration.

## Preparation ordering and asset truth

The supported prepared-file flow is:

```text
writable ComputerCraft staging file
    -> exact-size import into immutable server MediaAsset
    -> analyze that committed asset
    -> attach metadata
    -> return asset UUID to Lua
```

If analysis rejects the committed bytes, the temporary asset reference is released and its encoded file is deleted. A rejected file is never returned as a prepared asset.

This order matters because the staging mount remains writable by ComputerCraft. Analyzing staging first and copying it afterward would leave a time-of-check/time-of-use race where a program could change the file between analysis and import.

## Format-specific analysis

### MP3

The analyzer:

- skips an ID3v2 tag at the beginning;
- searches a bounded prefix for a valid MPEG Layer III frame sequence;
- walks frame headers without decoding PCM;
- supports MPEG-1, MPEG-2, and MPEG-2.5 Layer III frame timing;
- derives duration from encoded frame sample counts;
- records bounded coarse byte offsets for later seek/range work;
- rejects a stream which changes sample rate or channel layout mid-file.

Current MP3 duration is encoded-frame duration. M1D does not yet subtract encoder delay/padding from LAME/Xing gapless metadata.

### OGG Vorbis

The analyzer:

- requires the first logical stream to begin as a Vorbis stream rather than trusting `.ogg`;
- requires a complete 30-byte Vorbis identification header;
- validates version, channel count, sample rate, block-size exponents, and framing bit;
- walks Ogg pages to the final granule position for duration;
- records bounded coarse page offsets for later seeking/range work;
- rejects chained Vorbis logical streams for now.

The lightweight analyzer does not replace STB Vorbis's own deeper bitstream validation at decode time.

### WAV

The analyzer follows the current JavaSound client reader/conversion shape rather than accepting every syntactically plausible RIFF file:

- `fmt ` must appear before the first usable `data` chunk;
- the first `data` chunk after `fmt ` defines the current client stream;
- PCM, IEEE floating PCM, A-law, and mu-law tags are recognized;
- floating PCM is limited to 32- or 64-bit samples, matching the Java float converter;
- A-law/mu-law require 8-bit samples;
- PCM frame size/block alignment must equal `ceil(bits / 8) * channels`;
- duration is based on complete encoded frames in that first data chunk.

WAVE_FORMAT_EXTENSIBLE is deliberately not claimed yet even though modern JavaSound has a reader for PCM/float extensible WAV. Adding it should be explicit and tested against the shipped runtime rather than inferred from the container family name.

### AIFF

The analyzer reads `COMM` and `SSND`, including the AIFF 80-bit extended sample-rate field. Current acceptance is deliberately aligned to the JavaSound client reader:

- standard uncompressed AIFF only;
- 1–32 encoded bits per sample;
- `COMM` must precede `SSND`;
- non-zero SSND data offsets are rejected because the current JavaSound reader reads that field but does not apply it when positioning audio data;
- declared sample-frame count must fit in the available SSND audio bytes.

Compressed AIFC remains rejected.

### AU/SND

The analyzer reads the `.snd` header and accepts the encodings supported by the JavaSound AU reader used by the client:

- mu-law 8-bit;
- signed linear PCM 8/16/24/32-bit;
- float 32-bit;
- double 64-bit;
- A-law 8-bit.

Duration is based on complete encoded frames. Header/data offsets and declared data lengths are bounded against the real file.

## Memory and malformed-input behavior

The analyzer never materializes the encoded file or decoded track in memory. It uses one 64 KiB read window and scans metadata/frame/page boundaries.

MP3/OGG seek hints begin at a five-second granularity but are capped at **4096 points per asset**. When the cap would be exceeded, the index keeps every other point and doubles its interval. This keeps metadata memory bounded even if an administrator disables the normal file-size limit while preserving coarse coverage across the full track.

Malformed numeric/container values are converted to checked analysis failures rather than escaping as arithmetic errors. The window reader also rejects a source which repeatedly makes no read progress instead of spinning forever.

## What M1D deliberately does not fix

Playback still temporarily uses the old staged-finite transport underneath M1C prepared assets. M1D does **not** fix:

- fixed recipient lists;
- server-push whole-asset transfer;
- the renderer-observation timeout;
- clients deciding STARTED/ENDED state;
- late listener joining;
- dynamic range rendering;
- progressive download/playback.

Those are M1E and later milestones. M1D only moves finite **file truth** onto the server.

## Tests

`FiniteMediaAnalyzerTest` constructs synthetic encoded containers/frame sequences and checks:

- PCM WAV duration/rate/channels;
- first-WAV-data-chunk duration semantics;
- invalid WAV block alignment rejection;
- unsupported floating-WAV sample width rejection;
- uncompressed AIFF duration/rate/channels;
- AIFF >32-bit rejection;
- non-zero AIFF SSND offset rejection;
- truncated AIFF audio-data rejection;
- AU PCM duration/rate/channels;
- OGG Vorbis identification and granule-derived duration;
- truncated/non-Vorbis OGG rejection;
- bounded adaptive seek metadata across a long synthetic OGG timeline;
- MP3 frame-derived duration and coarse seek hints;
- ID3v2 skipping;
- unsupported MP4-style bytes rejection even when the filename claims MP3;
- accepted WAV/AIFF/AU fixtures opening through the same JavaSound conversion shape used by the client;
- channel position reset after both successful and failed analysis.

`MediaStorageLimitsTest` also covers the storage-policy adapter needed by large/unlimited M1D files, including the CC:T writable-mount overflow clamp for the `0` (no HQ quota) setting.

`scripts/m1d_media_analysis_test.lua` is the Minecraft runtime contract for byte-based identification, prepared metadata, playback-status agreement, invalid-file rejection, and the truthful supported-format list. For a small accepted fixture it also waits for `audioStatus().observed == true`, so the manual pass proves that the client decoder accepted the prepared asset rather than merely proving server metadata analysis.

Source/CI success is not Minecraft runtime proof. The Lua contract must still be run in-game before M1D is called runtime-accepted.
