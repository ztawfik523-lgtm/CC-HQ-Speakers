# Known issues / product gaps

Severity here is project priority, not a claim of exploitability. Source/CI work is not Minecraft runtime proof until the relevant runtime contract passes.

M1E server-authority source/test/CI is complete at code-bearing head `d0e66ab9135359627086c13647d5241ad778643f` (run `34658958488`). Minecraft M1E runtime acceptance is still pending. M1F demand-driven finite transport is the next implementation milestone.

## Source-implemented / runtime pending

### KI-001 — standard CC:T contract was historically replaced instead of preserved

**Status: source-implemented in M1A; runtime acceptance pending.**

The normal speaker delegates exposed `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` to CC:T 1.120.0's actual `SpeakerPeripheral` through the composite boundary.

### KI-002 — synthetic `speaker_audio_empty` was not native CC:T backpressure

**Status: source-implemented in M1A; runtime acceptance pending.**

Legacy HQ synthetic `speaker_audio_empty` is filtered. Standard CC:T owns native `speaker_audio_empty`; HQ RAW uses separate bounded `hqspeaker_audio_empty` producer pacing.

### KI-003 — finite/raw source ownership could desynchronize

**Status: source-implemented for the normal single-speaker M1A path.**

The composite tracks one HQ continuous owner. Legacy `*All` / `*At` paths still bypass this boundary and are migrated later rather than patched onto obsolete architecture.

### KI-005 — RAW renderer could remain alive after data drains

**Status: source lifecycle implemented for normal M1A path; runtime timing pending.**

Accepted RAW duration drains in server ticks and releases ownership after queue/data idle. Dynamic listener lifecycle remains M1H work.

### KI-006 — loop-disable position edge

**Status: source-resolved by the server-authoritative clock path; runtime acceptance pending.**

`FinitePlaybackClock.setLooping()` rebases the current wrapped position before changing loop state, and M1E uses that clock as canonical server state.

### KI-007 — prototype renderer/anchor authority

**Status: source-resolved in M1E.**

`successfulRenderers` and renderer/anchor authority were removed from `HQFiniteMediaServer`. The server clock starts immediately and remains canonical regardless of client renderers.

### KI-008 — client STARTED/SEEKED/etc. could rewrite canonical state

**Status: source-resolved in M1E.**

Protocol v4 removes `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, and `ENDED` from client finite-status telemetry. Only READY and diagnostic ERROR remain. Client renderer state cannot rewrite the server clock/state.

### KI-009 — no-renderer timeout could fail valid playback

**Status: source-resolved in M1E.**

The 15-second renderer-observation failure and canonical `observed` state were removed. Playback progresses with zero listeners.

### KI-010 — exact-duration seek edge

**Status: source-resolved in M1E; runtime acceptance pending.**

Non-looping `seek(duration)` immediately enters canonical ENDED. Looping exact-duration seek wraps to 0. `FinitePlaybackClockTest` covers the deterministic clock edge and `scripts/m1e_server_authority_test.lua` covers the intended in-game contract.

### KI-020 — provider cache could retain stale world state

**Status: source-resolved in M0.5.**

Composite entries are explicitly evicted on speaker removal, server Level unload, and server shutdown.

### KI-028 — early canonical EOF could outlive the temporary transfer

**Status: source-resolved in M1E.**

Canonical EOF now closes the old transfer channel before releasing the playback asset reference, so a short song cannot release/delete the asset while the transitional transfer still reads it.

### KI-029 — old BEGIN/CONTROL packets lacked current canonical state

**Status: source-resolved in M1E.**

Protocol v4 adds `HQFiniteMediaStatePacket`. BEGIN remains immutable/setup information for the temporary bridge; STATE carries current canonical state/position/duration/loop/volume. A READY client explicitly receives fresh server truth before it starts/reseeks.

## Active high priority — M1F/M1G finite engine replacement

### KI-004 — leaving range can leave stale client renderer state

Current inherited stop/control delivery is range-local and the temporary finite recipient set is still fixed. A client can leave before later invalidation and retain stale local audio state.

**Target:** M1H dynamic relevance. Leaving range destroys/parks local renderer and cancels demand; returning receives current server state and only restarts if playback is still active.

### KI-026 — transitional client still requires a complete local file

Current M1E `HQFiniteMediaClient` still creates `hqspeaker-cache`, writes the whole encoded file, renames `.part` -> `.media`, and opens `FileFiniteAudioStream` only after complete transfer. The important M1E change is that READY then requests current server state instead of starting from 0.

**Target:** M1F/M1G replace this bridge with bounded in-memory encoded streaming and progressive decoding. No persistent client song cache remains in the final path.

### KI-027 — transitional finite file IO still runs on game threads

The old server bridge still reads chunks from its open file channel during `tick()`. The old client bridge writes received chunks after scheduling onto the Minecraft client thread.

**Target:** M1F moves server asset reads to a bounded IO executor and removes client disk transfer. M1G decode/conversion runs on workers; the sound thread consumes ready PCM only.

### KI-030 — no final demand-driven finite protocol yet

M1E still captures recipients once and pushes the complete file. The STATE packet fixes semantic authority but intentionally does not pretend this is final transport.

**Target:** M1F adds bounded client-requested encoded ranges, generation/relevance validation, async reads, stale-work discard, and per-player outstanding/rate limits.

### KI-031 — MP3 temporary starvation can be mistaken for EOF

The final progressive MP3 path must not return permanent EOF to JLayer just because a requested network range has not arrived yet.

**Target:** M1G decoder input waits/refills on a decoder worker until bytes arrive or true asset EOF is reached.

### KI-032 — MP3 seek/rejoin needs bit-reservoir pre-roll

Opening Layer III exactly at the audible target frame may produce incorrect initial frames because compressed main-data can depend on earlier frames.

**Target:** server-selected earlier frame anchor + silent decode/discard pre-roll in M1G.

### KI-033 — historical WAV acceptance is broader than the final converter target

Frozen M1D accepts unusual sample widths/encodings because it followed JavaSound parity.

**Target:** M1G narrows active prepared/local WAV support to mono/stereo common PCM (8/16/24/32 integer) plus float32, matching the implemented progressive converter.

### KI-034 — FLAC is desired but not proven

There is no current native-FLAC analyzer/decoder/seek implementation in the project.

**Target:** M1I is a gated extension. MP3/WAV completion does not wait for FLAC. Do not advertise FLAC until analyzer, progressive decode, seek/rejoin, malformed-input behavior, packaging, and Minecraft runtime pass.

## Retained legacy/stream/multispeaker problems

### KI-011 — legacy finite decoder queue is unbounded

The inherited decoder executor is single-threaded with an unbounded task queue.

**Target:** M1L removes/migrates the old finite engine rather than polishing it.

### KI-012 — legacy whole decoded PCM allocation scales with duration

Legacy finite decode can materialize a complete decoded result before validation.

**Target:** M1G bounded encoded RAM + bounded mono PCM queue.

### KI-013 — stream volume is applied twice

`StreamingAudioSource.queuePCM()` scales PCM and renderer volume applies gain again.

**Target:** later live/OpenAL cleanup; one logical gain stage plus Minecraft master/category scaling.

### KI-014 — HLS live-window progression can stall

`EXT-X-MEDIA-SEQUENCE` is parsed but the retained logic can rely on a persistent segment-list index.

**Target:** M3 live-stream rebuild.

### KI-015 — direct TS path is not incremental

`streamTS()` can collect a full demuxed list before playback.

**Target:** M3 incremental TS.

### KI-016 — unsupported TS audio may be mislabeled as PCM

Unsupported decode may return compressed bytes unchanged.

**Target:** M3 explicit unsupported-codec failure.

### KI-017 — live server state is intent, not renderer/network truth

Current stream state does not prove client render/network state; live pause/reconnect semantics are incomplete.

**Target:** M3.

### KI-018 — inherited multispeaker expected-member barrier can deadlock partial listeners

Expected group size is global while packet delivery is range-local.

**Target:** M1J shared server sync clocks with no expected-global-member barrier.

### KI-019 — retained shared stream session can leak before start

A shared stream session which never reaches its expected taps can remain mapped.

**Target:** M3/release cleanup.

## Medium / cleanup

### KI-021 — finite advertised formats need narrowing after M1D

Frozen M1D correctly narrowed earlier unsupported MP4/M4A/AAC claims, but its historical product surface still includes OGG/AIFF/AU.

**Target:** M1G final prepared/local advertisement becomes MP3 + implemented common WAV. M1I optionally adds native FLAC. Historical M1D remains reproducible on its frozen branch.

### KI-022 — separate HQ block/group architecture appears duplicated

The repo registers `hqspeaker:hq_speaker` while the actual product path upgrades normal CC speakers.

**Target:** decide/remove/quarantine before release (M4).

### KI-023 — inherited 8 MiB finite byte API remains

Legacy `speakMp3`/`speakOgg`/`speakWav` byte APIs still use one-shot limits/old engine.

**Target:** M1L. Large local files use prepared/server assets and streamed client ranges.

### KI-024 — documentation/testing can become stale during redesign

**Status:** active docs are aligned around the M1E code-bearing checkpoint and the M1F+ streaming direction. `CHAT-HANDOFF.md` is the canonical new-chat continuation document; `CHAT-HANDOFF-2026-09-12.md` is retained for deeper dated context. Exact current source and CI still take precedence if the branch later moves.

### KI-025 — license metadata mismatch

Top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0.

**Target:** resolve provenance before public release (M4). Do not silently relicense.
