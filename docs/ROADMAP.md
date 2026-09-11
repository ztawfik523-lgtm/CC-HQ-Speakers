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
- M1A local-file prototype reference: `69e34a5346f6ce47580f49ed867c9951bfd338bc` on `codex/m1a-local-finite-media`;
- active cleanup/redesign preparation: `codex/m0.5-cleanup-prep`.

The M1A prototype proved that CC writable staging, chunked client-bound transfer, disk-backed client cache files, and incremental finite decoding are viable. Its fixed-recipient, renderer-authority, observation-timeout, and per-speaker media ownership models are prototypes scheduled for replacement, not architecture to polish.

## Settled architecture decisions

These supersede the old unresolved D1-D4 framing.

1. **No Java playlist.** A new incompatible HQ continuous playback replaces the prior HQ continuous playback. Lua owns sequencing/queue policy. Repeated chunks of one raw feed remain one feed. Standard CC:T methods retain their own native compatibility semantics.
2. **No permanent listener ownership list.** The server owns playback state; clients dynamically create/destroy renderers according to current range/tracking state and receive current state when they become relevant again.
3. **Finite media is an asset separate from playback and physical speaker.** One encoded asset may be referenced by several speaker playbacks.
4. **Large finite transfer is client-pulled and bounded.** Clients request bounded asset ranges; the next request naturally provides pacing/acknowledgement. Encoded file chunks are reliable, while stale decoded PCM may later be dropped at renderer taps to preserve real-time sync.
5. **Server owns the finite playback clock.** Client renderers may report errors, but do not define canonical start/position/EOF.
6. **Multispeaker finite playback shares media/timeline, not physical source position.** Transfer/cache/decode should be shared when possible; each audible physical speaker still has its own positional Minecraft/OpenAL renderer for spatial audio and future SPR processing.
7. **Finite sync has no expected-member barrier.** Synchronized playbacks reference a shared sync clock. A client may render whichever physical speakers are currently relevant without waiting for a global group count.
8. **Large-file local playback is the priority.** Internet MP3/HLS/TS work is deferred until finite/local media is solid.

Two implementation choices remain deliberately open:

- first-play timing: immediately advance the server clock vs wait for initial nearby readiness;
- progressive finite playback: M1 starts with complete encoded cache before playback; progressive download/play is a later milestone, but the range protocol must not prevent it.

## M0.5 — cleanup and redesign preparation

Goal: make the codebase safe to refactor without spending effort repairing concepts that will be deleted.

Work:

- preserve `69e34a5` as the prototype reference and work on a separate branch;
- add deterministic provider/world/server cleanup instead of relying on weak-key cache behavior;
- establish explicit cleanup boundaries for speaker removal, Level unload, server shutdown, and computer detach;
- keep native CC:T compatibility delegation as the standard-method boundary;
- mark legacy finite and prototype finite paths clearly so new code does not accidentally grow both architectures;
- update roadmap/agent/design docs to remove stale unresolved D1-D4 guidance;
- verify the bundled `hqspeaker.lua` ROM module in CI;
- keep pure finite clock/path tests green;
- do **not** repair the prototype renderer-observation timeout, fixed recipient set, or client-authoritative status model: those are scheduled for removal.

Exit: both NeoForge versions build, lifecycle cleanup is deterministic, docs describe the accepted target architecture, and the next finite refactor has one clear direction.

## M1A — compatibility and output ownership

- preserve CC:T 1.120.0 behavior by delegating `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` to CC:T's original speaker;
- keep note events independent as CC:T expects;
- define one HQ continuous output owner across HQ raw, finite, and later streams;
- new incompatible HQ playback replaces the previous HQ playback;
- remove ambiguous status ownership between old and new finite paths;
- fix `speakMaxSamples()` reporting;
- give HQ raw feed a truthful, separate readiness/backpressure contract;
- terminate drained raw renderers cleanly.

## M1B — media asset layer

Create a server-side finite media asset abstraction independent of a speaker:

- generated asset ID;
- encoded disk-backed file;
- size, detected format, duration/metadata;
- lifetime/reference ownership;
- safe cleanup after no playbacks reference the asset;
- no speaker UUID as the conceptual media identity.

The writable CC mount becomes an import/staging mechanism for assets, not a speaker-private song store.

## M1C — local CC file import

Keep the simple Lua entrypoint:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3")
```

Internally use `fs.copy` into the writable mount, validate size/path, clean partial staging on failure, prepare an asset, then play it. Add lower-level prepare/play/release capabilities so Lua may preload without Java implementing a playlist.

## M1D — server media analysis

Determine finite facts without whole-track PCM decode:

- actual supported format;
- duration;
- sample rate/container facts where useful;
- MP3 frame/coarse seek information as practical;
- OGG/WAV/AIFF/AU metadata;
- explicit unsupported-format failure.

Stop advertising AAC/M4A/MP4 unless exact decoder/runtime evidence exists.

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

No historical recipient set is required.

## M1J — multispeaker shared assets and sync clocks

One asset may feed several physical speakers without repeated file transfer.

- synchronized playbacks reference one sync-clock ID;
- each physical speaker retains independent programmable state and may later leave the shared clock;
- no expected-group-size/expected-tap barrier;
- one client cache entry per asset;
- eventually decode once per shared asset/timeline and fan PCM to relevant renderers;
- one positional renderer per audible physical speaker, preserving distance, stereo direction, occlusion, VS2 movement, and future SPR processing.

## M1K — renderer fan-out optimization

For playbacks using the same asset and sync clock, share decoder/PCM production while keeping independent positional sources. Use bounded per-renderer buffers; a lagging renderer may discard stale **decoded PCM** and rejoin the current timeline. Never drop encoded file ranges and pretend the asset is complete.

## M1L — migrate legacy finite byte APIs

Keep compatibility names such as `speakMp3(bytes)`, `speakOgg(bytes)`, and `speakWav(bytes)`, but route them into the same asset/playback engine. A modest Lua argument cap may remain; large CC files use `hq.playFile`.

Then remove the old whole-packet/whole-PCM finite decoder, duplicate status logic, and unbounded legacy finite decoder queue.

## M1M — HQ raw feed stabilization

- bounded raw feed buffer;
- one continuous raw session;
- truthful acceptance/readiness event distinct from native `speaker_audio_empty`;
- finite duration/seek remain unavailable;
- idle renderer cleanup;
- replacement by newer incompatible HQ playback.

## M1N — Minecraft/OpenAL integration cleanup

- use the appropriate CC-speaker sound category rather than arbitrary BLOCKS behavior;
- one logical volume stage plus Minecraft category/master scaling;
- F3+T/resource reload recovery;
- no stale channels;
- correct positional attenuation and VS2 movement.

## M1O — lifecycle hardening

Complete explicit block/Level/server/computer cleanup for peripheral caches, asset managers, transfer workers, decoder workers, client caches, and global maps. No static world retention across integrated-server restart.

## M1P — automated state-machine coverage

Add pure/component tests for replacement generation, server EOF, pause/resume/seek/loop, asset lifetime/refcount, range request bounds, interrupted/resumed transfer, stale generations, dynamic range entry/exit, dimension change, shared sync clocks, decoder cancellation, renderer stale-PCM drop, and lifecycle cleanup.

## M1Q — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify the packaged mod includes metadata, mixin config, JarJar dependencies, and the bundled ComputerCraft ROM `hqspeaker.lua` module.

## M1R — consolidated Minecraft acceptance

Only after the architecture above is coherent, run one batched runtime pass covering standard CC speaker compatibility; HQ raw feed; MP3/OGG/WAV; >8 MiB and 50-100+ MiB local files; pause/resume/seek/loop/EOF/volume; replacement; late range entry; leave/return; stop while away; dimension change; F3+T; speaker replacement; disconnect/rejoin; asset reuse; synchronized multispeaker playback; independent speaker desync; memory/network usage; and tick stalls.

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

Update all state/fact/architecture docs, remove obsolete P0 unresolved language and dead classes, decide whether the separate `hqspeaker:hq_speaker` block remains, document final Lua APIs, and resolve the repository MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch before public release.
