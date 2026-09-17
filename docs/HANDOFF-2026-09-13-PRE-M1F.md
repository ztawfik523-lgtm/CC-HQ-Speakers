# Fresh handoff — pre-M1F documentation checkpoint

> **Historical preparation checkpoint. M1F has since been implemented/finalized and M1G is integrated in source.**
>
> Preserve this file for the pre-M1F reasoning/evidence, but do not use its “current handoff,” stop conditions, or implementation plan as present instructions. Current authority is `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, and exact current source.

Date: 2026-09-13

## Product framing which remains valid

The project upgrades the normal ComputerCraft `speaker` into a better programmable audio peripheral.

Lua decides whether audio is music, a sound effect, an alarm, speech, a notification, ambience, a soundboard clip, or something else. Java exposes truthful audio capabilities, not application roles or playlist policy.

The normal `computercraft:speaker` remains the product surface.

Standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain compatibility requirements. HQ RAW uses separate `hqspeaker_audio_empty` producer pacing.

## Historical checkpoint status

At this checkpoint:

- M1E server-authority source/tests/CI were finalized;
- final manual M1E Minecraft acceptance had been skipped/unrecorded by owner decision;
- M1F implementation had **not started**;
- `audioPlayStaged()` was approved for removal when M1F replaced the old direct-staging route.

M1F has since been implemented and finalized. Modern prepared playback now uses bounded range transport and no modern complete client song file. `audioPlayStaged()` is removed.

## Exact repository state from this checkpoint

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Historical branch: `codex/m1e-server-authoritative-finite`

Important implementation checkpoints recorded at the time:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- original M1E semantic checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`
- runtime-diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`
- last code/documentation head before this historical documentation refresh: `ff8fc52e8660249150e056d1dff4307377afe7c4`

Known M1E finalization CI at that time:

- run `34725651930`
- NeoForge 21.1.247: success
- NeoForge 21.1.248: success
- build/tests/package verification/artifact upload passed

These are historical evidence, not the current source checkpoint. Current green M1G source is recorded in `CURRENT-STATE.md`.

## Historical M1E model

The server owns the finite playback timeline.

A successful prepared play:

- starts the server clock immediately;
- does not wait for a Minecraft client to be ready;
- advances even with zero listeners;
- owns pause/resume/seek/loop/volume/EOF;
- treats client decoder/render failure as local diagnostic rather than canonical authority.

Semantic states:

```text
PLAYING
PAUSED
ENDED
ERROR
```

Client finite telemetry:

```text
READY  -> asks for fresh current server state
ERROR  -> diagnostic only
```

This server-authority model remains foundational.

## Historical decoder boundary

The old prepared client bridge was known-bad for the tested MP3 and was intentionally disposable. M1F was not supposed to repair it; M1G owned progressive MP3/common-WAV decoding and actual audible finite playback.

That replacement M1G path is now integrated in source. Do not use this historical old-decoder discussion to justify restoring the complete-file bridge.

## Historical M1F target

M1F was designed to make large finite-file transport demand-driven and bounded:

```text
server owns the whole encoded file
-> client requests a bounded encoded range near current need
-> server reads it off-thread
-> client keeps only a bounded temporary encoded window
-> old encoded bytes are discarded as playback advances
```

Required properties included:

- arbitrary encoded offsets;
- bounded response packets;
- bounded per-player outstanding work;
- file reads away from server tick;
- safe asset lifetime while reads are in flight;
- stale-result discard after replacement/leave/disconnect;
- bounded client encoded RAM independent of full file size;
- no modern client `.part/.media` song cache;
- clear distinction between DATA_AVAILABLE, NEED_DATA, true EOF, and cancelled/stale playback.

These goals are now implemented in M1F. Current transport tuning/evidence is recorded in `CURRENT-STATE.md`, `SERVER-CONFIG.md`, and `VERIFIED-FACTS.md`.

## Historical shutdown requirement and later correction

The pre-M1F recheck correctly required background range reads to stop/drain/cancel before the shared media store closes.

Later source auditing found KI-054: the current shutdown sequence can still fail earlier in `FiniteRangeReadService.close()`, preventing `MediaAssetStore.close()` and leaving the media-store root lock/registry alive in the JVM. Use the current KI-054 record rather than assuming this historical requirement is fully satisfied.

## Historical M1G boundary and current status

The pre-M1F handoff assigned actual progressive finite audio to M1G:

- MP3 decode;
- common WAV conversion;
- starvation/refill behavior;
- MP3 pre-roll;
- bounded PCM queues;
- cancellation;
- positional Minecraft sound.

Core modern target remains MP3 + supported common WAV; native FLAC remains later gated work.

M1G progressive decode/render is now integrated. Later selected M1G scope additionally specifies:

- explicit server-authoritative decoder/re-anchor revision;
- fixed 32-block core modern-finite radius with volume changing gain rather than range;
- global-volume-zero local hibernation while canonical server time continues;
- ordinary non-gapless replay after local EOF;
- no SPR acoustic/range integration in M1G.

## Legacy boundary which remains valid

The repo still contains inherited finite/live/multispeaker code such as byte-taking APIs, old whole-file/whole-PCM paths, streaming/HLS/TS behavior, and old multispeaker helpers.

Do not model the modern prepared architecture on those systems. Useful compatibility surfaces are reviewed in later milestones.

Current audit/recheck additionally confirms later issues such as live-HLS refreshed-window progression, misleading legacy format lists, and legacy `playNoteAll`/`playSoundAll` semantic mismatches. Those are not M1G scope.

## Current read order

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `NEXT-CHAT-HANDOFF.md`
7. `ARCHITECTURE.md`
8. `ROADMAP.md`
9. exact current source/CI

This pre-M1F handoff is historical evidence only.
