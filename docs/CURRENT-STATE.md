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
- M1E code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`
- active branch: `codex/m1e-server-authoritative-finite`

M1D final CI run: `34635484316` — success on NeoForge 21.1.247 and 21.1.248.

M1E code-bearing CI run: `34658958488` — success on NeoForge 21.1.247 and 21.1.248.

M1E documentation head `2d56c089aa7c09ec19bb3bf1be4ebcb8aa0913f5` also passed run `34659384866`. Later documentation-only commits may move the active branch again; the M1E code-bearing checkpoint above remains the useful implementation anchor.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

For a new chat, start with `CHAT-HANDOFF-2026-09-12.md`, then re-read the current branch source before making changes.

## Product identity

CC:HQ Speakers upgrades the normal CC:T speaker into a programmable audio peripheral. Lua owns application policy such as playlists, alarms, notifications, speech, ambience, and sequencing. Java exposes truthful source capabilities.

Source categories remain standard CC:T speaker behavior, HQ raw/feed PCM, finite encoded media with a server-owned timeline, and live/open-ended network streams later.

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

Finite data is streamed progressively from the authoritative server asset into bounded temporary client RAM in the final engine. There is no final persistent client song cache, `.part` library, LRU database, sparse cache file, or cross-restart download resume.

The target preserves large files, progressive audible start, duration/position, pause/resume, seek while streaming, loop, volume, natural EOF, late join, leave/re-enter recovery, and bounded memory.

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

`MediaAssetStore` provides server-side UUID media identity, exact disk-backed import, quotas, retain/release lifetime, final-reference deletion, and seekable reads. This is **server storage**, not a client cache.

The local-file path is:

```text
ComputerCraft file
    -> temporary HQ staging mount
    -> immutable server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Current server-owned storage defaults are 512 MiB per asset and 2048 MiB total; `0` removes the corresponding HQ-specific quota. ComputerCraft filesystem capacity is not changed. The unlimited staging path already clamps around CC:T's internal `MINIMUM_FILE_SIZE` accounting so `Long.MAX_VALUE` does not overflow.

## M1D — frozen historical media analysis

M1D is frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`. Final GitHub Actions run `34635484316` passed both target NeoForge versions.

M1D moved format/duration truth onto the server and analyzes the exact immutable committed asset with one 64 KiB window. Historical M1D analysis includes MP3, OGG Vorbis, WAV, uncompressed AIFF, and AU. That is historical code truth, **not the final format promise**.

Useful M1D facts retained by the new design include scanned MP3 duration, real MP3 frame-offset seek points, server-known duration/sample-rate/channels/size, bounded seek metadata, and immutable committed-byte analysis before Lua receives the asset UUID.

MP3 duration is still encoded-frame duration; gapless delay/padding correction is optional later accuracy work.

## M1E — server-authoritative finite playback

**Source/test/CI complete at code-bearing head `d0e66ab9135359627086c13647d5241ad778643f`; Minecraft runtime acceptance remains pending.**

The server-side finite model is no longer renderer-authoritative:

- semantic states are `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- there is no server `LOADING` state for client buffering;
- a successful finite play starts the canonical clock immediately at position 0;
- the clock advances even with zero listeners;
- `successfulRenderers`, canonical `observed`, and the 15-second no-renderer failure were removed;
- natural non-looping EOF is determined from the known server duration/clock;
- looping uses wrapped server position;
- non-looping `seek(duration)` ends immediately;
- status/control paths finalize elapsed EOF before returning/applying state;
- canonical EOF closes the temporary old transfer before releasing the playback asset reference.

Protocol v4 adds `HQFiniteMediaStatePacket`. The packet responsibilities are deliberately split:

```text
BEGIN/setup (temporary old bridge)
- source/media/generation
- format + total encoded bytes
- speaker world/block position
- initial volume/loop/pause setup

STATE (authoritative mutable truth)
- source/media/generation
- playing/paused/ended/error
- canonical position + duration
- volume + loop
- server error detail if any
```

Client -> server finite telemetry is only:

- `READY` — asks for a fresh authoritative state after the temporary complete-file bridge becomes decoder-ready;
- `ERROR` — diagnostic only.

The old renderer `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, and `ENDED` transitions are gone from protocol v4 and cannot rewrite the server clock.

The temporary M1E client still downloads the complete encoded asset to `hqspeaker-cache` because transport replacement is M1F. However it no longer starts at `0` merely because transfer completed. It opens the old file decoder, reports READY, receives a fresh STATE packet, then starts/seeks at the server's **current** canonical position. Pause/seek/loop/volume changes which happened while downloading are therefore resolved from server truth instead of stale client state.

The client still has a local `FinitePlaybackClock`, but it is only a renderer projection for local restart/resource behavior; it is not canonical authority.

## What is still transitional after M1E

The following are intentionally still old architecture and are the M1F/M1G boundary:

- recipients are captured once at finite play start;
- the server blindly pushes the whole encoded file;
- server file reads happen during the server tick;
- the client writes `.part/.media` files on the client thread;
- the decoder still requires a complete local encoded file;
- no new listener can dynamically join after play start.

These are not final requirements. They are the next replacement slices.

## M1E testing/evidence state

Pure Java coverage includes deterministic `FinitePlaybackClock.reachedEnd()` behavior for non-looping and looping tracks in addition to earlier pause/resume/loop/seek tests.

Source/test/package CI is green at M1E code-bearing head via run `34658958488`.

Focused runtime contract:

- `scripts/m1e_server_authority_test.lua <small-mp3-or-wav>`

It checks immediate server `PLAYING`, position advancement independent of renderer readiness, pause freeze, resume progression, non-looping exact-duration END, and looping exact-duration wrap.

This script has **not** yet been executed successfully in Minecraft, so M1E is not a Minecraft runtime PASS.

The old `m1d_media_analysis_test.lua` contains renderer-`observed` assumptions from frozen M1D and is historical M1D evidence, not the active M1E semantic contract.

## Streaming evidence/constraints already established

- temporary absence of encoded bytes is **not EOF**;
- the exact shipped JLayer family can decode progressively when its input waits for missing bytes instead of reporting EOF;
- MP3 seek/rejoin needs earlier-frame pre-roll because Layer III bit-reservoir state can depend on previous frames;
- sound/game threads must consume ready bounded data and never block on network or file IO;
- server/client `System.nanoTime()` values cannot be compared directly across JVMs;
- no persistent client cache is required for seek or late join because the server can serve fresh ranges;
- FLAC remains a target, not a proven implementation fact.

## Next: M1F demand-driven finite transport

M1F is the next implementation milestone:

- client-requested encoded byte ranges;
- server-selected stream/seek anchors;
- generation/asset/range/relevance validation;
- bounded outstanding work/rate controls;
- off-thread server asset reads with safe retained lifetime;
- stale async work discarded after replacement/leave;
- bounded temporary client encoded RAM;
- no final client disk song files/cache.

After M1F:

- **M1G:** progressive MP3 + common WAV, bounded encoded/PCM RAM, mono output, active-branch format narrowing;
- **M1H:** dynamic relevance, late join, leave/re-enter, underrun/rejoin, stale-generation hardening;
- **M1I:** optional/gated native FLAC extension;
- **M1J:** functional multispeaker shared clocks and one positional renderer per block;
- **M1K:** active-session transfer/decode fan-out optimization;
- **M1L+:** legacy finite migration, RAW finalization, OpenAL cleanup, lifecycle/performance hardening, package verification, and consolidated runtime acceptance.

After M1: M2 SPR, M3 live/open-ended network streams, M4 release cleanup.

See `ROADMAP.md`, `M1E-SERVER-AUTHORITY.md`, and `M1E-FINITE-STREAMING-DESIGN.md` for the exact implementation boundaries.

## Other retained issues

Later work still includes inherited `*All` / `*At` bypasses, legacy finite byte APIs, stream/HLS/TS defects, sound-category/gain cleanup, F3+T/resource lifecycle, SPR integration, the separate custom HQ block decision, and the repository license/metadata mismatch.
