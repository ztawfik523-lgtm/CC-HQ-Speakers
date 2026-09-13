# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E/M1F reevaluation hold; M1G has not started.**

The earlier labels `M1E finalized` and `M1F source/test/CI complete` are superseded by the 2026-09-13 reevaluation. The core architecture remains accepted, but correctness/acceptance work is reopened.

Read in this order before changing implementation:

1. `docs/M1E-M1F-REEVALUATION-2026-09-13.md`
2. `docs/HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`
3. `docs/CURRENT-STATE.md`
4. `docs/KNOWN-ISSUES.md`
5. `docs/TESTING.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1E-SERVER-AUTHORITY.md`
8. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/ROADMAP.md`
11. `docs/LUA-API.md`
12. `docs/ARCHITECTURE.md`
13. `docs/CC-T-COMPATIBILITY-CONTRACT.md`
14. `docs/FUTURE-CLEANUP.md`
15. exact current source and CI

Older finalization/handoff docs are historical and do not override the reevaluation.

## Exact current implementation checkpoint

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Branch: `codex/m1f-demand-driven-finite`

Current Java/source head:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Exact code-head CI:

`34731827907`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Those facts remain valid. Do not call them Minecraft runtime proof or proof of failure paths which current tests do not exercise.

No Java was changed during the reevaluation itself.

## Target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target 1.21.1-1.5.1

## Product rule

This is a programmable ComputerCraft speaker peripheral.

Lua decides application meaning and policy: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc.

Java models truthful technical capabilities only.

Do not create permanent Java music/effect/notification lanes, playlist/album managers, or application-level priority policy.

## Explain behavior before code

For modern finite playback:

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-owned playback clock/state
-> client asks for bounded encoded pieces
-> bounded client encoded RAM
-> M1G will decode those pieces into sound
```

Only then introduce packet/class/executor details when useful.

## Standard CC:T compatibility

The normal `computercraft:speaker` remains the product surface and exposed type remains `speaker`.

Standard calls stay delegated/native:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW uses separate `hqspeaker_audio_empty` backpressure.

## Modern finite Lua/API

Recommended one-call path:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, path, { volume = 0.6 })
```

Reusable path:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

Modern finite controls:

```text
audioStatus
audioPause
audioResume
audioSeek
audioSetVolume
audioSetLooping
audioStop
```

`audioPlayStaged()` was our prototype API and remains removed. Do not restore it.

## M1E authority — preserve the semantic model, harden failure paths

Server ownership remains mandatory for finite:

- generation;
- PLAYING/PAUSED/ENDED/ERROR;
- duration/position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

Client READY requests fresh state. Client ERROR is diagnostic only.

The final focused M1E Minecraft PASS was skipped by owner decision. Never claim it passed.

Reevaluation reopened:

- isolate per-player packet-send exceptions from canonical server transitions;
- make `playPrepared()` rollback/session installation atomic;
- keep/retry playback asset ownership if final release fails;
- add deterministic `HQFiniteMediaServer` failure/state-machine coverage.

Do not interpret these as a reason to return authority to clients.

## M1F range architecture — preserve it, do not overstate acceptance

Current source still has:

- protocol v5 client-pulled range request/data;
- no modern whole-file server push;
- no modern `.part/.media` client song file;
- no modern finite CHUNK/END packets;
- first demand waits for authoritative STATE/anchor;
- bounded 128 KiB range responses;
- bounded 512 KiB one-source encoded byte window;
- per-player outstanding request/byte caps;
- bounded server IO pool/queue;
- range reads off the server tick;
- in-flight range retain;
- generation/asset/player/relevance recheck before send;
- stale completion discard;
- availability distinguishes present/not-arrived/EOF/stale data.

Those numeric limits are tuning values, not frozen product semantics.

Reevaluation reopened:

- add a sliding consume/discard/advance operation which preserves unread prefetched bytes;
- close rare release-retry ownership gaps;
- add the missing deterministic acceptance coverage from the original M1F contract;
- runtime transport acceptance remains unrecorded.

Do not restore the old whole-file bridge to solve any of these.

## Supporting asset-lifetime caution

`MediaAssetStore.release()` deliberately preserves an entry/reference if final file deletion fails.

Any owner releasing a MediaAsset must therefore clear its ownership bookkeeping only **after** successful release, or otherwise retain an explicit retry owner.

This applies to playback references, in-flight range references, and detached prepared-owner cleanup. The current source has failure-path gaps in more than one of those callers.

## M1G boundary

M1G has not started and is currently blocked on owner choice from the reevaluation.

M1G owns:

- progressive MP3 decoder path;
- temporary missing encoded data must not become EOF;
- MP3 reservoir pre-roll;
- common WAV layout/time-to-byte metadata;
- WAV integer/float conversion and stereo-to-mono downmix;
- bounded PCM queue;
- decoder cancellation;
- actual positional Minecraft/OpenAL rendering.

Do not repair the obsolete JavaSound/mp3spi whole-file bridge instead.

## M1H remains separate

M1F revalidates current player relevance for range work, but full dynamic listener lifecycle remains M1H:

- late range entry;
- proactive leave cleanup;
- return/rejoin;
- dimension/world/resource reload;
- underrun rejoin;
- VS2 moving-speaker listener lifecycle.

Do not claim M1F solved M1H.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. current reevaluation record;
4. exact current CI/package evidence;
5. verified-facts/current-state docs;
6. architecture/design docs;
7. roadmap;
8. older milestone/finalization/handoff docs.

Green CI is not Minecraft runtime proof and only proves tests which actually exist.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior for cleanliness alone.
- Preserve useful compatibility, but not proven bugs or discarded prototype APIs.
- Keep server semantic state separate from transfer/decoder/renderer state.
- Never invent finite duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- Do not start M1G until the reevaluation decision is resolved.
- If a recheck discovers a genuine correctness/design choice with meaningful tradeoffs, present the options before changing direction.
- The owner explicitly asked to be consulted before Java changes when reevaluation finds substantive implementation problems; honor that gate.
- Do not report a runtime script PASS unless it actually ran successfully.
