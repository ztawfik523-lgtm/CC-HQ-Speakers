# M1G preparation — 2026-09-13

## Status

This document prepares M1G only. **M1G implementation has not started.**

Preparation branch:

`codex/m1g-preparation`

Preparation base:

`8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate remains:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI remains `34763362365`, with NeoForge 21.1.247 and 21.1.248 green.

The documentation-head verification run `34763711105` also completed green on both targets, and a fresh rerun on 2026-09-13 again completed both target jobs successfully.

Focused real-Minecraft M1F transport acceptance remains unrecorded. Do not turn CI into a runtime PASS.

## What M1G means for a ComputerCraft program

The Lua API should stay simple:

```text
hq.playFile(speaker, "/music/song.mp3")
        |
        v
server-owned finite playback clock
        |
        v
bounded encoded ranges already provided by M1F
        |
        v
M1G decodes only what this client needs
        |
        v
bounded mono PCM
        |
        v
one positional sound from that physical speaker
```

M1G is the milestone which turns the modern prepared-file path from a correct silent transport into audible MP3/common-WAV playback.

M1G must not bring back whole-song client files or whole-track decoded PCM.

## Starting facts rechecked from exact current source

### Modern M1F client is transport-only

`HQFiniteMediaClient` currently owns:

- the finite BEGIN descriptor;
- one bounded `FiniteRangeWindow`;
- server-selected encoded anchor/time;
- bounded range request pumping;
- terminal/cancellation state.

It has no modern decoder, PCM queue, or sound renderer. That is the clean M1G insertion point.

### Encoded window contract is ready for a decoder

`FiniteRangeWindow` now supports:

- unanchored startup;
- authoritative reset/re-anchor;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- exact contiguous-byte copy;
- forward `advanceTo(...)` while preserving useful unread prefetch;
- bounded memory independent of track duration.

A decoder should consume this API rather than invent a second encoded cache.

### Old finite decoders are evidence, not the new engine

`FileFiniteAudioStream` depends on a complete local file.

The finite path inside `HQAudioStream` decodes a complete finite payload into a retained `FiniteAudioTrack`, so decoded RAM still scales with track duration.

Both violate the modern M1G boundary and must not be repurposed as the new prepared-file engine.

### JLayer is already the exact packaged MP3 dependency

The project packages JLayer `1.0.1.4`. Existing live MP3 code already proves the project can use JLayer frame-by-frame:

```text
Bitstream(InputStream)
-> readFrame()
-> Decoder.decodeFrame(...)
-> SampleBuffer
-> mono signed PCM
```

This is useful implementation evidence. Do not copy the inherited live-stream lifecycle into finite playback wholesale.

### Temporary starvation cannot look like EOF

JLayer reads from an `InputStream`. For M1G, `NEED_DATA` from `FiniteRangeWindow` must not become `-1` to JLayer because `-1` means physical end-of-stream.

The prepared input boundary therefore needs a cancelable decoder-worker-facing encoded reader which can distinguish:

```text
DATA_AVAILABLE       -> return bytes
NEED_DATA            -> wait for refill, away from game/audio threads
TRUE_ASSET_EOF       -> physical EOF
CANCELLED_OR_STALE   -> abort/restart this local decode session
```

The Minecraft sound thread must never wait for network bytes.

### Current WAV analysis is broader than the final product target

The current analyzer accepts historical WAV shapes beyond the agreed M1G target, including more than two channels, A-law/mu-law, 64-bit float, and unusually wide integer PCM.

Current `MediaMetadata` also does not carry the complete direct-conversion layout needed by the final common-WAV engine. It has format, duration, sample rate, channels, bits per sample, and seek points, but not a final normalized sample representation/data-layout descriptor.

M1G must narrow active prepared/local WAV support to the implemented common subset rather than treating old JavaSound acceptance as a promise.

## M1G required component boundaries

These boundaries are requirements. Class names are not frozen.

### 1. Encoded-input bridge

A decoder-worker-facing source over the existing M1F window.

Requirements:

- reads from the current encoded cursor only;
- blocks/waits only on a decoder worker, never Minecraft/audio threads;
- wakes on newly accepted ranges;
- wakes and aborts on stop, replacement, re-anchor, disconnect, or local session cancellation;
- returns true EOF only at `TRUE_ASSET_EOF`;
- advances/discards M1F encoded data as the decoder consumes it;
- cannot retain encoded data beyond the bounded M1F window.

### 2. Decoder/converter session

One cancellable local finite decode session tied to source + asset + generation + a local decode epoch.

A new semantic seek can require a new decode epoch even when the server chooses the same coarse encoded anchor. Encoded-byte identity and decoder state are different things.

Requirements:

- no complete-file decode;
- no PCM sized by track duration;
- bounded worker/executor usage;
- stale worker output discarded after seek/replacement/cancel;
- client failure remains local/diagnostic and cannot end the server timeline.

### 3. Bounded PCM producer/consumer queue

The decoder writes mono PCM into a bounded queue. The renderer reads without blocking.

Requirements:

- hard memory cap independent of duration;
- backpressure from PCM queue to decoder;
- empty-but-not-terminal queue is underrun/starvation, not EOF;
- queue is dropped on seek/replacement/stop;
- sound thread never performs network, disk, or codec work.

### 4. Positional renderer

One physical speaker must still render as one mono positional source using the BLOCKS sound category/normal Minecraft attenuation semantics unless a later explicitly chosen design changes that.

M1G must prove actual audible positional output in Minecraft. M1N remains the later cleanup milestone for broader SoundEngine/OpenAL/category/gain hardening.

## MP3 plan

Core decoder target: exact packaged JLayer family unless new evidence justifies replacing it.

### Progressive decode

JLayer should consume the M1F encoded-input bridge frame-by-frame. Decoded samples are downmixed to mono and put into the bounded PCM queue.

### Seek and Layer III pre-roll

Canonical seek remains server-owned.

```text
server seek/current position T
-> STATE supplies encoded anchor A at/before T
-> local decoder restarts from A
-> decode earlier frames silently
-> discard PCM before audible target
-> renderer joins current server time
```

The server-selected coarse seek point is a decode anchor, not necessarily the exact audible sample.

If buffering/pre-roll takes time, do not pretend the server waited. The client should join the then-current server timeline.

### MP3 EOF and looping

A client decoder reaching physical asset EOF does not become canonical EOF authority. M1E remains server-owned.

Loop handling must remain synchronized to server semantic state rather than allowing an old client-only loop clock to take ownership.

## Common WAV plan

Final M1G prepared/local WAV target remains:

- mono or stereo only;
- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float;
- little-endian RIFF/WAVE common layouts;
- stereo downmixed to mono;
- >2 channels rejected;
- A-law, mu-law, compressed/telephony WAV, 64-bit float, and unusual widths rejected.

Conversion must be progressive from bounded encoded ranges. Do not hand the entire WAV to JavaSound.

## Decision gates — owner choice required before M1G code

No implementation should silently choose these.

### Decision A — how PCM reaches Minecraft

**A1. Minecraft `AudioStream` / normal SoundManager route**

The bounded PCM queue is exposed as a custom `AudioStream`, and a positional sound is played through Minecraft's normal `SoundManager` path.

Pros:

- fits the existing project renderer shape;
- native BLOCKS category and attenuation path;
- less direct OpenAL resource ownership;
- keeps M1G smaller and leaves deeper OpenAL cleanup to M1N.

Cons:

- less direct control over queued OpenAL buffers;
- underrun behavior must be represented carefully so empty PCM does not look like final EOF;
- pause/resume still needs safe channel access.

**A2. Direct Channel/OpenAL buffer queue**

M1G owns low-level streaming buffers/channel lifecycle more directly.

Pros:

- precise buffer/underrun control;
- explicit queue timing and refill behavior.

Cons:

- substantially more OpenAL/reload/resource-lifetime complexity;
- pulls work forward from M1N;
- greater risk of bypassing Minecraft lifecycle/category behavior;
- tighter coupling to future Sound Physics Remastered integration details.

### Decision B — where final WAV layout is resolved

**B1. Server-analyzed layout carried to the client**

Extend/narrow server analysis to produce normalized common-WAV layout facts and carry them in the finite descriptor/state. This likely requires a network schema/protocol update.

Pros:

- one trusted container parse on the server;
- invalid/unsupported WAV rejected before playback;
- client converter can seek directly from known layout;
- avoids duplicating WAV header parsing on every listener.

Cons:

- changes wire metadata/protocol;
- more server metadata schema to maintain.

**B2. Client progressively parses the WAV header from M1F ranges**

Keep the existing wire descriptor smaller and let each client resolve the WAV layout from encoded bytes.

Pros:

- smaller server/network metadata change;
- converter owns all decode-side container knowledge.

Cons:

- duplicate parsing on every listener;
- more malformed-container surface on the client;
- direct seek cannot be fully planned until the needed header/chunks have arrived;
- server and client can disagree unless validation is deliberately kept aligned.

### Decision C — finite PCM sample-rate policy

**C1. Preserve source sample rate**

MP3/WAV PCM is rendered at the source rate.

Pros:

- no resampler in M1G;
- lower CPU/complexity;
- JLayer and common WAV naturally produce source-rate samples.

Cons:

- renderer format differs by file;
- later shared-decode/sync optimization may need more format bookkeeping.

**C2. Normalize finite PCM to 48 kHz**

All modern finite PCM is resampled to 48 kHz before rendering.

Pros:

- one fixed finite PCM rate;
- simpler queue-duration math and later sharing assumptions.

Cons:

- requires a real quality resampler;
- additional CPU, latency, testing, and fidelity risk;
- expands M1G scope significantly.

## Control/state behavior M1G must preserve

- server PLAYING/PAUSED/ENDED/ERROR remains canonical;
- client decoder/renderer cannot canonically pause/end playback;
- pause freezes local audible output but not transport semantics beyond what server state says;
- resume follows server position;
- seek clears stale decoder state + PCM even if encoded anchor happens to be unchanged;
- volume affects the physical renderer without changing decode identity;
- loop remains a server semantic;
- stop/replacement cancels local encoded waits, decoder work, PCM, and renderer promptly.

## M1G / M1H boundary

M1G owns the core audible engine for an already-known relevant finite session.

M1H still owns the complete listener discovery/recovery lifecycle:

- player enters range after playback already started;
- proactive out-of-range resource cleanup;
- return/rejoin at current server time;
- dimension/world/resource reload recovery;
- robust underrun rejoin policy;
- final VS2 moving-speaker listener lifecycle.

M1G should make local cancellation/restart safe enough for M1H to build on, but it should not absorb M1H discovery policy.

## M1G deterministic acceptance plan

Before calling M1G source-complete, add deterministic/component proof for at least:

1. encoded-input starvation waits and later resumes without EOF;
2. true asset EOF is distinct and delivered exactly once;
3. cancel/re-anchor wakes a waiting decoder and invalidates stale output;
4. progressive MP3 frame decode produces non-silent bounded mono PCM without full-file buffering;
5. MP3 decode continues across multiple M1F window slides;
6. MP3 seek/pre-roll restarts decoder state and suppresses pre-target PCM;
7. each supported WAV representation converts known sample vectors correctly;
8. stereo downmix is correct and >2 channels are rejected;
9. malformed/unsupported WAV is rejected without unbounded work;
10. PCM queue hard cap/backpressure;
11. renderer-facing reads never block on encoded input/codec work;
12. temporary PCM starvation is not renderer EOF;
13. seek/replace/stop clears stale PCM/renderer state;
14. no modern complete-song client file and no whole-track PCM object is reintroduced.

## Focused Minecraft M1G acceptance plan

Real runtime proof is required before claiming audible M1G PASS. At minimum record:

- MP3 starts audibly after bounded prebuffer, before any whole-track transfer;
- supported common WAV plays audibly;
- long file memory remains bounded;
- pause/resume;
- seek forward/backward;
- looping;
- stop/replacement;
- temporary starvation/refill does not become permanent EOF;
- positional attenuation from the physical `computercraft:speaker`;
- standard CC:T speaker behavior remains intact;
- no modern client song `.part/.media` file appears.

Record exact commit/JAR/hash/NeoForge version/fixture facts and relevant logs. CI alone is not runtime proof.

## Out of scope for M1G

Do not pull in unless required for core correctness:

- FLAC (M1I);
- full dynamic listener lifecycle (M1H);
- multispeaker synchronization redesign (M1J/K);
- broad legacy finite API migration/removal (M1L);
- RAW redesign (M1M);
- broad OpenAL/category/gain cleanup (M1N);
- Sound Physics Remastered integration (M2);
- live/HLS/TS rebuild (M3);
- custom HQ block removal or license provenance cleanup (M4/release cleanup).

## Stale-document correction

Two old `ARCHITECTURE.md` statements are superseded by final M1F evidence:

- the active M1F maximum range is 128 KiB, not the older 256 KiB starting-point wording;
- the modern prepared path no longer has fixed whole-file server push/client `.part/.media` bridging. That was removed by M1F.

Current M1F finalization/current-state/source override those stale transitional paragraphs until the architecture document is fully reconciled.
