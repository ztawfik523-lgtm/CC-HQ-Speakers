# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G next and not started.**

Current branch:

`codex/m1f-finalization`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Focused Minecraft M1F transport acceptance is not recorded. Never convert CI into a runtime PASS.

Read before implementation:

1. `docs/M1F-FINALIZATION-2026-09-13.md`
2. `docs/M1E-FINAL-HARDENING-2026-09-13.md`
3. `docs/CURRENT-STATE.md`
4. `docs/KNOWN-ISSUES.md`
5. `docs/TESTING.md`
6. `docs/VERIFIED-FACTS.md`
7. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
8. `docs/M1E-FINITE-STREAMING-DESIGN.md`
9. `docs/ROADMAP.md`
10. `docs/LUA-API.md`
11. `docs/ARCHITECTURE.md`
12. `docs/CC-T-COMPATIBILITY-CONTRACT.md`
13. exact current source and CI

Historical reevaluation/preparation/handoff docs do not override the final M1E/M1F records.

## Target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target 1.21.1-1.5.1

## Product rule

This is a programmable ComputerCraft speaker peripheral.

Lua decides application meaning/policy: music, effects, notifications, alarms, speech, ambience, soundboards, playlists, sequencing, priorities, etc. Java models truthful technical capabilities only.

Do not create permanent Java music/effect/notification lanes or Java playlist/album policy. Do not infer role from MP3/WAV/etc.

One physical speaker remains one mono positional source.

## Standard CC:T compatibility

The normal `computercraft:speaker` remains the product surface and exposed type `speaker`.

Preserve native/delegated:

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

`audioPlayStaged()` was this project's prototype API and remains removed. Temporary staging is import plumbing only.

## M1E authority — closed unless new evidence appears

Server owns finite generation/state/time/control/EOF. Client READY refreshes state; client ERROR is diagnostic. Projection is best-effort/per-recipient. Asset release is retry-safe.

Final M1E code: `521d4323d9216c8a99e8ec60426997c3330c4068`.

Final M1E focused Minecraft acceptance was explicitly skipped by the owner. Do not claim it passed.

Do not reopen the resolved M1E packet-projection, ghost-session, release-owner, or ERROR-clock issues without new evidence.

## M1F transport — finalized source/test/CI contract

Explain it first as:

```text
server owns complete MediaAsset
-> authoritative STATE selects encoded anchor
-> nearby client requests bounded encoded pieces
-> server reads pieces off-thread
-> client keeps bounded sliding encoded RAM
-> M1G later decodes it into sound
```

Preserve:

- protocol v5 range request/data;
- no modern whole-file push;
- no modern complete-song `.part/.media` client file;
- no modern finite CHUNK/END packets;
- first demand waits for authoritative STATE;
- shared source/asset/generation/range sanity;
- same-dimension/current-range validation for range work;
- 128 KiB max range, 512 KiB client window, 4 requests/512 KiB per-player outstanding, 2 IO workers/64 queue as current tuning values;
- off-thread reads;
- in-flight asset retain/retry-safe release;
- stale completion discard;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- arbitrary re-anchor;
- forward sliding/discard preserving unread prefetch;
- bounded refill beyond one window;
- range shutdown before store cleanup.

`FiniteRangeTransportTest` is the integrated fake-consumer proof. `M1F-FINALIZATION-2026-09-13.md` has the full matrix.

Focused real-Minecraft M1F transport acceptance is unrecorded. M1F audibility is not required.

## M1G boundary — next work

M1G owns:

- progressive MP3 decode from the M1F window;
- temporary missing encoded bytes must not become decoder EOF;
- MP3 Layer III reservoir pre-roll;
- common WAV layout/time-to-byte metadata;
- WAV integer/float conversion and stereo-to-mono downmix;
- bounded mono PCM queue;
- decoder cancellation/replacement;
- actual mono positional Minecraft/OpenAL rendering.

Do not repair the obsolete complete-file JavaSound/mp3spi bridge as the new engine.

If M1G reveals a real transport defect, fix the defect rather than silently redefining M1F. If it reveals a meaningful architecture choice, present options to the owner first.

## M1H remains separate

M1F validates current relevance, but M1H owns proactive late-entry/leave-return/disconnect/reload/underrun/VS2 listener lifecycle.

## Evidence rules

Trust claims in this order:

1. exact target-stack runtime evidence;
2. exact current source;
3. current finalization/current-state records;
4. exact current CI/package evidence;
5. verified-facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. historical milestone/handoff docs.

Green CI is not Minecraft runtime proof.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior for cleanliness alone.
- Preserve useful original compatibility, not proven bugs or discarded project-prototype APIs.
- Keep server semantic state separate from transport/decoder/renderer state.
- Never invent finite duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- If a recheck discovers a genuine correctness/design choice with meaningful tradeoffs, present options before choosing.
- Do not report runtime PASS unless it actually ran successfully.
