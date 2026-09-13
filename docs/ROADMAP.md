# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-13

M1E server-authoritative finite playback is complete at the source/test/CI level after final hardening at:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Exact final code CI `34757923455` passed both NeoForge targets.

The final focused M1E Minecraft acceptance was explicitly skipped by the owner and remains unrecorded; do not claim a runtime PASS.

M1F range transport is implemented but still has its own completion/acceptance work. M1G has not started.

## Foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` stay delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Frozen server analysis proves format/duration/seek facts without whole-track PCM decode. Historical analyzer format breadth does not define final product support.

## M1E — server-authoritative finite timeline

**Complete at source/test/CI level.**

Final code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Implemented/finalized behavior:

- successful prepared play starts canonical server time immediately;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position/duration/pause/resume/seek/loop/volume/natural EOF;
- non-loop exact-duration seek -> ENDED;
- loop exact-duration seek -> zero and remain active;
- server ERROR freezes position;
- client READY is state refresh only;
- client ERROR is diagnostic only;
- start construction completes before canonical installation;
- client projection is best-effort and per-recipient isolated;
- MediaAsset final-release failures transfer to retry-safe ownership;
- server shutdown drains range IO before asset-store cleanup.

Deterministic coverage now includes the production playback state machine, core clock, projection failure containment, and deferred-release ownership.

Final focused Minecraft acceptance remains **skipped / no recorded PASS** by owner decision.

## M1F — demand-driven finite transport

**Architecture implemented; acceptance/completion still provisional.**

Implemented and retained:

- protocol v5 client range request/data;
- server-selected encoded anchors in STATE;
- bounded packet/window/outstanding limits;
- bounded off-thread server reads;
- in-flight MediaAsset retain/retry-safe release;
- current generation/asset/player/relevance recheck before send;
- arbitrary encoded re-anchors;
- no modern client whole-song `.part/.media` bridge;
- old modern finite CHUNK/END packets removed;
- project-prototype `audioPlayStaged()` removed;
- transport availability distinguishes missing data from real EOF.

Remaining M1F completion work:

- add a true sliding consume/discard encoded-window operation which preserves useful unread prefetched bytes;
- close the remaining deterministic component matrix for request identity/relevance/stale completion, shutdown integration, packet bounds/codecs, and client BEGIN/STATE anchor gating;
- record focused Minecraft range-transport acceptance if/when runtime proof is wanted.

M1E is no longer part of this completion gate.

## M1G — core progressive finite engine: MP3 + common WAV

**Not started.**

Goal:

```text
M1F bounded sliding encoded window
    -> progressive decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL renderer
```

MP3 requirements:

- progressive decode using the proven exact dependency path;
- network starvation waits/refills instead of becoming EOF;
- Layer III reservoir pre-roll from an earlier encoded anchor;
- seek/resume/rejoin cancellation safety;
- no sound-thread network/disk/decode blocking.

Common WAV final target:

- mono/stereo only;
- unsigned 8-bit PCM;
- signed 16/24/32-bit PCM;
- 32-bit IEEE float;
- explicit layout/time-to-byte metadata;
- stereo safely downmixed to mono;
- >2 channels and unusual/compressed/telephony WAV rejected.

M1G removes active reliance on the obsolete JavaSound/mp3spi complete-file decoder and narrows active prepared/local advertisement to formats the new engine actually implements.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- leaving range cancels local demand/resources;
- return/rejoin current server time;
- dimension/world/disconnect/resource reload safety;
- underrun refill/rejoin without canonical pause;
- stale generations cannot restart old sound;
- VS2 movement lifecycle.

## M1I — gated native FLAC

Optional. Only advertise `.flac` after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, package, and Minecraft-runtime proof. No Ogg-FLAC.

## M1J — multispeaker shared clocks

Correctness first: shared server sync clocks without expected-global-member barriers, while each physical speaker keeps its own mono positional renderer.

## M1K — active-session sharing optimization

Only after M1J correctness: coalesce identical active encoded/decode work where safe without persistent client caching or collapsing physical positional renderers.

## M1L — legacy finite API migration/removal

Migrate compatibility frontends worth keeping to the new asset/transport/decoder engine; remove obsolete whole-packet/whole-PCM finite state and old format promises.

## M1M — HQ RAW finalization

Keep bounded RAW producer semantics and separate `hqspeaker_audio_empty`; no fake finite timeline.

## M1N — SoundEngine/OpenAL integration cleanup

Correct category/gain, reload lifecycle, stale channel cleanup, attenuation/VS2 movement, one mono positional source per physical speaker.

## M1O/P/Q — hardening, package verification, consolidated runtime acceptance

Stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility only after finite positional rendering is stable.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. Do not force finite server-asset semantics onto live sources.

## M4 — release cleanup

Finalize public API/docs, remove obsolete prototype/dead paths, decide custom HQ block fate, resolve license metadata provenance, and package release cleanly.
