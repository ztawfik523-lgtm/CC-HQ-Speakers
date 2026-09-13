# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E + M1F complete at source/test/CI/package level; M1G integrated engine in progress.**

Current green integrated M1G source checkpoint:

`957832348eaa6e497282d923f2312c9c7d7c550f`

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including tests, package verification and artifact upload.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.** M1F source/test/CI/package/component work is complete.

### KI-046 — M1G audible Minecraft acceptance is not recorded

**Active.** The current source has the progressive decoder/renderer engine, but there is no recorded focused Minecraft audible PASS yet. CI cannot prove actual audibility, attenuation, channel lifecycle, reload behavior or loop-boundary experience.

## Resolved by M1E/M1F

- KI-040: client/network projection escaping canonical transitions — resolved by best-effort per-recipient projection.
- KI-041: `playPrepared()` ghost session rollback — resolved.
- KI-042: failed final MediaAsset release losing retry ownership — resolved for active prepared/finite/range paths.
- KI-045: finite server ERROR advancing time — resolved; ERROR freezes canonical position.
- KI-043: encoded window not progressively consumable — resolved by bounded forward sliding.
- KI-044: incomplete M1F deterministic matrix — resolved at component level.
- KI-026: modern prepared client requiring a complete song file — resolved.
- KI-027: modern finite asset reads on server tick — resolved by bounded range IO workers.
- KI-030: no demand-driven finite protocol — resolved.
- KI-031: temporary missing encoded data indistinguishable from true EOF — resolved.
- KI-038: `audioPlayStaged()` second finite route — removed.
- KI-039: modern CHUNK/END whole-file path — removed.

## M1G issues now source-resolved

### KI-021 — active modern finite format surface needed narrowing

**Resolved for modern prepared/local playback.**

New prepared assets are gated to MP3 or the supported common-WAV subset. Protocol v6 BEGIN carries a `FiniteDecodeDescriptor`; the modern path no longer uses historical `MP3/OGG/AUDIO_FILE` ambiguity.

Historical OGG/AIFF/AU support may still exist in inherited/legacy code and does not define the modern prepared product surface.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Resolved at current source/component level.**

Server anchor selection uses E1 conservative earlier seek points. Progressive JLayer decoding starts from that earlier anchor and discards pre-target PCM before exposing samples to the bounded queue.

Focused Minecraft seek audibility remains part of KI-046.

### KI-033 — historical WAV acceptance broader than final converter target

**Resolved for the modern prepared path.**

`CommonWavAnalyzer` + `ModernFiniteMediaAnalyzer` restrict modern WAV to U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or the chosen narrow PCM/float WAVEX subset. `CommonWavPcmConverter` converts progressively to mono S16 at source sample rate.

### KI-047 — M1G client primitives not wired into modern session

**Resolved.**

`HQFiniteMediaClient` owns decode epochs containing `FiniteEncodedInputStream`, bounded PCM, decoder task and renderer state. Range arrivals wake the active encoded input and stale epochs are cancelled on replacement/seek/stop.

### KI-048 — progressive common-WAV conversion not implemented

**Resolved.** `ProgressiveWavDecoder` uses bounded frame chunks and `CommonWavPcmConverter`; no whole decoded track is retained.

### KI-049 — progressive JLayer finite MP3 decoder not implemented

**Resolved.** `ProgressiveMp3Decoder` decodes frame-by-frame over the starvation-aware encoded input, validates analyzed rate/channels, downmixes to mono S16 and applies pre-roll discard.

### KI-050 — modern finite positional renderer not integrated

**Resolved at source/component level.**

`FinitePcmAudioStream` + `FiniteSpeakerSound` use the Minecraft SoundManager path with BLOCKS category and linear positional attenuation. Renderer-facing reads are bounded/nonblocking; temporary queue starvation is represented with short silence instead of EOF. Focused real-Minecraft proof remains KI-046.

## Active high priority — M1G

### KI-051 — looping finite playback needs an authoritative loop-wrap rejoin policy

**Active owner decision.**

The server finite clock already wraps canonically when looping. The client intentionally does not locally modulo its projected server clock. After a local decoder reaches physical asset EOF during a looping session, a fresh decoder epoch needs an authoritative position/anchor.

Options are recorded in `CURRENT-STATE.md` and `HANDOFF-2026-09-13-M1G-START.md`:

- client EOF -> request fresh server STATE;
- server detects wrap -> proactively sends fresh STATE;
- client locally predicts/modulos wraps and later reconciles.

These have meaningful authority/latency/server-complexity tradeoffs. Do not choose silently.

### KI-052 — focused integrated M1G control/lifecycle proof is incomplete

Source paths now exist for pause/resume, seek/replacement, volume, stop, catch-up discard, decoder cancellation and renderer close cancellation. They still need the remaining deterministic integration coverage plus focused Minecraft acceptance, especially repeated seeks, long starvation, loop-wrap behavior after KI-051 is chosen, and actual positional output.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1H owns proactive out-of-range cleanup, late-entry discovery, return/rejoin at current server time, dimension/world/resource-reload recovery, robust general underrun rejoin, and final VS2 movement lifecycle.

M1G supplies local cancellation/restart primitives but does not absorb the full listener policy.

## Gated/later work

### KI-034 — FLAC desired but unproven

M1I gated. No final native FLAC analyzer/decode/seek/runtime path is proven.

### KI-018 — inherited multispeaker expected-member barrier can deadlock partial listeners

M1J.

### KI-011 / KI-012 / KI-023 — inherited finite engine/APIs remain legacy

M1L migration/removal.

## RAW/live/OpenAL/release issues

- KI-013: live stream volume can be applied twice — later live/OpenAL cleanup.
- KI-014 / KI-015 / KI-016 / KI-017 / KI-019: inherited HLS/TS/live lifecycle problems — M3.
- KI-022: separate `hqspeaker:hq_speaker` registration duplicates product direction — release cleanup after world-compatibility review.
- KI-025: top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0 — resolve provenance before public release; do not silently relicense.

## Reminders

- M1E final manual Minecraft PASS remains skipped/unrecorded.
- M1F focused Minecraft transport PASS remains unrecorded.
- M1G current integrated source is green but audible runtime PASS is not recorded.
- Old `FileFiniteAudioStream` / whole-track finite classes may remain for inherited/legacy paths but are not the modern prepared engine.
- Historical docs may intentionally describe earlier checkpoints; current source and current handoff/state override them.