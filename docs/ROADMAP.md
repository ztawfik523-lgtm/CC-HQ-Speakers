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

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`;
- frozen local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- active branch: `codex/m1e-server-authoritative-finite`.

Detailed completed-milestone evidence lives in the milestone docs and `VERIFIED-FACTS.md`. This file defines what to build next.

## Settled finite architecture

1. A finite file is a **server asset**, separate from playback and from a physical speaker.
2. Successful finite `play` starts the **server clock immediately**, even with zero listeners.
3. The server owns generation, state, duration, position, pause/resume, seek, loop, volume, and EOF.
4. A client renderer never becomes canonical playback authority.
5. Finite files are **streamed progressively from server to client** in bounded encoded ranges. Full client-file download is not required.
6. There is **no persistent client song cache**. Clients keep only bounded temporary encoded/decoded RAM needed by active playback.
7. Seek and late join fetch a fresh bounded encoded window around the server's current/requested position.
8. A slow client may go silent/refill/rejoin; it never slows the canonical server clock.
9. One physical speaker renders **mono positional audio**. Mono stays mono; stereo is downmixed; >2 channels are rejected.
10. Core finite formats are **MP3 and common WAV**. Native FLAC is a wanted extension, but it must prove its analyzer/decoder/seek/package path before advertisement.
11. OGG Vorbis, AIFF/AIF, AU/SND, Ogg-FLAC, exotic WAV encodings, and surround finite input are not final product requirements.
12. Standard CC:T speaker behavior remains delegated to the real CC:T `SpeakerPeripheral`.
13. Large local finite media is the priority. Live Internet streams and SPR remain later milestones.

## Completed foundation

### M0.5 — cleanup/redesign preparation

Completed at `ad38412a2173f849a0fc8e867030da8a78965c9c`.

### M1A — CC:T compatibility and output ownership

Source/CI implemented. Normal single-speaker standard methods delegate to CC:T; HQ continuous ownership and RAW backpressure are bounded. Minecraft runtime acceptance remains pending.

### M1B — reusable server media assets

Completed at `40091ee32f412c1208e9016fca288b8d4f902dfa`. Server-side encoded assets are UUID-addressed, disk-backed, reference-counted, quota-controlled, and seekably readable.

### M1C — ComputerCraft local-file import/config

Verified base `33bcc6e04a2734500b7b15b84bee884562539216`. ComputerCraft files stage/import into reusable server assets. HQ-owned storage limits are configurable; CC filesystem capacity is not modified.

### M1D — server media analysis

Frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`, final CI run `34635484316` green on NeoForge 21.1.247 and 21.1.248. M1D historically analyzes MP3/OGG/WAV/AIFF/AU and records truthful server metadata. That historical format surface does **not** constrain the narrowed final product scope.

## M1E — server-authoritative finite state + state snapshots

**Active milestone.**

Goal: make finite playback semantically correct before replacing transport.

Implement:

- server semantic states `PLAYING`, `PAUSED`, `ENDED`, `ERROR`; no server `LOADING` state for client buffering;
- `playPrepared` starts canonical time immediately at position 0;
- playback advances with zero listeners;
- remove `successfulRenderers`, canonical `observed`, and the 15-second no-renderer failure;
- client `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, and `ENDED` reports never rewrite canonical state;
- client decode/render errors remain diagnostic/local;
- server duration/clock determines natural EOF;
- non-looping `seek(duration)` ends immediately; looping `seek(duration)` wraps to 0;
- status/control paths finalize elapsed EOF before returning/applying state;
- close/cancel the temporary old transfer before releasing its playback asset when canonical playback ends;
- add a real **server -> client finite state snapshot** packet carrying at least source, generation, asset ID, format, encoded size, duration, canonical position/state, loop, volume, and speaker position;
- use that snapshot for play/control changes and the temporary READY bridge;
- while old whole-file transfer remains, a READY client starts near the **current server position**, never at 0 merely because its download just finished.

Tests belong in M1E itself: clock EOF, looping, exact-end seek, no-listener progression, client-status non-authority, replacement generation, and asset lifetime at early EOF.

## M1F — demand-driven finite transport

Goal: delete fixed-recipient whole-file push without yet depending on the final decoder.

Implement:

- client -> server range request containing source, generation, asset ID, offset, bounded length;
- server -> client range data containing source, generation, asset ID, offset, bytes;
- bounded response packets; 256 KiB remains a starting point, not a frozen optimum;
- server validation of active playback, generation, asset, dimension/range relevance, bounds, and outstanding/rate limits;
- bounded server IO executor; no asset reads on the server tick;
- hold a safe asset reference while asynchronous IO is in flight;
- re-check generation/player relevance before sending completed asynchronous reads;
- bounded temporary client encoded buffers only; no `.part`, completed client song file, LRU, persistent resume, or sparse cache;
- stop/seek/leave cancels future demand; stale returned ranges are discarded;
- range requests work at arbitrary encoded offsets so seek/rejoin can jump rather than downloading from byte 0.

### Stream/seek descriptor rule

Do **not** require clients to own the server's whole seek index. State/stream setup may include a codec-appropriate seek anchor such as:

- anchor encoded byte offset;
- anchor media time;
- format/layout facts needed by the decoder.

For a late join or seek, the server chooses an anchor at/before the desired canonical position. The client requests forward from that anchor and performs codec-specific pre-roll. This keeps authoritative seek metadata on the server while retaining a generic byte-range transport.

M1F tests cover request bounds, stale generation, relevance, cancellation, in-flight asset lifetime, async completion after replacement, packet sizing, and bounded outstanding work.

## M1G — core progressive finite engine: MP3 + common WAV

Goal: finish the real single-speaker progressive decoder path without letting FLAC delay it.

Pipeline:

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

### MP3

- use the exact shipped JLayer path unless a better Java decoder is proven;
- temporary missing bytes are **not EOF**; decoder workers wait/refill instead of reporting EOF;
- server MP3 frame metadata provides seek/rejoin anchors;
- start earlier than the audible target and decode/discard pre-roll to rebuild Layer III bit-reservoir state;
- never block the Minecraft sound thread on network/decoder progress.

### WAV

Narrow the final prepared/advertised WAV contract to the converter we actually implement:

- mono/stereo only;
- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float;
- reject >2 channels and unusual/compressed/telephony WAV encodings.

Extend server metadata with the internal PCM layout needed for direct time-to-byte mapping: data offset/length, encoding, bits/sample, frame size, sample rate, and channel count. Stereo downmixes safely to mono.

### Format-scope cleanup in M1G

Once MP3/WAV progressive playback replaces the historical client path:

- prepared/local final advertisement becomes MP3 + supported common WAV;
- OGG/AIFF/AU stop being required prepared/local formats on the active branch;
- keep frozen M1D branch/docs as historical evidence rather than preserving obsolete formats in the new engine.

M1G tests cover starvation-vs-EOF, bounded RAM, MP3 pre-roll/rejoin, WAV conversion/downmix, malformed input, seek, pause/resume/loop interaction, decoder cancellation, and sound-thread non-blocking behavior.

## M1H — dynamic listener lifecycle and recovery

Goal: make the single-speaker streamed engine behave correctly as players move.

Implement:

- discover/notify newly relevant listeners with current state snapshot;
- entering range begins from current server position, not from file start;
- leaving range destroys/parks local renderer, cancels demand, and frees temporary buffers;
- returning while active rejoins current time;
- returning after stop/end stays silent;
- dimension change, chunk/speaker removal, disconnect, and resource reload are safe;
- stale generations/ranges cannot restart old sound;
- underrun causes local refill/rejoin, never canonical pause;
- VS2 position updates remain supported;
- measure real drift before adding periodic correction or latency prediction.

M1H closes the known stale-audio case where a client leaves before a later range-local stop packet.

## M1I — native FLAC extension, gated

FLAC is wanted, but **M1 completion for MP3/WAV does not wait for it**.

Only advertise normal native `.flac` after proving:

- byte-based FLAC identification and STREAMINFO metadata;
- duration/sample facts and mono/stereo validation;
- bounded progressive decode from server ranges;
- codec-appropriate seek/rejoin anchors;
- malformed-input/checksum failure behavior;
- bounded RAM and cancellation;
- mono downmix for stereo;
- packaged dependency/runtime behavior on NeoForge 21.1.247 and 21.1.248;
- Minecraft runtime playback.

Do not add Ogg-FLAC. If a clean decoder/seek path is not found, leave FLAC unadvertised rather than weakening the finite architecture.

## M1J — multispeaker shared clocks and correct physical renderers

Goal: functional multispeaker behavior first, optimization second.

Implement:

- one server asset may back many playbacks;
- synchronized playbacks reference a shared server sync-clock ID;
- no expected-global-member/expected-tap barrier;
- each physical speaker remains independently programmable and may leave the shared clock;
- each audible block gets its own mono positional renderer;
- migrate/reroute inherited `*All` / `*At` helpers so they no longer bypass current ownership/state rules.

## M1K — active-session sharing/fan-out optimization

Only after M1J is correct:

- coalesce duplicate encoded requests for active clients hearing the same asset/timeline where useful;
- share one decoder/PCM producer for identical active timelines where safe;
- keep independent positional output buffers/sources;
- a lagging renderer may drop stale decoded PCM and rejoin current time;
- do not introduce persistent client caching to optimize this.

## M1L — legacy finite API migration/removal

- route useful compatibility frontends such as `speakMp3(bytes)` / `speakWav(bytes)` into the new asset engine where sensible;
- large files remain `hq.playFile`/prepared-asset territory;
- deprecate/remove legacy OGG-specific finite APIs instead of adding Vorbis back to the final engine;
- remove old whole-packet/whole-PCM finite decoder, duplicate finite state logic, and obsolete begin/chunk/end/status code after all callers are migrated.

## M1M — HQ raw/feed finalization

- preserve `hqspeaker_audio_empty` and bounded RAW backpressure;
- keep finite duration/seek unavailable for RAW;
- use the common dynamic renderer lifecycle where applicable;
- migrate legacy multi-speaker RAW helpers away from expected-group behavior.

## M1N — Minecraft/OpenAL integration cleanup

- use the appropriate speaker sound category;
- one logical gain stage plus Minecraft category/master scaling;
- finite output remains mono positional;
- F3+T/resource reload recovery;
- no stale channels;
- correct attenuation and VS2 movement.

## M1O — lifecycle/performance hardening

Tests are **not deferred here**; each milestone ships its own deterministic tests. M1O is the final stress/hardening sweep:

- bounded executors/queues under many players/speakers;
- cancellation storms and seek spam;
- repeated replacement/stop/rejoin;
- integrated-server restart/world unload cleanup;
- memory/network/tick profiling;
- packet-size benchmark (for example 64/128/256 KiB with compression);
- no persistent client song state left behind.

## M1P — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify packaged metadata, mixins, decoder dependencies, and bundled ComputerCraft ROM `hqspeaker.lua`.

## M1Q — consolidated Minecraft acceptance

Run one batched final M1 pass covering:

- standard CC:T speaker compatibility;
- HQ raw feed;
- MP3;
- all supported common WAV variants;
- FLAC only if M1I passed;
- >8 MiB and 50–100+ MiB finite files;
- audible start before full file transfer;
- pause/resume/seek/loop/EOF/volume;
- replacement;
- late range entry and leave/return;
- stop/end while away;
- dimension change;
- F3+T/resource reload;
- disconnect/rejoin;
- synchronized and independently controlled multispeaker playback;
- mono downmix;
- bounded RAM/network use;
- server/client tick and sound-thread stall checks.

OGG/AIFF/AU are not required final acceptance formats.

## M2 — Sound Physics Remastered

Integrate the frozen SPR compatibility work only after finite positional renderer lifecycle is stable. Shared in-memory decode must never collapse several physical speakers into one OpenAL source. Test per-speaker occlusion/reverb and many-source performance. Companion-mod vs integrated optional-module packaging remains an explicit later choice.

## M3 — live/open-ended streams

Only after finite/local media is stable:

- live MP3, HLS, TS;
- no fake duration/seek;
- pause/resume reconnects to current live point;
- one gain stage;
- correct HLS media-sequence progression;
- incremental TS;
- unsupported codecs fail explicitly;
- bounded HTTP/resources;
- dynamic renderer behavior without expected-tap deadlocks.

Finite server-asset semantics must not be forced onto genuinely live sources.

## M4 — release cleanup

- final docs/API surface;
- remove obsolete prototype/dead classes and old P0 language;
- remove obsolete OGG/AIFF/AU advertised finite surfaces once the new engine owns all finite playback;
- decide whether the separate `hqspeaker:hq_speaker` block remains;
- resolve the repository MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch before public release.
