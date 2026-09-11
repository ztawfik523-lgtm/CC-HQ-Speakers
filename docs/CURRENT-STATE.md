# Current state

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- active branch: `codex/m1e-server-authoritative-finite`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

CC:HQ Speakers upgrades the normal CC:T speaker into a programmable audio peripheral. Lua owns application policy such as playlists, alarms, notifications, speech, ambience, and sequencing. Java exposes truthful source capabilities.

Source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a server-owned timeline;
- live/open-ended network streams later.

## Final finite direction

Core target formats:

- MP3 / MPEG Layer III;
- common WAV.

Wanted but separately gated:

- normal native FLAC, only after its analyzer/decoder/seek/package path is proven.

Not final product requirements:

- OGG Vorbis;
- Ogg-FLAC;
- AIFF/AIF;
- AU/SND;
- exotic/compressed/telephony WAV variants;
- >2-channel finite input.

One physical speaker renders one **mono positional** source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

Finite data is streamed progressively from the authoritative server asset into bounded temporary client RAM. The final design has no persistent client song cache, `.part` library, LRU database, sparse cache file, or cross-restart download resume.

This still preserves the features that matter:

- large files;
- audible start before full transfer;
- duration/position;
- pause/resume;
- seek while streaming;
- loop;
- volume;
- natural EOF;
- late listeners joining current time;
- leave/re-enter recovery;
- bounded memory.

## M1A retained behavior

The active branch retains the M1A single-speaker compatibility/output work:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`;
- notes remain independent;
- one incompatible HQ continuous source replaces the prior HQ source;
- HQ RAW has separate bounded `hqspeaker_audio_empty` producer pacing;
- `speakMaxSamples()` reports 131072;
- ownership-changing calls on one physical speaker are serialized.

M1A Minecraft acceptance remains pending until its runtime contract is actually executed successfully.

## M1B/M1C server asset foundation

`MediaAssetStore` provides server-side UUID media identity, exact disk-backed import, quotas, retain/release lifetime, final-reference deletion, and seekable reads.

This is **server storage**, not a client cache.

The local-file path is:

```text
ComputerCraft file
    -> temporary HQ staging mount
    -> immutable server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Current server-owned storage defaults:

- `mediaStorage.maxAssetMiB = 512`;
- `mediaStorage.maxTotalMiB = 2048`;
- `0` removes that HQ-specific quota;
- ComputerCraft filesystem capacity is not changed.

The unlimited staging path already clamps around CC:T's internal `MINIMUM_FILE_SIZE` accounting so `Long.MAX_VALUE` does not overflow.

## M1D — frozen historical media analysis

M1D is frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`. Final GitHub Actions run `34635484316` passed both target NeoForge versions.

M1D moved format/duration truth onto the server and analyzes the exact immutable committed asset with one 64 KiB window.

Historical M1D analysis includes:

- MP3;
- OGG Vorbis;
- WAV;
- uncompressed AIFF;
- AU.

That is historical code truth, **not the final format promise**.

Useful M1D facts retained by the new design:

- MP3 duration from scanned Layer III frames;
- MP3 seek points are real scanned frame offsets;
- server-known duration/sample-rate/channels/size;
- bounded seek metadata;
- immutable committed-byte analysis before Lua receives the asset UUID.

MP3 duration is still encoded-frame duration; gapless delay/padding correction is optional later accuracy work.

## Current transitional finite code

No M1E runtime code has landed yet. The branch still begins from the frozen M1D implementation plus documentation cleanup.

`HQFiniteMediaServer` still currently has prototype behavior:

- fixed recipients captured at play start;
- server-pushed whole-file begin/chunk/end transfer;
- server state `LOADING` tied to renderer readiness;
- `successfulRenderers` / `observed` authority;
- 15-second no-renderer error;
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED can rewrite canonical clock/state;
- file reads happen from the server tick.

`HQFiniteMediaClient` still currently:

- creates `hqspeaker-cache` files on client disk;
- writes the complete `.part` file on the Minecraft client thread;
- waits for the whole encoded file;
- renames it to `.media`;
- opens `FileFiniteAudioStream` only after full transfer;
- starts from 0 after transfer;
- maintains a second client semantic clock and reports renderer state back to the server.

All of those finite-transfer/state behaviors are transitional and scheduled for replacement.

## Streaming evidence/constraints already established

The new direction has been checked against the actual target implementation constraints:

- temporary absence of encoded bytes is **not EOF**;
- the exact shipped JLayer family can decode progressively when its input waits for missing bytes instead of reporting EOF;
- MP3 seek/rejoin needs earlier-frame pre-roll because Layer III bit-reservoir state can depend on previous frames;
- sound/game threads must consume ready bounded data and never block on network or file IO;
- server/client `System.nanoTime()` values cannot be compared directly across JVMs;
- no persistent client cache is required for seek or late join because the server can serve fresh ranges;
- FLAC remains a target, not a proven implementation fact.

## Active M1E target

M1E now includes both semantic authority and the state packet needed to make the temporary bridge correct.

Required work:

- server states become `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- successful finite play starts canonical time immediately;
- playback advances with zero listeners;
- client renderer reports stop controlling server truth;
- remove no-renderer timeout/anchor authority;
- server clock/duration determines EOF;
- exact-end seek semantics are server-owned;
- before canonical EOF releases the playback asset, close/cancel the temporary transfer still using it;
- add a server -> client finite state snapshot containing semantic state, current position, generation, asset/format/size/duration, loop, volume, and position information;
- use the snapshot for control changes and the temporary READY bridge;
- a client that finishes the old whole-file transfer late must begin near the **current** server position, not at 0.

## Current roadmap after M1E

- **M1F:** client-requested encoded ranges, stream/seek anchors, off-thread server IO, bounded request limits, no client disk cache.
- **M1G:** progressive MP3 + common WAV, bounded encoded/PCM RAM, mono output, and active-branch format narrowing.
- **M1H:** dynamic relevance, late join, leave/re-enter, underrun/rejoin, stale-generation hardening.
- **M1I:** optional/gated native FLAC extension. MP3/WAV completion does not wait for it.
- **M1J:** functional multispeaker shared clocks and one positional renderer per block.
- **M1K:** active-session transfer/decode fan-out optimization.
- **M1L+:** legacy finite migration, RAW finalization, OpenAL cleanup, lifecycle/performance hardening, package verification, and consolidated runtime acceptance.

After M1:

- **M2:** Sound Physics Remastered integration;
- **M3:** live/open-ended network streams;
- **M4:** release cleanup.

See `ROADMAP.md` and `M1E-FINITE-STREAMING-DESIGN.md` for the exact implementation contract.

## Testing state

Pure Java coverage currently includes the existing finite clock/path tests, M1A RAW tests, M1B asset-store tests, M1D analyzer tests, and storage-limit tests.

Frozen M1D tests for OGG/AIFF/AU remain historical evidence until M1G deliberately narrows the active format surface.

Runtime contracts from completed work remain:

- `scripts/p0_cc_speaker_contract.lua`;
- `scripts/m1a_output_contract.lua`;
- `scripts/m1c_local_import_test.lua`;
- `scripts/m1d_media_analysis_test.lua`.

None should be reported as a Minecraft runtime PASS until actually executed successfully on the target stack.

## Other retained issues

Later work still includes:

- inherited `*All` / `*At` bypasses;
- legacy finite byte APIs;
- stream/HLS/TS defects;
- sound-category/gain cleanup;
- F3+T/resource lifecycle;
- SPR integration;
- separate custom HQ block decision;
- repository license/metadata mismatch.
