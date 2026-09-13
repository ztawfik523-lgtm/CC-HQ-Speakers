# Known issues / product gaps

This file tracks active gaps and where they belong. Source/CI evidence is not the same as Minecraft runtime proof.

Current checkpoint: **documentation/preparation only before M1F**.

M1E server-authority source/tests/CI are finalized and re-reviewed. The final manual M1E Minecraft script was not run; the project owner chose to skip it. Therefore M1E has **no recorded final runtime PASS**. The evidence gap remains documented, but it is no longer treated as a required sequencing gate before future M1F work.

M1F implementation has **not started**.

Read `HANDOFF-2026-09-13-PRE-M1F.md`, `LUA-API.md`, and `CURRENT-STATE.md` first.

## Resolved in current source

### Standard CC:T behavior

The normal speaker delegates `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` to CC:T's real speaker path. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

### M1E server authority

Current modern prepared finite playback is server-authoritative:

- server clock starts immediately;
- no renderer-observation authority/timeout;
- client READY only requests current state;
- client ERROR is diagnostic only;
- exact-end non-loop seek ends immediately;
- exact-end loop seek wraps to zero;
- canonical EOF closes temporary transfer ownership before playback asset release.

The final manual Minecraft M1E acceptance was skipped, so these are source/test/CI guarantees plus supporting diagnostic evidence, not a final recorded runtime PASS.

## Active finite replacement issues

### Temporary JavaSound/mp3spi bridge is unreliable

**Target: M1G replacement.**

The old prepared client bridge showed:

- clearly wrong MP3 duration for the tested fixture;
- incompatible seek/skip progress semantics;
- apparent seek success after incomplete positioning;
- redundant restart seek;
- first PCM read ending immediately in the 2026-09-12 diagnostic run.

Do not spend M1F repairing this disposable bridge just to preserve temporary audibility.

### Whole-file client transfer/cache still exists

**Target: M1F.**

Current modern prepared playback still uses the old full-file transfer and client `.part/.media` files before decoder construction.

M1F replaces this with bounded client-requested encoded ranges kept in temporary RAM.

### Server finite file reads still happen from tick-driven transfer code

**Target: M1F.**

M1F moves asset reads to bounded background IO and keeps large file reads off the server tick.

### No final demand-driven finite protocol yet

**Target: M1F.**

M1F must add bounded range requests/responses, current playback/generation/asset/relevance validation, bounded outstanding work, stale-result discard, safe asset lifetime, arbitrary offsets, and bounded client encoded memory.

### M1F background reads must not race server media-store shutdown

**Target: M1F.**

When M1F adds background server reads, those reads must stop/drain/cancel before the shared media store closes and removes asset files during server shutdown.

This must be covered by lifecycle tests.

### Temporary missing MP3 bytes must not look like EOF

**Targets: M1F data contract + M1G decoder behavior.**

The encoded-window layer must distinguish:

- data available;
- data not arrived yet;
- true asset EOF;
- stale/cancelled playback.

M1G then waits/refills instead of treating network starvation as permanent EOF.

### MP3 seek/rejoin needs earlier-frame pre-roll

**Targets: M1F anchors + M1G decode.**

M1F must support requesting an earlier server-selected encoded anchor. M1G performs the Layer III pre-roll/discard needed to reconstruct decoder state.

### Historical WAV support is broader than the final converter target

**Target: M1G.**

Final prepared/local WAV support should match the actual progressive converter: mono/stereo common PCM integer formats plus float32.

### FLAC is wanted but not proven

**Target: M1I.**

Do not advertise native FLAC until its analyzer, progressive decoder, seek/rejoin, malformed-input, packaging, and runtime path is proven.

## API / prototype issue

### `audioPlayStaged()` still exists but is approved for removal

**Target: M1F.**

This command is **our own staged-file prototype API**, not original HQ Speakers compatibility. It directly plays a temporary staged file instead of using the reusable server-asset flow.

Project decision: remove it when M1F implementation begins. New programs use `hq.playFile()` or prepare/play/release.

No removal has happened yet at the documentation checkpoint.

## Dynamic listener issue

A client can still leave the old fixed-recipient/range-local path before later invalidation and retain stale local state.

**Target: M1H.**

Leaving range should stop local demand/rendering and free temporary buffers; returning should join current server time only if playback is still active.

## Retained legacy engine issues

The inherited legacy finite/live/multispeaker code still has problems which should not expand M1F scope:

- unbounded/old finite decode task behavior;
- whole decoded PCM allocation scaling with track length;
- inherited finite byte APIs with one-shot limits;
- old multispeaker expected-member/tap behavior;
- double-applied stream gain;
- HLS progression problems;
- non-incremental TS behavior;
- unsupported TS decode paths;
- incomplete live lifecycle/truth.

Targets remain M1L/M1J/M3/M1N as appropriate.

## Product/cleanup issues

### Historical format advertisement

Frozen M1D includes OGG/AIFF/AU analysis. Final active prepared/local core is intended to become MP3 + implemented common WAV in M1G, with optional native FLAC later.

### Separate custom HQ block

The repo still registers `hqspeaker:hq_speaker` even though the product direction upgrades normal CC:T speakers.

**Target: M4 decision**, including existing-world/registry compatibility.

### Documentation drift

Current continuation entry points are now:

- `HANDOFF-2026-09-13-PRE-M1F.md`
- `LUA-API.md`
- `CURRENT-STATE.md`

Older handoffs/preparation docs are historical/superseded. Exact current source and CI take precedence.

### License metadata mismatch

Top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0.

**Target: M4.** Resolve from actual provenance; do not silently relicense.

## Dedicated cleanup inventory

See `FUTURE-CLEANUP.md` for the broader parked cleanup list. It is a reminder, not permission to expand the active milestone.