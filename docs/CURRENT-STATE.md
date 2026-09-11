# Current state

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- untouched inherited fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1 reference: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5 preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- M1C local-import/config base verified at: `33bcc6e04a2734500b7b15b84bee884562539216`
- current implementation branch: `codex/m1d-media-analysis`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

The mod upgrades the normal CC:Tweaked speaker into a programmable ComputerCraft audio peripheral. Java exposes technical audio capabilities; Lua owns application policy such as sequencing, playlists, alarms, notifications, and similar behavior.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a truthful timeline;
- live/open-ended network streams later.

## M1A inherited behavior

The current branch retains the M1A single-speaker compatibility/output work:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`;
- native notes remain independent;
- one incompatible HQ continuous source replaces the prior HQ source; Java does not queue a playlist;
- `audioStatus()` follows the current HQ source instead of stale terminal state from another subsystem;
- `audioStop()` stops the current HQ source;
- `speakMaxSamples()` reports the real 131072-sample contiguous table ceiling;
- HQ RAW has separate `hqspeaker_audio_empty` admission/backpressure behavior;
- RAW admission is bounded by the inherited 16-packet queue plus 135872 outstanding samples and drains at 2400 samples/server tick;
- ownership-changing calls on one physical speaker run one at a time.

M1A Minecraft acceptance remains pending until its runtime scripts are actually run in-game.

## M1B completed storage foundation

`MediaAssetStore` provides:

- UUID media identity independent of speakers;
- exact-size disk-backed import through `.part` then atomic `.media` publication;
- bounded copy buffers rather than whole-file RAM reads;
- caller-supplied per-asset and total quotas with pre-copy reservation;
- `retain`/`release` reference lifetime;
- final-reference file deletion;
- seekable encoded-file reads;
- startup orphan pruning;
- a root OS file lock;
- shutdown/import race handling and retryable close cleanup.

The exact M1B head passed the Java 21 NeoForge 21.1.247/21.1.248 CI matrix including tests, package verification, and candidate-JAR upload.

See `docs/M1B-MEDIA-ASSETS.md`.

## M1C local-file import and server storage configuration

The local-file path is now:

```text
CC filesystem file
    -> temporary writable HQ Speaker staging mount
    -> shared server MediaAsset UUID
    -> prepared-owner reference
    -> playback reference when started
```

`HQMediaStaging` owns the ComputerCraft-visible staging mount. `ServerMediaAssets` owns one shared `MediaAssetStore` per running Minecraft server. Prepared ownership is tracked by ComputerCraft computer ID; playback takes a separate reference, so releasing a preparation handle does not kill a playing asset.

Lua helper capabilities:

- `prepareFile(speaker, path)`
- `preparedInfo(speaker, assetId)`
- `playPrepared(speaker, assetId [, options])`
- `releasePrepared(speaker, assetId)`
- `playFile(speaker, path [, options])`

### Storage policy

ComputerCraft's own filesystem capacity is **not** controlled by HQ Speaker.

HQ Speaker has NeoForge `SERVER` safety settings only for disk space allocated by the mod itself:

- `mediaStorage.maxAssetMiB` — default 512 MiB per prepared asset/staging file;
- `mediaStorage.maxTotalMiB` — default 2048 MiB total prepared-media store;
- either value may be set to `0` to remove that HQ Speaker-specific quota;
- changes require a world/server restart.

The old prototype packet's hard-coded 512 MiB policy check has been removed. Transfer packets validate wire sanity; file-size policy belongs to the server config/store.

See `docs/SERVER-CONFIG.md` and `docs/M1C-LOCAL-IMPORT.md`.

## M1D server finite-media analysis

M1D moves finite file identity/duration facts onto the server before playback starts.

Prepared files are inspected from their encoded bytes rather than their filename extension. `FiniteMediaAnalyzer` uses one 64 KiB read window and does not decode the whole track to PCM.

Current accepted prepared/local formats:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- WAV;
- uncompressed AIFF/AIF;
- AU/SND.

The analyzer records:

- actual format;
- duration;
- sample rate;
- channel count;
- bits per sample when meaningful;
- coarse MP3/OGG encoded-byte seek hints.

Examples of explicit rejection now include:

- arbitrary bytes renamed to `.mp3`;
- OGG using a codec other than Vorbis;
- compressed AIFC;
- unsupported WAV/AU encodings.

`audioPreparedInfo(assetId)` and `hqspeaker.preparedInfo(...)` expose the server-derived facts before playback. The prepared-file bridge also puts the same format/duration/rate/channel facts into `audioStatus()` immediately.

The exposed `speakSupportedFiles()` list has been narrowed to `wav`, `ogg`, `mp3`, `aiff`, `aif`, `au`, and `snd`; MP2/MP4/M4A/AAC are no longer advertised without decoder evidence.

MP3 duration is currently frame/sample-count duration and does not yet subtract encoder delay/padding from gapless metadata.

See `docs/M1D-MEDIA-ANALYSIS.md`.

## Transitional finite playback still present

M1D fixes file truth; it does not pretend the old finite sender is final.

Prepared assets currently bridge into `HQFiniteMediaServer`, which still uses prototype behavior:

- fixed player recipients captured at playback start;
- server-pushed begin/chunk/end whole-file transfer;
- client STARTED/ENDED reports affecting canonical playback state;
- renderer-observation timeout;
- no dynamic late join from authoritative server state.

One improvement already available to the bridge is that finite duration/format now comes from the server analysis rather than a client renderer or filename extension.

M1E removes client renderer authority and makes the server clock/state canonical. M1F replaces fixed-recipient whole-file push with bounded client-pulled asset ranges.

## Multi-speaker boundary

The inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path and retain the old expected-group/tap design. They are scheduled for replacement in M1J rather than being patched onto architecture already marked for removal.

## Runtime/testing state

Pure Java coverage now includes:

- existing finite track/clock/path and HLS parser tests;
- M1A RAW lifetime/admission tests;
- M1B asset import, quota, reference, crash, root-lock, and shutdown tests;
- M1D synthetic WAV/AIFF/AU/OGG-Vorbis/MP3 analysis;
- ID3v2 MP3 handling;
- non-Vorbis OGG rejection;
- invalid/unsupported file rejection;
- analyzer channel reset after failure.

Runtime scripts relevant now:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua`
- `scripts/m1c_local_import_test.lua`
- `scripts/m1d_media_analysis_test.lua`

None should be reported as a runtime PASS until actually executed successfully in Minecraft on the target stack.

## Next implementation milestones

- M1E: server-authoritative finite playback state/clock/EOF;
- M1F: bounded client-pulled asset transfer and worker-thread IO;
- M1G/H: reusable client cache and hardened incremental decode;
- M1I/J: dynamic range rendering and shared multispeaker assets/sync clocks.

Other retained issues such as legacy finite byte APIs, live HLS/TS behavior, sound-category normalization, SPR integration, and the license metadata mismatch remain later roadmap work.
