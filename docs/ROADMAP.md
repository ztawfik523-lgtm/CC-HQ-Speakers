# Roadmap

## Product rule

Build a better **programmable ComputerCraft speaker peripheral**. Java exposes truthful audio capabilities; Lua decides whether they are used for music, alarms, speech, notifications, soundboards, PA systems, ambience, or anything else.

Technical source categories remain:

- standard CC:T speaker behavior (`playNote`, `playSound`, `playAudio`, `stop`);
- HQ raw/feed PCM;
- finite encoded media with a truthful server-owned timeline;
- live/open-ended network streams, later.

Do not add application roles or a Java playlist manager.

## Reference points

- immutable reviewed M1 reference: `fba84a33a94d451af09b983bcb04416c97ff64cf`;
- frozen local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc` on `codex/m1a-local-finite-media`;
- completed M0.5 cleanup/preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- M1A compatibility/output branch: `codex/m1a-compat-output`;
- completed M1B media-asset storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- verified M1C + configurable-storage base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen source/test/CI M1D media-analysis head: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- active implementation branch: `codex/m1e-server-authoritative-finite`.

The old finite prototype proved writable CC staging, bounded chunk packets, client decode, and server/client integration. Its fixed-recipient set, whole-file-before-render behavior, client disk-file requirement, renderer authority, and observation timeout are prototypes scheduled for replacement, not architecture to polish.

## Settled architecture decisions

1. **No Java playlist.** A new incompatible HQ continuous playback replaces the prior HQ continuous playback. Lua owns sequencing/queue policy. Repeated chunks of one raw feed remain one feed. Standard CC:T methods retain their own native compatibility semantics.
2. **No permanent listener ownership list.** The server owns playback state; clients dynamically create/destroy renderers according to current relevance and receive current state when they become relevant again.
3. **Finite media is an asset separate from playback and physical speaker.** One encoded server asset may be referenced by several speaker playbacks.
4. **The server starts finite time immediately.** Successful finite `play` starts the canonical server clock even with zero nearby listeners. Transfer/decoder readiness never gates semantic playback.
5. **Server owns finite state and EOF.** Client renderers may report diagnostics but do not define canonical start, position, pause/resume, seek, or end.
6. **Finite files are streamed progressively from server to client.** Clients request bounded encoded ranges around the current playback need. Full client-side file download is not a prerequisite for playback.
7. **No persistent client song cache.** The final design uses bounded temporary RAM for encoded/decode buffers. It does not maintain a client `.part` library, persistent LRU, cross-restart song cache, or partial-download database.
8. **Finite product formats are intentionally narrow.** Target MP3, common/easy WAV, and normal native FLAC once FLAC's exact implementation is proven. OGG Vorbis, AIFF/AIF, and AU/SND are not final product targets.
9. **One physical speaker renders mono positional audio.** Mono input stays mono; stereo input is downmixed to mono; more-than-stereo input is rejected.
10. **Seek and late join remain supported without caching.** The client discards obsolete buffers and requests a fresh encoded window around the new/current server position.
11. **Multispeaker playback shares server asset/timeline, not physical source position.** Active clients may share in-memory transfer/decode work where useful, but each audible speaker keeps its own positional renderer.
12. **Large local finite playback is the priority.** Internet MP3/HLS/TS work is deferred until finite/local media is solid.
13. **Storage safety limits are server configuration, not speaker semantics.** HQ Speaker only limits disk space allocated by the mod itself. ComputerCraft filesystem limits remain ComputerCraft/server policy.

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

- old `*All` / `*At` methods remain legacy until the multispeaker migration;
- range leave/re-enter rendering remains later M1 work;
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

**Status: frozen source/unit-test/CI complete at `4a2cd5de96228fc091226c7e72fb669b82be258c`; Minecraft runtime acceptance pending.**

M1D is historical implementation truth and is not being rewritten retroactively. It implemented/hardened:

- byte-based finite format identification;
- immutable committed-byte analysis;
- bounded 64 KiB analysis window;
- bounded MP3/OGG seek metadata;
- MP3 Layer III frame scan and duration;
- OGG Vorbis analysis;
- WAV/AIFF/AU JavaSound-parity analysis;
- truthful server-derived prepared metadata;
- analyzer and storage-limit tests.

M1D therefore still contains support/evidence for OGG/AIFF/AU. The final product scope is now narrower: MP3 + common WAV + native FLAC target. Later cleanup must narrow analyzer/advertisement/runtime tests only when the replacement streaming decoder path is ready, so historical M1D evidence remains reproducible until then.

Current MP3 duration is encoded-frame duration. Gapless encoder delay/padding correction remains optional later accuracy work.

## M1E — server-authoritative finite playback

**Active milestone.**

The server owns generation, state, position, duration, pause/resume, seek, loop, volume, and EOF.

Implement:

- successful play starts the canonical server clock immediately at position 0;
- no server `LOADING` state for client transfer readiness;
- remove `successfulRenderers` / canonical `observed` authority;
- remove the 15-second no-renderer failure;
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED reports no longer mutate canonical playback;
- client decode/render failures are diagnostic and local, not global playback failures;
- natural non-looping EOF is determined from server duration/clock;
- looping wraps server position rather than waiting for a renderer;
- non-looping seek to exact duration transitions immediately to ENDED;
- status/control paths advance/finalize semantic time before reporting state, so status cannot remain PLAYING past known EOF;
- playback progresses with no listeners;
- temporary old whole-file transport, while it still exists, starts a READY client at the **current** server position rather than position 0.

## M1F — bounded client-requested finite streaming

Replace fixed-recipient whole-file server push with bounded range requests.

Implement:

- client requests `source + generation + assetId + offset + bounded length`;
- server validates active playback, generation, asset, player dimension/range, offset/length, and rate/outstanding limits;
- server performs asset reads on bounded IO workers, not the server tick;
- server holds an asset lifetime reference while asynchronous reads are active;
- before sending completed IO, re-check generation/relevance so stale work is discarded;
- response packets remain bounded; current 256 KiB is a starting point and should be benchmarked against 64/128 KiB under packet compression;
- client requests additional data only as its bounded RAM window needs it;
- stop/seek/leave-range cancels future demand rather than draining an already-queued whole file;
- no persistent client disk asset file is created by the final path.

This milestone must support arbitrary bounded encoded ranges because seek and late join need fresh data near the current server position.

## M1G — progressive MP3/WAV/FLAC decode and bounded RAM buffering

There is no persistent client-cache milestone anymore.

Build the real finite render pipeline:

```text
bounded encoded RAM window
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL renderer
```

MP3:

- use the exact shipped JLayer path or another proven Java decoder;
- temporary missing encoded bytes are **not EOF**;
- decoder worker waits/refills when required bytes have not arrived;
- random seek/rejoin begins from earlier MP3 frame metadata and silently pre-rolls to rebuild Layer III bit-reservoir state;
- never block the Minecraft sound thread waiting for network or disk.

WAV:

- narrow final support to common/easy sample formats only;
- mono stays mono; stereo downmixes to mono;
- reject >2 channels;
- use server-derived audio-data layout for direct bounded conversion/seek;
- do not preserve unusual JavaSound-only WAV variants merely for historical compatibility.

FLAC:

- support normal native `.flac` only after an exact decoder/analyzer/index path is proven;
- require bounded progressive decoding, random rejoin/seek, malformed-input handling, and packaging tests before advertising FLAC;
- do not add Ogg-FLAC.

General:

- bounded prebuffer before initial audible output;
- small bounded decoded PCM queue;
- seek discards obsolete encoded/PCM buffers and requests the new window;
- natural decoder EOF is diagnostic; canonical EOF remains server-owned;
- if a client underruns, server time continues and the client later rejoins the current position.

## M1H — dynamic range/state rendering

Clients render current server state rather than owning playback:

- state snapshot when a speaker becomes relevant;
- update snapshots on playback/control changes plus measured periodic correction if runtime tests show it useful;
- leaving range stops renderer and encoded requests only;
- returning while playback continues streams from current position;
- returning after stop stays silent;
- dimension/chunk/speaker removal is safe;
- stale generation work cannot restart old sound;
- VS2 position updates remain supported.

No historical recipient set is required. This milestone also closes the known case where an inherited range-local HQ stop can be missed by a client which already left speaker range.

Do not compare server/client `System.nanoTime()` values: their origins are JVM-local. Measure actual playback drift first before adding any latency compensation.

## M1I — multispeaker shared assets and sync clocks

One server asset may feed several physical speakers.

- synchronized playbacks reference one sync-clock ID;
- each physical speaker retains independent programmable state and may later leave the shared clock;
- no expected-group-size/expected-tap barrier;
- active client renderers may share in-memory encoded requests/decode work when they use the same asset/timeline;
- no persistent client asset cache is required;
- one mono positional renderer per audible physical speaker preserves direction, attenuation, occlusion, VS2 movement, and future SPR processing;
- replace/reroute inherited `*All` / `*At` helpers so they no longer bypass current ownership/state architecture.

## M1J — renderer fan-out optimization

For playbacks using the same asset and sync clock, share bounded in-memory decode/PCM production where beneficial while keeping independent positional sources. A lagging renderer may discard stale decoded PCM and rejoin the current timeline. Encoded ranges are re-requestable from the server and are never treated as permanently cached client assets.

## M1K — migrate legacy finite byte APIs

Keep only compatibility frontends which make sense for the narrowed format scope. `speakMp3(bytes)` and `speakWav(bytes)` may route into the same server asset/playback engine. Large CC files use `hq.playFile`.

Legacy OGG-specific APIs may be deprecated/removed instead of forcing Vorbis into the final architecture.

Then remove the old whole-packet/whole-PCM finite decoder, duplicate status logic, and legacy finite decoder queue.

## M1L — HQ raw feed finalization

M1A establishes normal single-speaker RAW ownership/backpressure/idle-release. After shared renderer/range architecture exists:

- use the common dynamic range/state renderer lifecycle where applicable;
- preserve `hqspeaker_audio_empty`;
- keep finite duration/seek unavailable;
- add pause only if a coherent producer/feed contract justifies it;
- migrate old multi-speaker RAW helpers away from expected-group behavior.

## M1M — Minecraft/OpenAL integration cleanup

- use the appropriate CC-speaker sound category rather than arbitrary BLOCKS behavior;
- one logical volume stage plus Minecraft category/master scaling;
- finite output is mono positional audio;
- F3+T/resource reload recovery;
- no stale channels;
- correct positional attenuation and VS2 movement.

## M1N — lifecycle hardening

Complete explicit cleanup for peripheral maps, asset managers, transfer workers, decoder workers, bounded client RAM buffers, and global maps. No static world retention across integrated-server restart. No persistent client song cache exists to maintain or migrate.

## M1O — automated state-machine and streaming coverage

Add pure/component tests for replacement generation, server EOF, pause/resume/seek/loop, asset lifetime/refcount, range request bounds, stale generations, cancellation, dynamic range entry/exit, dimension change, shared sync clocks, decoder cancellation, MP3 temporary-starvation-vs-EOF behavior, MP3 seek pre-roll, WAV conversion/downmix, FLAC once added, renderer underrun/rejoin, and lifecycle cleanup.

## M1P — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify packaged metadata, mixins, decoder dependencies, and the bundled ComputerCraft ROM `hqspeaker.lua` module.

## M1Q — consolidated Minecraft acceptance

Only after the architecture above is coherent, run one batched runtime pass covering:

- standard CC speaker compatibility;
- HQ raw feed;
- MP3;
- common supported WAV variants;
- native FLAC if/when its milestone is implemented;
- >8 MiB and 50-100+ MiB local finite files;
- progressive start before full transfer;
- pause/resume/seek/loop/EOF/volume;
- replacement;
- late range entry;
- leave/return;
- stop while away;
- dimension change;
- F3+T;
- speaker replacement;
- disconnect/rejoin;
- synchronized multispeaker playback;
- independent speaker desync;
- mono downmix;
- bounded RAM/network usage;
- server/client tick and sound-thread stall checks.

OGG/AIFF/AU are not required final acceptance formats even though frozen M1D historically analyzed them.

## M2 — progressive finite milestone removed

The old M2 concept of "download full finite asset in M1, make it progressive later" is obsolete. Progressive server-to-client finite streaming is now part of M1F/M1G and is required for the target large-file architecture.

## M3 — Sound Physics Remastered

Integrate the frozen SPR compatibility work only after positional renderer lifecycle is stable. Shared in-memory media/PCM work must not collapse physical source positions. Test per-speaker occlusion/reverb and many-source performance. Companion mod vs integrated optional module remains an explicit packaging choice.

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

Do not force finite server-asset semantics onto live streams merely because both deliver bytes progressively.

## M5 — release cleanup

Update state/fact/architecture docs, remove obsolete P0 language and dead classes, remove obsolete OGG/AIFF/AU advertised finite surfaces after the narrowed engine replaces them, decide whether the separate `hqspeaker:hq_speaker` block remains, document final Lua APIs, and resolve the repository MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch before public release.
