# M1E finalization checkpoint — 2026-09-13

This document records the final M1E review before M1F.

M1E is the **server-authoritative finite playback** milestone. It is not the final transport milestone and it is not the final MP3/WAV decoder milestone.

## Finalization result

The M1E implementation was re-read before finalization. No server-authority correctness defect was found that justified changing the M1E semantics.

The review confirmed that the server owns the finite timeline and semantic state: successful prepared playback starts immediately, pause/resume/seek/loop/volume are server-owned, non-looping exact-duration seek ends immediately, looping exact-duration seek wraps to zero, natural EOF comes from the known server duration, client READY only requests current server state, and client ERROR is diagnostic rather than canonical authority.

The temporary whole-file decoder/renderer bridge remains known-bad for the tested MP3 and is intentionally deferred to M1G rather than repaired merely for M1E/M1F audibility.

## Runtime diagnostic evidence

The 2026-09-12 diagnostic logs showed repeated server state/control behavior consistent with the M1E authority contract, including PLAYING, PAUSED, resume, exact-end ENDED, replay under a newer generation, loop enable, and exact-end loop wrap near zero.

The same run showed the old client MP3 bridge failing after renderer submission/first PCM read. The server timeline continued independently, which is the intended authority separation.

Those logs do **not** contain the focused ComputerCraft script success result and therefore are not a recorded M1E runtime PASS.

## Finalization candidate

Exact code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

GitHub Actions run:

`34725651930`

Both target jobs passed:

- NeoForge 21.1.247 — build/tests/package verification/artifact upload;
- NeoForge 21.1.248 — build/tests/package verification/artifact upload.

Baseline 21.1.247 candidate JAR SHA-256:

`cb661c4a9a132f236edb3a526c16b887f853f283af80db062ba6a84adfc33b21`

The finalization candidate changed acceptance coverage only: it added a deterministic immediate-clock test and strengthened `scripts/m1e_server_authority_test.lua`. It did not change `HQFiniteMediaServer` semantics, finite packet semantics, M1F transport, or decoder behavior.

## Prepared manual acceptance harness

The focused script is:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav> [result-file]
```

It checks immediate canonical PLAYING/progression, pause/resume, exact-end non-loop END, replay generation/asset identity, exact-end loop wrap, STOP -> idle, and prepared release. It can write an auditable PASS/FAIL result file.

A successful result would begin with:

```text
PASS
fixture=<path>
M1E server-authority contract passed
```

## Later project decision: manual M1E run skipped

After the finalization candidate was prepared, the project owner explicitly chose **not to run the final manual M1E Minecraft acceptance test** and to move forward later instead.

The status must therefore be stated precisely:

```text
M1E source/tests/CI: finalized
M1E manual Minecraft acceptance: skipped / no recorded PASS
M1F implementation: not started at the current documentation checkpoint
```

The missing manual PASS remains an evidence gap. By explicit project decision, it is no longer treated as a sequencing blocker before future M1F work unless that decision changes.

Do not claim the script was run successfully. Do not erase the diagnostic evidence gap.

## Decoder boundary

The temporary `FileFiniteAudioStream` / JavaSound / mp3spi path remains disposable. Known findings include wrong MP3 duration for the tested fixture, incompatible seek/skip units, false apparent seek success after incomplete positioning, redundant restart seek, and first-read failure.

M1G owns the replacement progressive MP3/common-WAV decoder and audible path.

## Next milestone direction

The milestone split remains:

```text
M1E — server authority
    -> M1F — bounded client-pulled encoded range transport
    -> M1G — progressive MP3/common-WAV decode + audible renderer
```

M1F should make a clean break from the modern whole-file `.part/.media` path and does not need the old decoder to remain audible.

The old direct-staging command `audioPlayStaged()` was later confirmed to be this project's own staged-prototype API, not inherited HQ Speakers compatibility. Project decision: remove it when M1F implementation starts. New programs use `hq.playFile()` or prepare/play/release.

For the current continuation state, read `HANDOFF-2026-09-13-PRE-M1F.md`, `LUA-API.md`, and `CURRENT-STATE.md` before this historical finalization record.