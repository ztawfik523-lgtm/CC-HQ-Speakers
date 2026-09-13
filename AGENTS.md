# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E source/test/CI complete; M1F completion/acceptance provisional; M1G not started.**

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Current hardening branch:

`codex/m1e-final-hardening`

Exact final M1E code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

The final focused Minecraft M1E acceptance script was explicitly skipped by the owner. Never claim it passed.

Read in this order before changing implementation:

1. `docs/M1E-FINAL-HARDENING-2026-09-13.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/M1E-SERVER-AUTHORITY.md`
7. `docs/M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit; M1E findings resolved
8. `docs/M1F-IMPLEMENTATION-2026-09-13.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/ROADMAP.md`
11. `docs/LUA-API.md`
12. `docs/ARCHITECTURE.md`
13. `docs/CC-T-COMPATIBILITY-CONTRACT.md`
14. `docs/FUTURE-CLEANUP.md`
15. exact current source and CI

Older finalization/handoff/preparation docs are historical and do not override current source or the final-hardening/current-state records.

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

One physical speaker remains one mono positional source.

Do not infer application role from a file format. MP3/WAV/etc. can represent any role chosen by the Lua program.

## Explain behavior before code

For modern finite playback, explain it first as:

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-owned playback clock/state
-> client asks for bounded encoded pieces
-> bounded client encoded RAM
-> M1G will decode those pieces into sound
```

Only then introduce packet/class/executor details when useful.

The user frequently asks to recheck, prove, and disprove assumptions. Treat plausible ideas as hypotheses until source/tests/CI/runtime evidence supports them.

When there are multiple meaningful choices, explain the visible tradeoff to the user before choosing. If a recheck discovers a genuine correctness/design choice with meaningful tradeoffs, present the options before changing direction.

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

`audioPlayStaged()` was this project's prototype API and remains removed. Do not restore it as an original-HQ-Speakers compatibility requirement.

## M1E authority — finalized source/test/CI contract

Preserve server ownership of:

- generation;
- PLAYING / PAUSED / ENDED / ERROR;
- duration/position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF;
- terminal server errors.

Client READY requests fresh state. Client ERROR is diagnostic only.

Final M1E hardening already resolved:

- packet/client projection exceptions escaping canonical transitions;
- one failing player aborting later recipients in a broadcast;
- prepared-start ghost-session/rollback inconsistency;
- failed final MediaAsset deletion losing retry ownership;
- detached/rejected/in-flight active-path release retry gaps;
- terminal ERROR continuing to advance position.

Production semantic behavior is in `FinitePlaybackStateMachine` and has deterministic tests.

Do not reopen those findings without new evidence.

The final focused M1E Minecraft PASS remains skipped by owner decision. Never convert CI into a claimed runtime PASS.

## MediaAsset lifetime rule

`MediaAssetStore.release()` deliberately preserves an entry/reference if final file deletion fails.

Active logical releases therefore hand responsibility to `MediaAssetReleaseQueue`, which keeps retry ownership until release succeeds or the asset is already missing.

This applies to finite playback, prepared/detached/rejected asset cleanup, and M1F in-flight range references.

`ServerMediaAssets` retries pending releases at a throttled cadence. Server shutdown stops/drains range IO before store close, and speaker/peripheral cleanup runs before shared media-store shutdown.

Do not replace this with log-and-forget cleanup.

## M1F range architecture — preserve it, finish its own acceptance

Current source has:

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
- in-flight range retain/retry-safe release;
- generation/asset/player/relevance recheck before send;
- stale completion discard;
- availability distinguishes present/not-arrived/EOF/stale data.

Those numeric limits are tuning values, not frozen public semantics.

M1F still needs:

- a real sliding consume/discard/advance window which preserves unread prefetched bytes;
- the remaining deterministic component acceptance for request identity/relevance/stale completion, shutdown integration, packet bounds/codecs, and client BEGIN/STATE anchor gating;
- focused Minecraft transport acceptance remains unrecorded.

Do not restore the old whole-file bridge to solve any of these.

Do not call M1F complete merely because M1E is complete.

## M1G boundary

M1G has not started.

M1G owns:

- progressive MP3 decoder path;
- temporary missing encoded data must not become EOF;
- MP3 Layer III reservoir pre-roll;
- common WAV layout/time-to-byte metadata;
- WAV integer/float conversion and stereo-to-mono downmix;
- bounded PCM queue;
- decoder cancellation;
- actual positional Minecraft/OpenAL rendering.

Do not repair the obsolete JavaSound/mp3spi complete-file bridge instead.

Normally finish M1F's sliding-window/acceptance work before M1G. If the owner explicitly wants to move that work into the opening M1G phase, present the milestone tradeoff before changing the boundary.

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
3. current final-hardening/current-state records;
4. exact current CI/package evidence;
5. verified-facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. older milestone/finalization/handoff docs.

Green CI is not Minecraft runtime proof and only proves tests which actually exist.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior for cleanliness alone.
- Preserve useful original compatibility, but not proven bugs or discarded project-prototype APIs.
- Keep server semantic state separate from transfer/decoder/renderer state.
- Never invent finite duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- If a recheck discovers a genuine correctness/design choice with meaningful tradeoffs, present options before changing direction.
- The owner explicitly asked to be consulted before Java changes when a recheck finds substantive implementation problems; honor that gate unless the owner has already explicitly asked to fix that exact issue.
- Do not report a runtime script PASS unless it actually ran successfully.
