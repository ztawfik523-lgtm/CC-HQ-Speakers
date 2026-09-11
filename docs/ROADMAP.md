# Roadmap

## Product rule

Build a better **programmable ComputerCraft speaker peripheral**. Java exposes truthful audio capabilities; Lua decides whether they are used for music, alarms, speech, notifications, soundboards, PA systems, ambience, or anything else.

Technical source categories remain:

- standard CC:T speaker behavior (`playNote`, `playSound`, `playAudio`, `stop`);
- HQ raw/feed PCM;
- finite encoded media with a truthful timeline;
- live/open-ended network streams, later.

Do not add application roles or a Java playlist manager.

## Reference points

- immutable reviewed M1 reference: `fba84a33a94d451af09b983bcb04416c97ff64cf`;
- frozen local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc` on `codex/m1a-local-finite-media`;
- completed M0.5 cleanup/preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- M1A compatibility/output branch: `codex/m1a-compat-output`;
- completed M1B media-asset storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- verified M1C + configurable-storage base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- completed source/CI M1D media-analysis branch: `codex/m1d-media-analysis` (Minecraft runtime acceptance pending).

The frozen prototype proved that writable CC staging, chunked client transfer, disk-backed client encoded files, and incremental finite decoding are viable. Its fixed-recipient, renderer-authority, observation-timeout, and per-speaker media ownership models are prototypes scheduled for replacement, not architecture to polish.

## Settled architecture decisions

1. **No Java playlist.** A new incompatible HQ continuous playback replaces the prior HQ continuous playback. Lua owns sequencing/queue policy. Repeated chunks of one raw feed remain one feed. Standard CC:T methods retain their own native compatibility semantics.
2. **No permanent listener ownership list.** The server owns playback state; clients dynamically create/destroy renderers according to current range/tracking state and receive current state when they become relevant again.
3. **Finite media is an asset separate from playback and physical speaker.** One encoded asset may be referenced by several speaker playbacks.
4. **Large finite transfer is client-pulled and bounded.** Clients request bounded asset ranges; the next request naturally provides pacing/acknowledgement. Encoded file chunks are reliable, while stale decoded PCM may later be dropped at renderer taps to preserve real-time sync.
5. **Server owns the finite playback clock.** Client renderers may report errors, but do not define canonical start/position/EOF.
6. **Multispeaker finite playback shares media/timeline, not physical source position.** Transfer/cache/decode should be shared when possible; each audible physical speaker still has its own positional Minecraft/OpenAL renderer for spatial audio and future SPR processing.
7. **Finite sync has no expected-member barrier.** Synchronized playbacks reference a shared sync clock. A client may render whichever physical speakers are currently relevant without waiting for a global group count.
8. **Large-file local playback is the priority.** Internet MP3/HLS/TS work is deferred until finite/local media is solid.
9. **Storage safety limits are server configuration, not speaker semantics.** HQ Speaker only limits disk space allocated by the mod itself. Safe defaults are configurable, and `0` may disable either HQ-specific quota. ComputerCraft's own filesystem limits remain ComputerCraft/server policy.

Two later implementation choices remain deliberately open:

- first-play timing: immediately advance the server clock vs wait for initial nearby readiness;
- progressive finite playback: M1 starts with complete encoded cache before playback; progressive download/play is a later milestone, but the range protocol must not prevent it.

## M0.5 — cleanup and redesign preparation

**Status: completed at `ad38412a2173f849a0fc8e867030da8a78965c9c`.**

Completed:

- preserved the prototype as historical evidence;
- added deterministic speaker/provider cleanup for block removal, Level unload, and server stop;
- retained exact native CC:T delegation as the compatibility boundary;
- updated project guidance around the accepted redesign;
- made CI verify the bundled `hqspeaker.lua` module;
- deliberately did not repair prototype fixed-recipient/renderer-authority concepts scheduled for deletion.

## M1A — compatibility and output ownership

**Status: source/CI implemented on `codex/m1a-compat-output`; Minecraft runtime acceptance pending.**

Implemented for the normal single physical speaker:

- delegate standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` to CC:T 1.120.0;
- keep notes independent;
- one HQ continuous owner across raw/finite/stream intent;
- latest incompatible HQ start replaces the previous HQ source;
- truthful status/control ownership;
- truthful 131072 `speakPCM` table ceiling;
- separate `hqspeaker_audio_empty` RAW admission event;
- packet-count plus audio-duration RAW backpressure;
- sample-derived server-tick RAW drain and idle release;
- serialized source replacement when several computers use the same speaker.

Intentional M1A boundaries:

- old `*All` / `*At` methods remain legacy until M1J;
- range leave/re-enter rendering remains M1I;
- runtime scripts still need in-game execution.

## M1B — media asset storage layer

**Status: completed at `40091ee32f412c1208e9016fca288b8d4f902dfa`.**

Completed reusable encoded-media storage:

- speaker-independent UUID media identity;
- disk-backed `.part` -> atomic `.media` import;
- bounded exact-size copying;
- caller-supplied per-asset/total quotas with reservation before copy;
- `retain`/`release` ownership and final-reference deletion;
- seekable encoded-file reads;
- crash-orphan pruning;
- root OS lock;
- shutdown/import race handling and retryable close cleanup;
- unit coverage for import/lifetime/quota/concurrency/failure paths;
- exact `.247` / `.248` CI pass.

## M1C — local CC file import and configurable storage safety

**Status: source/CI implemented; verified base `33bcc6e04a2734500b7b15b84bee884562539216`; Minecraft runtime acceptance pending.**

Implemented:

- separate writable staging from finite playback (`HQMediaStaging`);
- one server-wide `MediaAssetStore` service (`ServerMediaAssets`);
- CC file size check before `fs.copy`;
- partial staging cleanup when `fs.copy` fails;
- import staged encoded bytes into a reusable asset UUID;
- lower-level peripheral prepare/play/release capabilities;
- bundled Lua helpers `prepareFile`, `playPrepared`, `releasePrepared`, and `playFile`;
- prepared ownership tied to ComputerCraft computer ID and released on detach;
- playback takes a separate asset reference so releasing a prepared handle does not stop playback;
- one asset UUID may be played by another physical speaker because encoded media is server-wide;
- server shutdown releases speaker ownership before closing the shared store;
- failed store close remains reachable for retry;
- dedicated `scripts/m1c_local_import_test.lua` runtime contract.

Server-owned storage settings:

- `mediaStorage.maxAssetMiB`, safe default 512 MiB;
- `mediaStorage.maxTotalMiB`, safe default 2048 MiB;
- `0` disables the corresponding HQ Speaker-specific quota;
- both are world/server-restart settings;
- the same per-asset setting controls temporary HQ staging so there is no second hidden staging limit;
- ComputerCraft filesystem capacity is not modified by HQ Speaker.

The old prototype packet's 512 MiB policy check was removed so configured limits are actually authoritative.

M1C deliberately still bridges prepared assets into the prototype finite sender. Fixed recipients, server-push whole-file transfer, client STARTED/ENDED authority, and renderer observation timeout remain temporary until M1E/M1F.

## M1D — server media analysis

**Status: source/unit-test/CI complete on `codex/m1d-media-analysis`; Minecraft runtime acceptance pending.**

Implemented and hardened:

- identify files from encoded bytes rather than filename extension;
- commit staging bytes to an immutable server asset first, then analyze that exact committed copy;
- reject and release the temporary asset if analysis fails, so no unsupported asset UUID is returned to Lua;
- bounded 64 KiB analysis window; no whole-track PCM decode;
- bounded seek metadata: at most 4096 MP3/OGG points, with adaptive self-thinning for deliberately huge/unlimited files;
- MP3 Layer III frame scan, ID3v2 skip, duration from frame sample counts, and coarse byte seek hints;
- OGG Vorbis identification/header validation, channel/rate metadata, final-granule duration, and coarse page seek hints;
- WAV RIFF analysis aligned to the current JavaSound client path: first data chunk after `fmt `, frame/block-alignment validation, and 32/64-bit-only IEEE float;
- uncompressed AIFF `COMM`/`SSND` analysis including 80-bit sample rate, 1–32-bit limit, real sound-byte validation, and rejection of non-zero SSND offsets the current JavaSound reader does not honor;
- AU/SND header/encoding/rate/channel analysis matching current JavaSound AU encoding support;
- reject non-Vorbis OGG, compressed AIFC, malformed decoder-incompatible WAV/AIFF, unsupported containers/encodings, and arbitrary bytes renamed to a supported extension;
- convert malformed numeric/container overflow into checked analysis failures and bound no-progress reads;
- `audioPreparedInfo` / `hqspeaker.preparedInfo` for format/duration/rate/channel facts;
- prepared playback status uses the same server-derived metadata immediately;
- finite advertised list narrowed to `wav`, `ogg`, `mp3`, `aiff`, `aif`, `au`, `snd`;
- MP2/MP4/M4A/AAC no longer advertised without exact decoder evidence;
- synthetic analyzer tests cover byte-based identification, duration, decoder parity, malformed inputs, bounded index behavior, and channel-reset behavior;
- `scripts/m1d_media_analysis_test.lua` remains the Minecraft runtime contract.

Current MP3 duration is encoded-frame duration. Gapless encoder delay/padding correction may be added later if exact timeline tests show it is needed.

M1D changes finite file truth, not the old transport/state architecture. M1E/M1F still replace renderer authority and fixed-recipient push.

## M1E — server-authoritative finite playback

The server owns generation, state, position, duration, pause/resume, seek, loop, volume, and EOF.

Remove the prototype concepts of anchor renderer, successful-renderer authority, no-renderer timeout, client STARTED resetting the clock, and renderer-defined EOF. A finite playback progresses even when no player is nearby; a late listener joins at the current server position.

## M1F — bounded client-pulled asset transfer

Replace fixed-recipient server push with bounded range requests:

- client requests asset ID + offset + bounded length;
- server returns sub-1-MiB chunks (target 256 KiB packets);
- client writes the batch, then asks for the next range;
- bounds, permission, generation, and asset lifetime are validated;
- cancellation means stop requesting;
- disk/network IO runs on bounded workers, not server/client render ticks.

## M1G — reusable client asset cache

- `.part` and completed encoded asset files;
- size/integrity validation and atomic completion;
- reuse across leaving/re-entering range and across several speakers using the same asset;
- bounded cache size/LRU cleanup;
- orphan partial cleanup after crashes;
- no delete-on-renderer-stop behavior.

## M1H — incremental finite decoding

Maintain bounded RAM regardless of track duration:

- OGG: file-backed STB Vorbis reads/seeks;
- MP3: incremental decode with asynchronous seek and later coarse index optimization;
- WAV/AIFF/AU/genuine JavaSound formats: incremental conversion and format-appropriate seeking;
- separate natural EOF from close/cancel/resource reload;
- bounded decoder workers and prompt cancellation;
- no whole decoded PCM track as the large-file architecture.

## M1I — dynamic range/state rendering

Clients render current server state rather than owning playback:

- state snapshot when a speaker becomes relevant;
- update snapshots on playback/control changes and optionally a small lease/refresh;
- leaving range destroys/parks the local renderer only;
- returning while playback continues joins at current position;
- returning after stop stays silent;
- dimension/chunk/speaker removal is safe;
- VS2 position updates remain supported.

No historical recipient set is required. This milestone also closes the known case where an inherited range-local HQ stop can be missed by a client which already left speaker range.

## M1J — multispeaker shared assets and sync clocks

One asset may feed several physical speakers without repeated file transfer.

- synchronized playbacks reference one sync-clock ID;
- each physical speaker retains independent programmable state and may later leave the shared clock;
- no expected-group-size/expected-tap barrier;
- one client cache entry per asset;
- eventually decode once per shared asset/timeline and fan PCM to relevant renderers;
- one positional renderer per audible physical speaker, preserving distance, stereo direction, occlusion, VS2 movement, and future SPR processing;
- replace/reroute inherited `*All` / `*At` helpers so they no longer bypass current ownership/state architecture.

## M1K — renderer fan-out optimization

For playbacks using the same asset and sync clock, share decoder/PCM production while keeping independent positional sources. Use bounded per-renderer buffers; a lagging renderer may discard stale **decoded PCM** and rejoin the current timeline. Never drop encoded file ranges and pretend the asset is complete.

## M1L — migrate legacy finite byte APIs

Keep compatibility names such as `speakMp3(bytes)`, `speakOgg(bytes)`, and `speakWav(bytes)`, but route them into the same asset/playback engine. A modest Lua argument cap may remain; large CC files use `hq.playFile`.

Then remove the old whole-packet/whole-PCM finite decoder, duplicate status logic, and legacy finite decoder queue.

## M1M — HQ raw feed finalization

M1A establishes normal single-speaker RAW ownership/backpressure/idle-release. After shared renderer/range architecture exists:

- use the common dynamic range/state renderer lifecycle;
- preserve `hqspeaker_audio_empty`;
- keep finite duration/seek unavailable;
- add pause only if a coherent producer/feed contract justifies it;
- migrate old multi-speaker RAW helpers away from expected-group behavior.

## M1N — Minecraft/OpenAL integration cleanup

- use the appropriate CC-speaker sound category rather than arbitrary BLOCKS behavior;
- one logical volume stage plus Minecraft category/master scaling;
- F3+T/resource reload recovery;
- no stale channels;
- correct positional attenuation and VS2 movement.

## M1O — lifecycle hardening

Complete explicit cleanup for peripheral caches, asset managers, transfer workers, decoder workers, client caches, and global maps. No static world retention across integrated-server restart.

## M1P — automated state-machine coverage

Add pure/component tests for replacement generation, server EOF, pause/resume/seek/loop, asset lifetime/refcount, range request bounds, interrupted/resumed transfer, stale generations, dynamic range entry/exit, dimension change, shared sync clocks, decoder cancellation, renderer stale-PCM drop, and lifecycle cleanup.

## M1Q — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify packaged metadata, mixins, JarJar dependencies, and the bundled ComputerCraft ROM `hqspeaker.lua` module.

## M1R — consolidated Minecraft acceptance

Only after the architecture above is coherent, run one batched runtime pass covering standard CC speaker compatibility; HQ raw feed; MP3/OGG/WAV/AIFF/AU; >8 MiB and 50-100+ MiB local files; pause/resume/seek/loop/EOF/volume; replacement; late range entry; leave/return; stop while away; dimension change; F3+T; speaker replacement; disconnect/rejoin; asset reuse; synchronized multispeaker playback; independent speaker desync; memory/network usage; and tick stalls.

## M2 — progressive finite playback

M1 may require the complete encoded asset cache before rendering. M2 may start after a bounded prebuffer and continue downloading, including missing-range/seek coordination. The M1 range protocol must leave this possible.

## M3 — Sound Physics Remastered

Integrate the frozen SPR compatibility work only after positional renderer lifecycle is stable. Shared media/PCM must not collapse physical source positions. Test per-speaker occlusion/reverb and many-source performance. Companion mod vs integrated optional module remains an explicit packaging choice.

## M4 — live/open-ended streams

Only after finite/local media is stable:

- live MP3, HLS, TS;
- no fake duration/seek;
- pause/resume reconnects to current live point;
- single gain stage;
- HLS media-sequence progression;
- incremental TS;
- explicit unsupported-codec failure;
- bounded HTTP/resource handling;
- dynamic renderer/sync behavior without expected-tap deadlocks.

## M5 — release cleanup

Update state/fact/architecture docs, remove obsolete P0 language and dead classes, decide whether the separate `hqspeaker:hq_speaker` block remains, document final Lua APIs, and resolve the repository MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch before public release.
