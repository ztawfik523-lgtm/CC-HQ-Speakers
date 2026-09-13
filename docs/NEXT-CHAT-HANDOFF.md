# CC:HQ Speakers — current next-chat handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current hardening branch: `codex/m1e-final-hardening`

Final M1E code candidate: `521d4323d9216c8a99e8ec60426997c3330c4068`

Final M1E code CI: `34757923455` — NeoForge 21.1.247 and 21.1.248 both passed build/tests/package verification/artifact upload.

Baseline final-hardening JAR SHA-256: `da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`.

## How to explain this project

Explain behavior in normal ComputerCraft/Minecraft terms first, then implementation details.

The product is a better programmable ComputerCraft speaker peripheral. Lua decides application intent such as music, alarm, speech, ambience, or notification. Java should model technical source capabilities, not permanent application roles.

One physical speaker is one mono positional source.

Do not equate file type with application intent. MP3 or WAV may be music, an effect, a notification, speech, or anything else chosen by the Lua program.

The normal CC:T `computercraft:speaker` remains the user-facing block/peripheral. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain foundational/delegated behavior.

## Current architecture

```text
ComputerCraft file
    -> temporary HQ writable staging
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> bounded client-requested encoded ranges (M1F)
    -> progressive decoder + bounded PCM + positional renderer (M1G)
```

Staging is import plumbing, not the persistent playback model.

Recommended user-facing finite helpers remain:

- `hq.playFile(speaker, path)`
- `hq.prepareFile`
- `hq.preparedInfo`
- `hq.playPrepared`
- `hq.releasePrepared`

The project-prototype `audioPlayStaged()` route was removed by M1F. That was this project's temporary API, not an original HQ Speakers compatibility API.

## M1E — finished source/test/CI milestone

M1E is complete at the source/test/CI level.

Canonical states:

- PLAYING
- PAUSED
- ENDED
- ERROR

The server owns position, duration, pause/resume, seek, loop, volume, natural EOF, and server errors. Playback starts counting immediately after a successful prepared start, even if no client is rendering sound.

Client READY only requests a fresh STATE. Client ERROR is diagnostic only.

Final hardening closed:

- packet-send exceptions escaping canonical transitions;
- one failed player aborting later recipients in a broadcast;
- prepared-start ghost-session/rollback inconsistency;
- failed final MediaAsset deletion losing retry ownership;
- detached/rejected/in-flight asset release retry gaps on active paths;
- server ERROR position continuing to advance.

Production semantics are in `FinitePlaybackStateMachine`; deterministic tests cover server-owned time/state behavior. `BestEffortProjectionTest` covers projection failure containment. `MediaAssetReleaseQueueTest` covers deferred release ownership.

### M1E runtime evidence boundary

The final strengthened Minecraft M1E acceptance script was explicitly skipped by the owner.

Do not claim a final M1E runtime PASS.

Use exactly:

```text
M1E source/test/CI: complete
M1E final manual Minecraft acceptance: skipped / no recorded PASS
```

The earlier 2026-09-12 runtime diagnostic remains supporting authority-separation evidence only.

## M1F — implemented architecture, completion still provisional

Current transport direction remains valid:

```text
server MediaAsset
-> client requests only bounded encoded ranges it needs now
-> server reads those ranges off-thread
-> client keeps bounded encoded RAM
```

Current implementation facts:

- protocol v5 range request/data;
- STATE includes server-selected `anchorOffset` + `anchorTime`;
- max range response 128 KiB;
- one active client encoded window 512 KiB;
- max 4 outstanding requests/player;
- max 512 KiB outstanding bytes/player;
- 2 range-IO workers, queue 64;
- accepted range work retains the asset while queued/running;
- stale generation/asset or currently irrelevant player completion is discarded;
- modern prepared client no longer creates complete-song `.part/.media` files;
- old modern finite CHUNK/END path is removed.

M1F remaining work:

1. make `FiniteRangeWindow` a real sliding consume/discard window which retains useful unread prefetched data;
2. complete deterministic acceptance for full request identity/relevance/stale completion, shutdown integration, packet bounds/codecs, and client BEGIN/STATE anchor gating;
3. focused Minecraft range-transport acceptance remains unrecorded.

M1F is allowed to be silent. Do not repair the obsolete complete-file JavaSound/mp3spi bridge just to make this transition audible.

## M1G and later

M1G has not started. It owns progressive MP3 + common WAV decode, network-starvation-vs-EOF behavior, MP3 Layer III pre-roll, bounded mono PCM, cancellation, and actual positional rendering.

M1H owns dynamic late-entry / leave-range cleanup / return-rejoin / dimension and reload lifecycle.

M1I gates native FLAC later.

M1J/M1K own multispeaker shared-clock correctness and later sharing optimization.

M1L migrates/removes legacy finite APIs.

M2 is Sound Physics Remastered integration after finite positional rendering is stable.

## Important unresolved release items

- top-level LICENSE is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0; resolve provenance, do not guess/relicense;
- separate custom `hqspeaker:hq_speaker` registration remains despite the product direction using normal CC:T speakers;
- inherited live/HLS/TS paths remain later M3 work;
- active advertised finite format breadth must eventually be narrowed to formats the new progressive engine actually implements.

## Read order before changing code

1. `M1E-FINAL-HARDENING-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit; M1E findings resolved
8. `M1F-IMPLEMENTATION-2026-09-13.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `ROADMAP.md`
11. `LUA-API.md`
12. exact current source and current CI

Do not call M1F complete just because M1E is now complete. Do not start M1G by silently carrying M1F's unfinished sliding-window/acceptance work forward unless the owner explicitly chooses that tradeoff.
