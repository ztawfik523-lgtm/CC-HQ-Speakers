# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-13

M1E and M1F are complete at source/test/CI/package level.

M1E final hardening:

- code `521d4323d9216c8a99e8ec60426997c3330c4068`;
- CI `34757923455`;
- final focused Minecraft acceptance skipped/unrecorded by owner decision.

M1F finalization:

- source/test candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- CI `34763362365`;
- both NeoForge targets green;
- focused real-Minecraft transport acceptance unrecorded.

M1G is now the next implementation milestone.

## Foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` stay delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Frozen server analysis proves format/duration/seek facts without whole-track PCM decode. Historical analyzer breadth does not define final product support.

## M1E — server-authoritative finite timeline

**Complete at source/test/CI level.**

Server owns generation, PLAYING/PAUSED/ENDED/ERROR, position/duration, pause/resume, seek, loop, volume, natural EOF, and terminal server errors. Client READY refreshes state; client ERROR is diagnostic. Projection failures are best-effort; asset release is retry-safe.

Final focused Minecraft acceptance remains skipped/unrecorded.

## M1F — demand-driven finite encoded transport

**Complete at source/test/CI/package level.**

Final candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Implemented/finalized:

- protocol v5 client range request/data;
- server-selected encoded anchors in STATE;
- first demand waits for authoritative STATE;
- shared source/asset/generation/range validation;
- current relevance validation on request and completion;
- bounded packet/window/outstanding limits;
- bounded off-thread server reads;
- in-flight MediaAsset retain/retry-safe release;
- stale completion discard;
- queued cancellation/accounting cleanup;
- range-service shutdown before store close;
- arbitrary encoded re-anchor;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- forward sliding consume/discard preserving unread prefetch;
- repeated refill beyond one window with bounded RAM;
- integrated fake server-reader -> client-window component proof;
- no modern client whole-song `.part/.media` bridge;
- no modern finite CHUNK/END packets;
- project-prototype `audioPlayStaged()` removed.

Focused real-Minecraft transport acceptance remains unrecorded. M1F audibility was never required.

## M1G — core progressive finite engine: MP3 + common WAV

**Next; not started.**

Goal:

```text
M1F bounded sliding encoded window
    -> progressive decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL renderer
```

MP3 requirements:

- progressive decode from M1F encoded data;
- network starvation waits/refills instead of becoming EOF;
- Layer III reservoir pre-roll from an earlier encoded anchor;
- seek/resume/replacement cancellation safety;
- no sound-thread network/disk/decode blocking.

Common WAV final target:

- mono/stereo only;
- unsigned 8-bit PCM;
- signed 16/24/32-bit PCM;
- 32-bit IEEE float;
- explicit layout/time-to-byte metadata;
- stereo safely downmixed to mono;
- >2 channels and unusual/compressed/telephony WAV rejected.

M1G should remove active reliance on the obsolete JavaSound/mp3spi complete-file decoder and narrow active prepared/local advertisement to formats the new engine actually implements.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- leaving range cancels local demand/resources;
- return/rejoin current server time;
- dimension/world/disconnect/resource reload safety;
- underrun refill/rejoin without canonical pause;
- stale generations cannot restart old sound;
- VS2 movement lifecycle.

M1F's current relevance checks remain the admission/completion safety layer; M1H adds proactive discovery/recovery.

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
