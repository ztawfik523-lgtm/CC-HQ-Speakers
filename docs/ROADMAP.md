# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-13 reevaluation

`M1E-M1F-REEVALUATION-2026-09-13.md` supersedes the earlier completion labels for M1E/M1F.

The architecture remains accepted, but M1E/M1F correctness/acceptance work is reopened. M1G must not begin until the owner chooses how to handle those findings.

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

**Implemented semantic design; hardening/acceptance reopened.**

Still correct by current source review:

- server starts canonical playback immediately;
- server owns state/time/EOF/seek/loop/volume;
- client READY/ERROR is non-authoritative;
- exact-end and loop semantics remain intact.

Evidence limits:

- core clock math has deterministic tests;
- both-target CI/package evidence exists;
- historical runtime diagnostics support authority separation;
- final focused Minecraft PASS was skipped/unrecorded;
- no direct deterministic `HQFiniteMediaServer` state-machine/failure-path test currently exists.

Reopened correctness work:

- isolate client packet-send failures from canonical server transitions;
- make `playPrepared()` start/rollback atomic;
- keep/retry playback asset ownership when final release fails.

Do not call M1E fully finalized until the chosen hardening path is resolved.

## M1F — demand-driven finite transport

**Architecture implemented and CI-green; acceptance/completeness reopened.**

Current code head:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

CI `34731827907` passed both NeoForge targets, including tests/package verification/artifact upload.

Implemented and retained:

- protocol v5 client range request/data;
- server-selected encoded anchors in STATE;
- bounded packet/window/outstanding limits;
- bounded off-thread server reads;
- in-flight asset retain;
- current generation/asset/player/relevance recheck before send;
- arbitrary encoded re-anchors;
- no modern client `.part/.media` whole-song bridge;
- old modern finite CHUNK/END packets removed;
- project-prototype `audioPlayStaged()` removed;
- transport availability distinguishes missing data from real EOF.

Reopened M1F work:

- add a real sliding/consume/discard window operation suitable for progressive refill without throwing away useful unread prefetched bytes;
- close the rare in-flight asset-release retry gap;
- add the deterministic acceptance coverage originally promised for server request identity/relevance/stale completion, shutdown ordering, packet bounds/integration, client anchor gating, and consume/discard progression;
- record focused Minecraft transport acceptance if/when the owner wants runtime proof.

Do not call M1F fully accepted/source-test-CI complete at the reevaluated checkpoint.

## Decision gate before M1G

Owner chooses one:

1. finish all reopened M1E/M1F work first;
2. fix shared correctness first and make M1F buffer/test completion explicit M1G entry work;
3. defer Java changes and keep M1E/M1F provisional.

See `M1E-M1F-REEVALUATION-2026-09-13.md` for tradeoffs.

## M1G — core progressive finite engine: MP3 + common WAV

**Not started; blocked on the reevaluation decision.**

Goal:

```text
M1F bounded encoded window
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
