# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Completed/current foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` stay delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Frozen server analysis proves format/duration/seek facts without whole-track PCM decode. Historical analyzer format breadth does not define final product support.

### M1E — server-authoritative finite timeline

Source/tests/CI implemented. Server owns state/time/EOF and does not wait for client renderer readiness. Final manual Minecraft M1E PASS was skipped by owner decision and must not be claimed.

### M1F — demand-driven finite transport

**Source/test/CI complete at `934e74b8ff619178d703f73df8a16ee97b3fc2af`, CI `34731827907` green both targets.**

Implemented:

- protocol v5 client range request/data;
- server-selected encoded anchors in STATE;
- bounded encoded packet/window/outstanding limits;
- bounded off-thread server reads;
- safe in-flight asset lifetime;
- stale completion rejection;
- arbitrary encoded offsets;
- no modern client `.part/.media` complete-file bridge;
- old finite CHUNK/END packets removed;
- project-prototype `audioPlayStaged()` removed;
- transport availability distinguishes missing data from true EOF.

Minecraft runtime acceptance for M1F is not recorded.

## M1G — core progressive finite engine: MP3 + common WAV

**Next implementation milestone; not started.**

Goal:

```text
M1F bounded encoded window
    -> progressive decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL renderer
```

MP3 requirements:

- progressive decode using the proven exact dependency path (currently JLayer family unless replaced by better evidence);
- network starvation must wait/refill rather than become decoder EOF;
- server-derived encoded anchor plus earlier Layer III reservoir pre-roll;
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

M1G also removes active reliance on the old JavaSound/mp3spi complete-file decoder and narrows active prepared/local advertisement to the formats the new engine actually implements.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- leaving range cancels demand/releases local renderer resources;
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
