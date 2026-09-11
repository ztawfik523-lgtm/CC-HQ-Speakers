# Current state

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- untouched inherited fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1 reference: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5 preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- M1C local-import/config base verified at: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- active implementation branch: `codex/m1e-server-authoritative-finite`

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
- finite encoded media with a truthful server-owned timeline;
- live/open-ended network streams later.

## Current finite product direction

The final finite path is intentionally simpler than the M1D/prototype compatibility surface.

Target formats:

- MP3 / MPEG Layer III;
- normal WAV using common/easy sample representations;
- normal native FLAC after its exact analyzer/decoder path is proven.

Not final product targets:

- OGG Vorbis;
- AIFF/AIF;
- AU/SND;
- Ogg-FLAC;
- unusual WAV codecs/bit widths retained only because JavaSound can open them.

One physical HQ speaker renders **mono positional audio**. Mono input stays mono. Stereo input is downmixed to mono. More-than-stereo finite input is rejected.

The client will not maintain a persistent finite-song cache. The target path streams bounded encoded ranges from the server into bounded temporary client RAM, decodes into a bounded mono PCM queue, and discards old data when it is no longer useful.

This still supports:

- large files;
- playback beginning before the entire encoded file transfers;
- late listeners joining current position;
- seek while streaming by requesting a new encoded window;
- pause/resume/loop/volume/EOF;
- bounded memory.

## M1A inherited behavior

The active branch retains the M1A single-speaker compatibility/output work:

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

This is **server-side asset storage**, not a client cache. It remains useful because the server must keep prepared finite files somewhere authoritative while they are referenced.

The exact M1B head passed the Java 21 NeoForge 21.1.247/21.1.248 CI matrix including tests, package verification, and candidate-JAR upload.

See `docs/M1B-MEDIA-ASSETS.md`.

## M1C local-file import and server storage configuration

The local-file path is:

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

For `maxAssetMiB = 0`, the server asset-store policy remains effectively unbounded. The ComputerCraft staging adapter clamps its capacity to `Long.MAX_VALUE - MountConstants.MINIMUM_FILE_SIZE` before constructing CC:T's writable mount, avoiding overflow in CC:T's internal accounting while remaining effectively unbounded in practice.

The old prototype packet's hard-coded 512 MiB policy check has been removed. File-size policy belongs to server config/store.

See `docs/SERVER-CONFIG.md` and `docs/M1C-LOCAL-IMPORT.md`.

## M1D server finite-media analysis — frozen historical implementation

M1D moved finite file identity/duration facts onto the server before playback starts. Its source/unit-test/CI implementation is frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`; Minecraft runtime acceptance remains pending.

Prepared files are identified from their encoded bytes rather than filename extension. The supported prepare path first commits exact staging bytes to the immutable server asset store, then analyzes that committed copy. If analysis fails, the asset reference is released and the rejected file is not exposed to Lua.

`FiniteMediaAnalyzer` uses one 64 KiB read window and never decodes the complete track to PCM.

M1D historically analyzes:

- MP3 / MPEG Layer III;
- OGG Vorbis;
- WAV forms aligned to the then-current JavaSound decoder path;
- uncompressed AIFF/AIF;
- AU/SND forms aligned to JavaSound.

That list is **historical current-code truth, not the final product format commitment**. Later finite-decoder work will narrow advertisement/analysis to MP3, common WAV, and proven native FLAC once the replacement path is ready.

M1D records actual format, duration, sample rate, channel count, bits per sample when meaningful, and bounded MP3/OGG encoded-byte seek hints. MP3 seek points are actual scanned frame offsets. OGG seek points are actual Ogg page offsets.

MP3 duration remains encoded-frame/sample-count duration and does not yet subtract encoder delay/padding from gapless metadata.

See `docs/M1D-MEDIA-ANALYSIS.md`.

## Decoder/streaming evidence established before M1E implementation

The simplified streaming design has been checked against the exact target direction rather than assuming a complete client file is required.

Important implementation constraints:

- temporary absence of encoded bytes is not finite EOF;
- MP3 decoding must not be told `EOF` merely because the next requested range has not arrived yet;
- MP3 random rejoin/seek requires earlier-frame pre-roll because MPEG Layer III can depend on bit-reservoir data carried by earlier frames;
- game/audio threads must consume already-available bounded data and must not block on server IO/network refill;
- server/client `System.nanoTime()` values are not directly comparable across separate JVMs, so canonical sync should use server-reported positions and measured correction rather than subtracting raw nanoTime values;
- no persistent client disk cache is needed for seek or late join because the server can serve fresh bounded encoded ranges around the new/current position.

Native FLAC remains a target, not a completed fact. It must receive its own exact decoder/analyzer/seek and packaging proof before being advertised.

## Transitional finite playback still present

The active branch still starts from the frozen M1D codebase. Prepared assets currently bridge into `HQFiniteMediaServer`, which still has prototype behavior until M1E code changes land:

- fixed player recipients captured at playback start;
- server-pushed begin/chunk/end whole-file transfer;
- client waits for complete encoded transfer before constructing the existing file decoder;
- client STARTED/ENDED reports affect canonical playback state;
- renderer-observation timeout;
- no dynamic late join from authoritative server state.

M1E removes client renderer authority and makes server clock/state canonical. M1F replaces fixed-recipient whole-file push with bounded client-requested streaming. M1G replaces full-file client decoding with bounded progressive MP3/WAV/FLAC decode and temporary RAM buffering.

## M1E immediate target

M1E is the active implementation milestone.

Required semantic changes:

- a successful finite `play` starts the server clock immediately;
- playback progresses with zero listeners;
- client readiness does not create a server `LOADING` state;
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED no longer mutate canonical state;
- no renderer-observation timeout can fail canonical playback;
- server duration/clock determines natural EOF;
- non-looping `seek(duration)` ends immediately;
- looping wraps server position;
- client errors remain local/diagnostic;
- while old full-file transfer temporarily remains, a READY client starts at the current server position, not at 0.

## Streaming roadmap after M1E

- M1F: bounded client-requested encoded ranges with off-thread server IO and request/relevance limits;
- M1G: bounded-RAM progressive MP3/common-WAV/native-FLAC decoding and mono output;
- M1H: dynamic range/state lifecycle, late join, leave/re-enter, underrun/rejoin hardening;
- M1I: multispeaker shared server assets and sync clocks without persistent client caching.

The old separate M2 progressive-playback milestone is retired: progressive finite streaming is part of finishing M1 correctly.

## Multi-speaker boundary

The inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path and retain the old expected-group/tap design. They are scheduled for replacement after dynamic finite rendering rather than being patched onto architecture already marked for removal.

## Runtime/testing state

Pure Java coverage currently includes:

- existing finite track/clock/path and HLS parser tests;
- M1A RAW lifetime/admission tests;
- M1B asset import, quota, reference, crash, root-lock, and shutdown tests;
- M1D synthetic WAV/AIFF/AU/OGG-Vorbis/MP3 analysis;
- ID3v2 MP3 handling;
- non-Vorbis and truncated-identification OGG rejection;
- WAV first-data semantics, block-alignment checks, and float-width rejection;
- AIFF width/offset/truncation checks;
- bounded adaptive seek-index behavior;
- accepted WAV/AIFF/AU fixtures opening through the then-current JavaSound conversion shape;
- analyzer channel reset after success/failure;
- storage-limit adapter coverage.

Those old format tests remain valid evidence for frozen M1D until later cleanup deliberately removes the obsolete format surface.

Runtime scripts relevant to completed work:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua`
- `scripts/m1c_local_import_test.lua`
- `scripts/m1d_media_analysis_test.lua`

None should be reported as a runtime PASS until actually executed successfully in Minecraft on the target stack.

Other retained issues such as legacy finite byte APIs, live HLS/TS behavior, sound-category normalization, SPR integration, and the license metadata mismatch remain later roadmap work.
