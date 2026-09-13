# Historical M1E finalization checkpoint — 2026-09-13

> **Historical record:** this document preserves the first M1E finalization checkpoint. A later reevaluation correctly reopened M1E failure-path hardening, and that work was subsequently completed. The definitive current M1E record is `M1E-FINAL-HARDENING-2026-09-13.md`.

## Original finalization result

The first M1E finalization established the server-authoritative finite playback model:

- successful prepared playback starts the canonical server clock immediately;
- server owns pause/resume/seek/loop/volume/EOF;
- non-looping exact-duration seek ends;
- looping exact-duration seek wraps to zero;
- client READY only requests fresh server state;
- client ERROR is diagnostic only.

The temporary whole-file decoder/renderer bridge was known-bad for the tested MP3 and was intentionally deferred rather than repaired.

## Historical runtime diagnostic evidence

The 2026-09-12 diagnostic logs showed server state/control behavior consistent with PLAYING, PAUSED, resume, exact-end ENDED, replay under a newer generation, loop enable, and exact-end loop wrap while the old client MP3 bridge failed.

That supported authority separation but did **not** constitute the final focused M1E PASS.

## Original candidate

Original finalization candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

GitHub Actions:

`34725651930`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Historical baseline 21.1.247 candidate JAR SHA-256:

`cb661c4a9a132f236edb3a526c16b887f853f283af80db062ba6a84adfc33b21`

## Manual acceptance decision

The owner explicitly chose not to run the final strengthened M1E Minecraft acceptance script.

That has not changed. The evidence boundary remains:

```text
M1E final focused Minecraft PASS: skipped / not recorded
```

Do not rewrite that as a PASS.

## Later reevaluation

The subsequent reevaluation found failure-path issues which the original checkpoint had not proven:

1. client packet-send failure isolation;
2. prepared-start atomicity / ghost-session possibility;
3. retry ownership after failed final MediaAsset deletion;
4. terminal ERROR position freeze.

Those findings were valid and temporarily superseded the first `M1E finalized` status label.

## Final hardening resolution

The reopened M1E findings are now resolved by the hardening sequence ending at:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Exact final code CI:

`34757923455`

Both NeoForge targets passed build, complete tests, package verification, and artifact upload.

The definitive final-hardening record includes production state-machine tests, best-effort/per-recipient client projection, retry-safe asset release ownership, prepared-start atomicity, and ERROR clock freeze.

Therefore this file is useful historical evidence for the first finalization attempt, but it no longer defines current status.

## Current continuation

Read:

1. `M1E-FINAL-HARDENING-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `TESTING.md`
4. `KNOWN-ISSUES.md`
5. exact current source/CI
