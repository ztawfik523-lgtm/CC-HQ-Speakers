# CC:HQ Speakers — next-chat handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1f-finalization`

Current source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365`

## Current status

M1E and M1F are complete at source/test/CI/package level.

M1E focused final Minecraft acceptance was explicitly skipped by the owner and remains unrecorded.

M1F focused real-Minecraft range-transport acceptance is unrecorded. M1F audibility was not required.

M1G is next and has not started.

## Product framing

This is a programmable upgrade to the normal CC:T `computercraft:speaker`, not a built-in music player.

Lua decides music/effect/notification/alarm/speech/ambience/etc. Java models technical source capabilities only.

One physical speaker = one mono positional source.

Standard CC:T behavior stays foundational/delegated:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ RAW has separate `hqspeaker_audio_empty` pacing.

## Modern local finite API

Recommended:

```lua
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Reusable flow:

```text
prepareFile -> preparedInfo -> playPrepared -> releasePrepared
```

`audioPlayStaged()` was a project prototype and remains removed. Temporary writable staging is only an import bridge into immutable server MediaAssets.

## M1E final authority contract

Server owns finite generation/state/time/pause/resume/seek/loop/volume/EOF/terminal server errors.

Client READY requests fresh state; client ERROR is diagnostic only. Projection failure cannot roll back canonical state. Asset release is retry-safe.

Final M1E code: `521d4323d9216c8a99e8ec60426997c3330c4068`.

Do not reopen resolved M1E issues without new evidence.

## M1F final transport contract

```text
server MediaAsset
-> authoritative STATE + encoded anchor
-> client bounded range requests
-> bounded off-thread server reads
-> bounded sliding client encoded window
-> M1G decoder later consumes that window
```

Protocol v5 modern range identity:

```text
source + asset + generation + offset + length/bytes
```

Current tuning:

- max range 128 KiB;
- client encoded window 512 KiB;
- per-player outstanding: 4 requests / 512 KiB;
- IO workers 2;
- IO queue 64.

Final M1F behavior/proof:

- first demand waits for authoritative STATE;
- pure shared source/asset/generation/range validation;
- same-dimension/current-range relevance on request/completion;
- per-player bounded work;
- reads on dedicated range IO workers;
- in-flight MediaAsset retain + retry-safe release;
- stale completion discard;
- queued shutdown cancellation/accounting cleanup;
- range shutdown before store close;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- arbitrary non-zero reset/re-anchor;
- sliding `advanceTo` discards consumed prefix while preserving unread overlap;
- repeated refill beyond one complete window remains bounded;
- integrated `FiniteRangeTransportTest` moves exact bytes from a 2 MiB server asset through the async read service into the client window, slides/refills, then jumps elsewhere;
- no modern complete-song `.part/.media` files;
- no modern finite CHUNK/END packets;
- no `audioPlayStaged()`.

M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`.

M1F CI `34763362365` passed both NeoForge targets.

Baseline .247 JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

## M1G next

M1G should consume the M1F bounded/sliding encoded window and add:

- progressive MP3 decode;
- network starvation != decoder EOF;
- Layer III reservoir pre-roll;
- common WAV layout/time-to-byte metadata;
- common WAV integer/float conversion;
- stereo -> mono downmix; reject >2 channels;
- bounded mono PCM queue;
- decoder cancellation/replacement;
- actual mono positional Minecraft/OpenAL rendering.

Do not repair the obsolete complete-file JavaSound/mp3spi bridge as the new engine.

If M1G exposes a real M1F defect, fix the defect; if it exposes a meaningful architecture choice with tradeoffs, ask the owner before choosing.

## M1H boundary

M1F validates current relevance but does not implement full listener discovery/recovery. M1H owns late entry, proactive leave cleanup, return/rejoin, dimension/reload safety, underrun recovery, and VS2 moving-speaker lifecycle.

## Read order

1. `docs/M1F-FINALIZATION-2026-09-13.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
7. `docs/M1E-FINITE-STREAMING-DESIGN.md`
8. `docs/ROADMAP.md`
9. `docs/LUA-API.md`
10. `docs/ARCHITECTURE.md`
11. exact current source and CI

Older reevaluation/preparation/handoff docs are historical and must not override these finalization records.
