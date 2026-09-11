# M1D — server finite-media analysis

## Goal

Make the server know what a prepared finite file actually is **before** playback starts.

The file extension is not trusted. A file called `song.bin` containing a real MP3 may be accepted; a file called `song.mp3` containing arbitrary bytes must be rejected.

M1D does not decode the whole track to PCM. It scans encoded container/frame metadata with a reusable 64 KiB window.

## Current analyzed formats

The prepared/local-file path currently accepts:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- WAV;
- uncompressed AIFF/AIF;
- AU/SND.

OGG files using another codec such as Opus are rejected by the current finite path. Compressed AIFC is also rejected.

The exposed finite-format list no longer advertises MP2, MP4, M4A, or AAC because this repository does not currently have exact finite-decoder evidence for them.

## Facts recorded per asset

`MediaMetadata` records:

- actual encoded format;
- duration in seconds;
- sample rate;
- channel count;
- encoded bits per sample where the container exposes a meaningful fixed value;
- coarse encoded-file seek hints where practical.

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

`audioStatus()` for the M1C prepared-file bridge uses the same server-derived format/duration/rate/channel facts immediately. It no longer waits for a client renderer to invent the duration.

## Format-specific analysis

### MP3

The analyzer:

- skips an ID3v2 tag at the beginning;
- searches a bounded prefix for a valid MPEG Layer III frame sequence;
- walks frame headers without decoding PCM;
- supports MPEG-1, MPEG-2, and MPEG-2.5 Layer III frame timing;
- derives duration from encoded frame sample counts;
- records coarse byte offsets approximately every five seconds for later seek/range work;
- rejects a stream which changes sample rate or channel layout mid-file.

Current MP3 duration is encoded-frame duration. M1D does not yet subtract encoder delay/padding from LAME/Xing gapless metadata.

### OGG Vorbis

The analyzer:

- verifies the first logical stream is Vorbis rather than trusting `.ogg`;
- reads channel count and sample rate from the Vorbis identification packet;
- walks Ogg pages to the final granule position for duration;
- records coarse page offsets for later seeking/range work;
- rejects chained Vorbis logical streams for now.

The lightweight analyzer does not replace the client decoder's own validity checks.

### WAV

The analyzer reads RIFF/WAVE `fmt ` and `data` chunks, deriving duration from encoded data bytes, sample rate, and block alignment. It recognizes PCM, IEEE floating PCM, A-law, and mu-law format tags currently compatible with the JavaSound conversion path used by the client.

### AIFF

The analyzer reads `COMM` and `SSND`, including the AIFF 80-bit extended sample-rate field. Standard uncompressed AIFF is supported; compressed AIFC is currently rejected.

### AU/SND

The analyzer reads the `.snd` header and derives duration from the declared/available data length, encoding width, sample rate, and channels. The current JavaSound client path remains responsible for actual sample conversion.

## Memory behavior

The analyzer does not allocate the encoded file or decoded track in memory. It uses one 64 KiB window and scans metadata/frame/page boundaries.

MP3/OGG seek hints are small metadata records rather than decoded samples. Under the default 512 MiB per-asset safety setting this remains modest; later seek-index work may further tune indexing density for deliberately very large/unlimited server configurations.

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
- uncompressed AIFF duration/rate/channels;
- AU PCM duration/rate/channels;
- OGG Vorbis identification and granule-derived duration;
- MP3 frame-derived duration and coarse seek hints;
- ID3v2 skipping;
- OGG non-Vorbis rejection;
- unsupported MP4-style bytes rejection even when the filename claims MP3;
- channel position reset after analysis failure.

`scripts/m1d_media_analysis_test.lua` is the Minecraft runtime contract for byte-based identification, prepared metadata, playback status agreement, invalid-file rejection, and the truthful supported-format list.

Source/CI success is not Minecraft runtime proof. The Lua contract must still be run in-game before M1D is called runtime-accepted.
