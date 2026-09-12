# M1E finalization checkpoint — 2026-09-13

This document records the final M1E review before M1F begins.

M1E is the **server-authoritative finite playback** milestone. It is not the final transport milestone and it is not the final MP3/WAV decoder milestone.

## Finalization scope

The 2026-09-13 pass deliberately does **not**:

- repair the temporary JavaSound/mp3spi decoder;
- implement M1F range transport;
- implement M1G progressive decoding/rendering;
- change the settled server-authority semantics.

The goal is to make the M1E checkpoint internally consistent, testable, auditable, and ready for one final focused Minecraft acceptance run.

## Pre-change recheck

The active M1E server implementation was re-read before changing anything.

The review confirmed:

- `HQFiniteMediaServer` owns `PLAYING`, `PAUSED`, `ENDED`, and `ERROR`;
- session construction sets known server duration and starts `FinitePlaybackClock` immediately;
- no renderer READY/STARTED handshake is required for canonical time to begin;
- client finite telemetry is only READY and diagnostic ERROR;
- READY only requests a fresh authoritative state snapshot;
- client ERROR is logged diagnostically and does not mutate canonical playback;
- pause/resume/seek/loop/volume operate against the server clock;
- non-looping `seek(duration)` immediately ends at duration;
- looping `seek(duration)` wraps to zero;
- natural non-looping EOF is determined from the known server duration;
- canonical EOF closes the transitional transfer before releasing the playback asset reference;
- replay after terminal END creates a fresh generation while the prepared asset reference remains valid;
- STOP clears the active finite session back to idle.

No server-authority correctness defect was found that justified changing M1E semantics.

## Runtime evidence recheck

The 2026-09-12 diagnostic logs were re-read as part of finalization.

They show repeated runs reaching the expected M1E server sequence, including:

- initial PLAYING;
- pause around the current canonical position;
- resume from the frozen canonical position;
- exact-end non-looping ENDED at `161.304` seconds;
- replay under a new generation;
- loop enable;
- exact-end looping wrap back near zero.

The same logs also show the old MP3 bridge reporting diagnostic client ERROR after its first PCM read returned no data. The server continued its authoritative state/control sequence despite that client failure, which is the intended M1E isolation property.

The logs still do not contain the ComputerCraft terminal success line, so they remain strong supporting evidence rather than the recorded runtime PASS.

## Decoder boundary remains unchanged

The temporary `FileFiniteAudioStream` / JavaSound / mp3spi path is known to be unreliable for this MP3 fixture:

- mp3spi reported `322.584` seconds for a roughly 2:45 source while the server analyzer reported `161.304` seconds;
- the current MP3 seek bridge mixes incompatible decoded-byte and compressed-frame/byte skip semantics;
- the bridge can report the requested seek position even when positioning terminated early;
- renderer restart currently performs a redundant second seek.

These are not M1E server-authority blockers.

The agreed rule remains:

**remember decoder requirements while designing later layers, but do not expect the temporary decoder to work correctly before M1G.**

## Finalization changes

Code/test candidate head before documentation-only finalization commits:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

This finalization pass intentionally changes only acceptance coverage:

1. `FinitePlaybackClockTest` explicitly proves that a started finite clock advances immediately without any renderer handshake.
2. `scripts/m1e_server_authority_test.lua`:
   - retains the original immediate PLAYING / progression / pause / resume / exact-end / loop-wrap checks;
   - verifies replay creates a newer generation for the same prepared asset;
   - verifies STOP returns finite status to `idle`;
   - writes an auditable PASS/FAIL result file in addition to terminal output.

The runtime result file defaults to:

```text
m1e_server_authority_result.txt
```

or may be supplied as the script's second argument.

A successful result file begins with:

```text
PASS
fixture=<path>
M1E server-authority contract passed
```

A failed run begins with `FAIL` and records the Lua traceback.

## Candidate CI/package proof

Exact finalization code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

GitHub Actions run:

`34725651930`

Both matrix jobs completed successfully:

- NeoForge 21.1.247 — build/tests, packaged-mod verification, and candidate-JAR upload passed;
- NeoForge 21.1.248 — build/tests, packaged-mod verification, and candidate-JAR upload passed.

Baseline 21.1.247 artifact:

`hqspeaker-neoforge-21.1.247`

Extracted JAR:

`hqspeaker-1.1.4-1.21.1-neoforge.jar`

SHA-256:

`cb661c4a9a132f236edb3a526c16b887f853f283af80db062ba6a84adfc33b21`

The 21.1.247 artifact is the preferred candidate for the final baseline Minecraft acceptance run. NeoForge 21.1.248 remains CI/package compatibility evidence.

## Final runtime acceptance command

Run on the exact target stack using a finite fixture longer than one second:

```text
m1e_server_authority_test <small-mp3-or-wav>
```

For an explicit result path:

```text
m1e_server_authority_test <small-mp3-or-wav> <result-file>
```

The temporary decoder may fail or sound wrong during this M1E test. That is not itself an M1E failure. The acceptance target is the server-authority contract checked by the script.

Do not call M1E Minecraft-runtime PASS until the final candidate is actually run and the terminal/result file reports PASS.

## Gate before M1F

M1F must not start until this final M1E acceptance result is captured.

Once M1E passes, the next milestone remains the agreed clean break:

```text
M1E — server authority
    -> M1F — bounded client-pulled encoded range transport
    -> M1G — progressive MP3/common-WAV decode + audible renderer
```

M1F does not need the current decoder to work and must not preserve the modern whole-file `.part/.media` bridge merely to keep temporary audible playback.

## After-review result

The implementation-side after-review for candidate `38cb2a4c...` confirms:

- CI passed NeoForge 21.1.247 and 21.1.248;
- packaged-mod verification passed on both;
- both candidate artifacts were uploaded;
- compared with the preparation head, the candidate changes only `scripts/m1e_server_authority_test.lua` and `FinitePlaybackClockTest.java`;
- no `HQFiniteMediaServer` semantic code changed;
- no finite packet semantic code changed;
- no M1F range transport was added;
- no decoder repair was added.

The remaining M1E gate is therefore runtime evidence only: execute the focused script using the exact 21.1.247 candidate above and preserve its PASS/FAIL result.

After a Minecraft PASS, record the runtime stack, fixture details, result file, and candidate JAR identity before starting M1F.
