# M1D — server finite-media analysis

> **Historical M1D milestone record.** M1D intentionally analyzed a broader format set than the final modern prepared product. Do not use its OGG/AIFF/AU/WAV breadth as current M1G support. Current modern prepared support is MP3 + the selected common-WAV subset; see `CURRENT-STATE.md`, `LUA-API.md`, `KNOWN-ISSUES.md`, and exact current source.

## Goal which remains valid

Make the server know what a prepared finite file actually is **before** playback starts.

The file extension is not trusted. Analysis runs against the committed immutable server asset, not the writable staging file, preventing metadata/byte TOCTOU mismatches.

M1D does not decode a whole track to PCM; it scans encoded/container metadata with bounded working memory.

## Historical M1D analyzed formats

At the M1D checkpoint, `FiniteMediaAnalyzer` supported analysis for:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- broad WAV shapes aligned to the then-current JavaSound bridge;
- uncompressed AIFF/AIF in the then-supported reader shape;
- AU/SND encodings supported by that bridge.

It also stopped advertising MP2/MP4/M4A/AAC because exact finite-decoder evidence did not exist.

### Current modern-prepared correction

M1G deliberately narrowed active prepared/local playback to:

- MP3 / MPEG Layer III;
- supported common WAV only:
  - mono/stereo;
  - U8/S16/S24/S32/F32;
  - classic RIFF/WAVE;
  - selected narrow PCM/float `WAVE_FORMAT_EXTENSIBLE`;
  - WAVEX `validBits == containerBits`;
  - stereo downmix to mono;
  - source sample rate preserved.

Historical OGG/AIFF/AU analysis and inherited APIs do **not** define the modern prepared support surface. Native FLAC remains separately gated M1I work.

## Metadata / server truth

`MediaMetadata` records finite facts such as:

- actual encoded format;
- duration;
- sample rate;
- channel count;
- meaningful fixed encoded bits per sample;
- bounded coarse encoded seek hints where practical;
- later normalized common-WAV layout for the modern path.

Preparation order remains:

```text
writable CC staging file
-> exact-size import into immutable server MediaAsset
-> analyze committed asset
-> attach metadata
-> return prepared asset UUID
```

Rejected committed bytes are never exposed as a prepared asset.

## Historical format-analysis details

### MP3

M1D MP3 analysis:

- skipped ID3v2 at the beginning;
- searched a bounded prefix for valid Layer III frames;
- walked frame headers without PCM decode;
- supported MPEG-1/2/2.5 Layer III timing;
- derived duration from encoded frame sample counts;
- recorded bounded real frame byte offsets for later seek/range work;
- rejected sample-rate/channel-layout changes mid-file.

Current MP3 duration still does not promise sample-gapless LAME/Xing padding subtraction, and M1G explicitly does not require gapless-loop metadata work.

Current M1G seek uses E1 conservative earlier seek points, progressive JLayer decode forward, and pre-target PCM discard.

`FiniteDecodeAnchorSelector.Anchor` contains exactly `(offset, seconds)`. No richer frame-index/skip-frame/skip-sample fields are computed and discarded.

### OGG Vorbis

M1D validated Vorbis identification/page/granule facts and bounded Ogg page seek hints. This remains historical analyzer evidence only; OGG is not a modern prepared M1G requirement.

### WAV

The M1D WAV analyzer originally mirrored a broader JavaSound bridge, including shapes later removed from the modern target.

M1G subsequently replaced that active contract with a server-normalized common-WAV layout and the narrow PCM/float subset above. WAVE_FORMAT_EXTENSIBLE support is now intentionally narrow rather than broadly inferred.

### AIFF / AU

M1D contained bounded server analyzers for uncompressed AIFF and selected AU/SND encodings. These remain historical analysis facts and are not current modern prepared playback commitments.

## Memory / malformed-input behavior

The analyzer uses bounded metadata scanning rather than whole-file or whole-PCM retention. MP3/OGG seek metadata is bounded, historically capped at 4096 points with adaptive thinning.

The analysis-side window reader rejects repeated no-progress rather than spinning forever.

Do not confuse that with current KI-064: `MediaAssetStore.writeExact()` has a separate repeated-zero-read no-progress gap during asset import.

## Historical non-goals and later status

At M1D, playback still used older staged/whole-file transport and client authority pieces. Those were intentionally left to M1E/M1F/M1G.

Since then:

- M1E made server finite state/time canonical;
- M1F replaced whole-file client transfer with bounded demand-driven ranges/sliding encoded RAM;
- M1G integrated progressive MP3/common-WAV decode, bounded PCM, and positional Minecraft rendering.

Do not restore the historical complete-file JavaSound bridge merely to preserve M1D format breadth.

## Tests and evidence boundary

`FiniteMediaAnalyzerTest` historically covers synthetic MP3/OGG/WAV/AIFF/AU analysis and malformed cases. Those tests remain useful for legacy/analyzer code but do not by themselves define the current modern prepared acceptance surface.

`scripts/m1d_media_analysis_test.lua` expects the old broad M1D prepared format surface and is **not** a valid current M1G modern-prepared pass/fail gate.

Current test authority is `TESTING.md`. Focused audible M1G Minecraft acceptance remains unrecorded.
