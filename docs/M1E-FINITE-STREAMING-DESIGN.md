# M1E+ finite streaming design contract

This document is the implementation contract for the simplified finite-media architecture agreed after M1D.

It describes the **target behavior**, not the current frozen M1D implementation. Historical M1D facts remain in `M1D-MEDIA-ANALYSIS.md` and `VERIFIED-FACTS.md`.

## Goal

Play large finite files through the normal ComputerCraft speaker without downloading the whole file to the client first and without turning HQ Speaker into a Java music player.

The server keeps the authoritative finite asset and playback timeline. A relevant client receives only the encoded bytes it currently needs, decodes them in bounded memory, and renders one mono positional source.

```text
ComputerCraft file
    -> server MediaAsset
    -> server-authoritative finite playback clock
    -> bounded encoded range requests
    -> temporary client RAM
    -> progressive decode/conversion
    -> bounded mono PCM queue
    -> one positional speaker renderer
```

## Final finite format scope

### Committed targets

- MP3 / MPEG Layer III
- common WAV

### Target after exact implementation proof

- normal native FLAC (`.flac`)

FLAC must not be advertised merely because a candidate library exists. Before advertisement it needs target-stack proof for:

- byte-based identification and metadata;
- bounded progressive decode;
- random rejoin/seek;
- malformed input failure;
- mono/stereo input handling;
- packaging on both NeoForge targets;
- Minecraft runtime playback.

### Not final targets

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- arbitrary compressed WAV codecs
- unusual professional/telephony WAV representations which substantially complicate the progressive converter

Frozen M1D may continue to analyze some of these until the replacement path is ready. Historical support does not create a permanent product requirement.

## Channel policy

The physical HQ speaker produces one **mono positional** source.

Accepted finite input:

- mono: render directly after decode/conversion;
- stereo: downmix to mono;
- more than two channels: reject.

The final WAV support set should be common/easy formats only. The initial intended set is:

- unsigned 8-bit PCM where appropriate for WAV;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float.

Exact WAV acceptance must match the converter actually implemented. Do not advertise widths/codecs merely because historical JavaSound accepted them.

## Server-authoritative semantics

A finite playback exists independently of any listener.

On successful `playPrepared` / `playFile`:

1. validate/retain the server asset;
2. increment the physical speaker's finite generation;
3. create/reset canonical playback state at position `0`;
4. set state `PLAYING`;
5. start the server clock immediately;
6. notify currently relevant clients, if any.

No nearby listener is required. No client READY/STARTED report starts semantic playback.

Canonical server state owns:

- generation;
- asset ID;
- format;
- duration;
- position;
- playing/paused/ended/error;
- loop;
- volume;
- later sync-clock membership.

Client transfer/decode/render state is not canonical playback state.

## Server states

The target semantic states are:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no server `LOADING` state for client buffering. The server already owns the complete finite asset.

`ERROR` means the **server playback itself** can no longer be maintained, for example an unrecoverable server-side asset/storage failure. One client's decoder/render failure does not globally set `ERROR`.

## EOF and looping

Before returning status or applying a control, update/finalize semantic time using the current server time.

For non-looping playback:

```text
position >= duration
    -> clamp position to duration
    -> state = ENDED
    -> stop canonical playback clock
    -> release playback's asset reference
```

For looping playback:

```text
position wraps modulo duration
```

Looping playback does not naturally enter `ENDED`.

For non-looping `seek(duration)`, transition to `ENDED` immediately rather than waiting one server tick.

For looping `seek(duration)`, wrap to position `0`.

## Client status reports

M1E removes renderer authority.

The following client reports must not mutate canonical server state:

- STARTED
- PAUSED
- RESUMED
- SEEKED
- ENDED

During the temporary old whole-file bridge, READY may remain only to ask the server for a fresh current-state snapshot. READY must not start the clock.

Client ERROR may remain as diagnostic telemetry. It must not end playback for other listeners.

M1F+ should remove protocol states which are no longer useful rather than preserving them indefinitely for architecture already deleted.

## Temporary M1E bridge behavior

M1E may initially retain the old full-file transfer only long enough to prove server authority before the M1F transfer rewrite.

Required behavior:

```text
server play begins at 0 immediately
    -> old client transfer runs
    -> client finally becomes ready
    -> client receives fresh canonical state
    -> client starts near CURRENT server position
```

If the transfer took eight seconds, the client must not begin audible output at `0:00` while server status says `0:08`.

If playback has already ended/stopped/replaced before client readiness, the stale generation must not start.

## M1F range request protocol

The final finite transfer is demand-driven.

Conceptual client request:

```text
FiniteRangeRequest
    sourceId
    generation
    assetId
    offset
    length
```

Conceptual server response:

```text
FiniteRangeData
    sourceId
    generation
    assetId
    offset
    bytes
```

Exact packet names may differ.

Generation belongs in request/response validation so old asynchronous work cannot feed a replacement song.

A seek does **not** need a new asset generation because cached/current encoded bytes are still the same asset. Generation represents playback replacement/lifetime, not control revision.

## Server range validation

Before scheduling IO, validate on the server thread:

1. source/speaker still has an active finite playback;
2. generation matches;
3. asset ID matches the playback;
4. player is in the correct dimension and currently relevant/in hearing range;
5. offset/length are valid and bounded;
6. request does not exceed per-player outstanding/rate limits;
7. playback still owns a live asset reference.

Then schedule the actual file read off-thread.

While the read is in flight, hold a safe asset lifetime reference.

Before sending the completed bytes, re-check at least:

- generation;
- active playback;
- player connection/dimension/relevance.

If the request became stale, discard the read result instead of sending obsolete audio data.

## Packet sizing

The existing 256 KiB finite chunk maximum is a valid starting point, not a guaranteed optimum.

The packet-size constant should be easy to tune. Benchmark at least typical encoded MP3/WAV/FLAC traffic with Minecraft packet compression enabled, because recompressing already-compressed MP3/FLAC may cost CPU without meaningful bandwidth savings.

Do not build application semantics around an exact packet size.

## No client disk cache

The final finite path does not save server songs to persistent client storage.

Do not implement:

- persistent `.part` encoded assets;
- completed client music files;
- LRU song cache;
- cache database;
- cross-restart partial resume;
- sparse cache files;
- hundreds of persistent range block files.

The server already has the authoritative asset. If the client later needs an old portion again, it requests it again.

## Client memory model

Client memory is bounded by active playback needs, not file size.

Maintain only:

- a bounded encoded-byte window/ring/range set required by active decoders;
- codec-specific short pre-roll/context;
- bounded decoded mono PCM ready for audio consumption.

A 500 MiB song must not imply 500 MiB client RAM or client disk.

Old encoded ranges may be dropped when:

- decoder has advanced beyond them;
- seek makes them irrelevant;
- playback is replaced/stopped;
- player leaves relevant range;
- client disconnects/world unloads.

## Threading

### Server thread

Allowed:

- playback state mutation;
- clock/EOF updates;
- range request validation;
- generation/relevance checks;
- scheduling IO;
- final send decision after async read.

Not allowed:

- large/slow asset file reads;
- blocking waiting for client/decoder readiness.

### Server IO workers

- bounded random asset reads;
- no direct semantic state mutation without hopping/rechecking appropriately.

### Client main/network thread

- validate packet/generation;
- enqueue/copy bounded received bytes into the streaming buffer abstraction;
- schedule renderer/control work.

Do not block it on decoder progress.

### Decoder workers

- MP3/FLAC decode;
- WAV conversion/downmix;
- waiting for missing-but-requested encoded data;
- pre-roll/discard after random seek/rejoin;
- produce bounded PCM.

### Sound/render thread

Consume already-ready PCM only. It must not block waiting for server ranges, disk IO, or a decoder worker.

## MP3 streaming contract

The exact currently bundled JLayer family can work incrementally only if temporary lack of bytes is distinguished from real EOF.

Rule:

```text
requested encoded byte not here yet
    -> decoder worker waits/refills
    -> NOT InputStream EOF
```

Do not return permanent EOF simply because the next network range has not arrived.

### MP3 seek/rejoin

M1D MP3 seek metadata comes from actual scanned frame offsets.

Layer III can use a bit reservoir from earlier frames, so seek/rejoin must start from earlier encoded frames, decode silently for pre-roll, then expose audible PCM around the current canonical server position.

The exact pre-roll policy should be covered by component tests and may be conservative initially.

## WAV streaming contract

For supported WAV layouts, playback does not require a complete local file.

Server analysis should eventually provide internal layout facts such as:

- audio-data byte offset;
- audio-data byte length;
- sample encoding;
- bits per sample;
- sample rate;
- source channel count;
- source frame size;
- endianness where relevant.

Then time-to-byte mapping is direct for uncompressed supported PCM/float data.

Stereo downmix must widen arithmetic before averaging to avoid overflow.

The exact converter defines the advertised WAV support list.

## FLAC contract

FLAC is wanted but deliberately not assumed complete.

Implementation must use normal native FLAC and provide:

- STREAMINFO parsing;
- total sample count/duration;
- mono/stereo acceptance only;
- seek/rejoin from bounded server ranges;
- frame synchronization and checksum/error handling appropriate to the chosen decoder;
- bounded decode memory;
- mono downmix for stereo;
- no Ogg container requirement.

If the chosen FLAC implementation cannot support the streaming/seek contract cleanly on the target stack, FLAC should remain unadvertised rather than weakening the finite architecture.

## Seek behavior

Lua seek updates server truth immediately.

Client behavior:

1. receive/observe new canonical position;
2. cancel/discard obsolete encoded requests and decoded PCM;
3. choose codec-appropriate encoded start/pre-roll point;
4. request bounded range(s);
5. decode/buffer;
6. obtain a fresh/current canonical position if enough time passed to matter;
7. begin audible output near that current position.

The server does not pause while the client catches up.

## Late listener behavior

If playback is already at `2:00` when a player enters range:

- do not stream `0:00 -> 2:00` first;
- send current playback state;
- request data near the codec-appropriate point for `2:00`;
- pre-roll/buffer;
- render current timeline.

## Underrun behavior

If the client cannot maintain enough encoded/decoded data:

- canonical server playback continues;
- the client may become locally silent;
- refill/reseek to current position;
- resume near current server time.

Never slow the global finite clock to match one listener.

The exact audible transition strategy (stop/recreate source versus a short bounded silence strategy) should be selected by runtime sound-quality testing, not by changing semantic playback rules.

## State synchronization

Do not compare raw `System.nanoTime()` values between server and client JVMs. Their origins are unrelated.

Initial implementation should use server-reported canonical position snapshots on:

- new relevant listener;
- play/replacement;
- pause/resume;
- seek;
- loop change;
- stop/end where relevant.

Periodic correction may be added if measured client/server drift justifies it. Measure drift before adding ping/clock prediction complexity.

## Resource limits

Protect the actual expensive resources rather than limiting legitimate ComputerCraft programs arbitrarily.

Useful server guards include:

- maximum range-request length;
- maximum outstanding range requests/bytes per Minecraft player;
- per-player encoded-byte rate limiting if needed;
- existing server asset/staging disk quotas;
- bounded server IO executor/queue.

Do not impose a low static "finite sessions per ComputerCraft computer" limit unless profiling finds a real session-object resource problem.

## Multispeaker direction

One server asset may back many playback sessions.

Later synchronized speakers may share:

- one canonical sync clock;
- active client's requested encoded windows;
- one decoder/PCM producer where timelines are identical.

They must **not** share one physical positional sound source. Each audible block keeps its own renderer position so Minecraft attenuation, direction, VS2 movement, and future SPR processing remain physically meaningful.

## Explicitly removed architecture

Do not reintroduce these as final behavior:

- wait for first READY client before starting server time;
- fixed historical recipient set;
- successful-renderer/anchor-client clock authority;
- renderer-defined canonical EOF;
- 15-second no-renderer playback failure;
- full client file required before first audio;
- persistent client song cache;
- client LRU music library;
- OGG/AIFF/AU requirements;
- surround/multichannel finite rendering from one physical speaker;
- whole decoded track in RAM.

## Milestone mapping

- **M1E:** server-authoritative finite timeline/state/EOF; old transfer only as temporary bridge.
- **M1F:** client-requested bounded encoded streaming; off-thread server IO; validation/rate/cancellation.
- **M1G:** progressive MP3/common-WAV/native-FLAC path; bounded encoded/PCM RAM; mono output.
- **M1H:** dynamic relevance, leave/re-enter, late join, underrun/rejoin and stale-generation hardening.
- **M1I:** multispeaker shared server assets/sync clocks and active in-memory sharing.

The former M2 "progressive finite playback" milestone is retired because progressive playback is required inside M1.
