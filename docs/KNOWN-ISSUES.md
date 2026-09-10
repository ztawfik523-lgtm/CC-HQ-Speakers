# Known issues / product gaps

## KI-001 — finite media is capped at 8 MiB twice

Source:
- `HQSpeakerPeripheral.SPEAKER_MAX_AUDIO = 8 MiB`
- `HQSpeakerAudioPacket.MAX_BYTES = 8 MiB`

The current finite path sends one complete encoded file in one packet.

Desired result:
- ordinary and long tracks are practical;
- encoded transfer and decoded memory remain bounded.

This requires more than blindly increasing a constant.

## KI-002 — `setLooping` is not end-to-end looping

Source audit:
- setter changes only a server-side boolean;
- packet has no looping state;
- client `HQSpeakerSound` sets `looping = false`.

Desired result:
- finite playback actually repeats reliably;
- loop can be changed predictably;
- stop/replay/EOF state remains correct.

M1 candidate status: implemented with retained-PCM cursor rewind and
generation-aware live control; real-client acceptance remains pending.

## KI-003 — `speakIsPlaying` is not truthful finite playback state

Current implementation reports server queue/stream state, not client finite renderer state.

Desired result:
- a player program can query useful status such as loading/buffering/playing/paused/stopped/ended/error.

M1 candidate status: `audioStatus()` now exposes semantic state plus renderer
observation, and finite `speakIsPlaying()` follows loading/playing/paused state.

## KI-004 — no coherent pause/resume/seek/position/duration control

Desired result:
- a real player-oriented API;
- old CC:HQ methods retained as compatibility wrappers where practical.

M1 candidate status: implemented for finite media. Raw PCM and live streams do
not claim finite duration/seek/loop semantics.

## KI-005 — finite decode materializes complete PCM

Current OGG uses full-memory `stb_vorbis_decode_memory`; JavaSound uses `readAllBytes()`.

Decoded finite PCM has a 64 MiB cap.

Desired result:
- long compressed media does not require whole-track decoded PCM where that becomes impractical.

## KI-006 — wait/underrun/EOF semantics need audit

`HQAudioStream.read()` can synthesize silence while data is unavailable.

Desired result:
- buffering is not confused with EOF;
- EOF is not confused with failure;
- repeat does not get stuck in permanent silence;
- player status reflects the real condition.

Related prior research:
See `research/HIGHAUDIO-TRANSFERABLE-FINDINGS.md`. The broader CC:HQ lineage
already exposed this same silence/wait-vs-completion ambiguity as a serious
lifecycle defect class, so future work should reuse that evidence rather than
treating it as a brand-new problem.

## KI-007 — no productized SPR support

Existing `cchq-soundphysics-compat` work already solves much of the acoustic problem.

Needed:
- productize, do not re-research;
- connect new player lifecycle;
- preserve V7.1 acoustics;
- retain hardening.

## KI-008 — repository hygiene

Inherited tracked/generated/local directories include:
- `.gradle/`
- `.idea/`
- `build/`

Add a proper `.gitignore` and untrack them in bootstrap.

## KI-009 — target dependency versions (M0 resolved)

Inherited:
- NeoForge 21.1.211
- CC:T 1.113.1

Target:
- NeoForge 21.1.247 / 21.1.248
- CC:T 1.120.0

Both exact builds and dedicated-server startup checks pass. The NeoForge
21.1.247 client smoke run also passed mod loading, API registration, PCM8,
PCM16, volume, finite MP3 playback, and clean shutdown. Its remaining skipped
or inconclusive cases are recorded in `M0-SMOKE-TEST.md`.

## KI-010 — license metadata mismatch

Top-level repository license is MPL-2.0 while mod metadata says LGPL-3.0.

Investigate provenance and preserve obligations before release.
