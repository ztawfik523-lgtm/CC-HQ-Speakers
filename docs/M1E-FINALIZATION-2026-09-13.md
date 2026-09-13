# Historical M1E finalization checkpoint — 2026-09-13

> **Superseded status notice:** this document originally recorded M1E as finalized before M1F. The 2026-09-13 M1E/M1F reevaluation later found exception/lifetime failure-path issues in the current inherited M1E implementation. For current status, read `M1E-M1F-REEVALUATION-2026-09-13.md` and `CURRENT-STATE.md` first.

This file preserves the evidence from the original M1E finalization checkpoint.

## Original finalization result

M1E established the server-authoritative finite playback model:

- successful prepared playback starts the canonical server clock immediately;
- server owns pause/resume/seek/loop/volume/EOF;
- non-looping exact-duration seek ends;
- looping exact-duration seek wraps to zero;
- client READY only requests fresh server state;
- client ERROR is diagnostic only.

The temporary whole-file decoder/renderer bridge was known-bad for the tested MP3 and was intentionally deferred rather than repaired.

## Runtime diagnostic evidence

The 2026-09-12 diagnostic logs showed server state/control behavior consistent with PLAYING, PAUSED, resume, exact-end ENDED, replay under a newer generation, loop enable, and exact-end loop wrap while the old client MP3 bridge failed.

That supported authority separation but did **not** constitute the final focused M1E PASS.

## Finalization candidate

Exact candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

GitHub Actions:

`34725651930`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Baseline 21.1.247 candidate JAR SHA-256:

`cb661c4a9a132f236edb3a526c16b887f853f283af80db062ba6a84adfc33b21`

The candidate added deterministic immediate-clock coverage and strengthened `scripts/m1e_server_authority_test.lua`; it did not change the normal-path M1E semantics.

## Manual acceptance decision

The owner explicitly chose not to run the final strengthened M1E Minecraft acceptance script.

Therefore the historical evidence boundary remains:

```text
M1E final focused Minecraft PASS: skipped / not recorded
```

Do not rewrite that as a PASS.

## Later reevaluation findings

After M1F implementation, the current `HQFiniteMediaServer` was re-read and the normal-path M1E semantic model still looked correct. However, three shared failure-path issues were found:

1. runtime packet-send failures are not isolated from authoritative transitions;
2. `playPrepared()` can leave installed session/asset bookkeeping inconsistent if an exception occurs after session assignment;
3. playback asset ownership is marked released before `MediaAssetStore.release()` succeeds, losing retry knowledge on final-delete failure.

The current test tree also has deterministic clock tests but no dedicated `HQFiniteMediaServer` state-machine/failure-path test.

Accordingly, `M1E finalized` is now a **historical checkpoint label**, not the current project status.

## Current continuation

Read:

1. `M1E-M1F-REEVALUATION-2026-09-13.md`
2. `HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`
3. `CURRENT-STATE.md`
4. `TESTING.md`
5. exact current source/CI
