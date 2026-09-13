# Handoff — M1E/M1F reevaluation resolution — 2026-09-13

This file replaces the old active handoff state which paused on the M1E/M1F reevaluation decision.

## What happened

The reevaluation correctly found shared M1E/M1F failure-path issues and separate M1F completeness gaps.

The owner then chose to finish M1E and asked for repeated source review before finalizing it.

M1E is now complete at the source/test/CI level.

## Exact M1E final code

Branch:

`codex/m1e-final-hardening`

Code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Baseline 21.1.247 JAR SHA-256:

`da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`

## M1E findings closed

The hardening pass resolved:

- client/network projection escaping canonical transitions;
- one failing broadcast recipient aborting later recipients;
- prepared-start ghost-session/rollback inconsistency;
- failed final MediaAsset release losing its retry owner;
- detached/rejected/in-flight release paths with the same ownership problem;
- terminal server ERROR continuing to advance position.

Production semantics are now concentrated in `FinitePlaybackStateMachine`, with deterministic coverage for server-owned start/time/controls/EOF/error behavior.

## Runtime evidence boundary

Do not claim a final M1E Minecraft PASS.

The strengthened final script was explicitly skipped by the owner. Historical runtime diagnostics still support authority separation, but they are not the skipped final acceptance run.

Use:

```text
M1E source/test/CI: complete
M1E final manual Minecraft acceptance: skipped / no recorded PASS
```

## M1F remains provisional

Do not treat the resolved M1E audit as resolving M1F.

Current M1F architecture remains the correct direction:

```text
server MediaAsset
-> bounded client range demand
-> off-thread bounded server read
-> bounded client encoded RAM
-> future progressive decoder
```

Still-open M1F work:

- convert `FiniteRangeWindow` from reset/re-anchor-only progression into a true sliding consume/discard window;
- fill the remaining deterministic acceptance matrix for complete request identity/relevance/stale completion, shutdown integration, packet bounds/codecs, and client anchor gating;
- focused Minecraft range-transport acceptance remains unrecorded.

M1F may remain silent; M1G owns decode/PCM/rendering.

## M1G/M1H boundaries

M1G has not started. It owns progressive MP3/common-WAV decode, starvation-vs-EOF, MP3 pre-roll, bounded mono PCM, cancellation, and positional rendering.

M1H still owns full listener late-entry / leave-return / rejoin / dimension and reload lifecycle.

## Read order

1. `M1E-FINAL-HARDENING-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-M1F-REEVALUATION-2026-09-13.md`
8. `M1F-IMPLEMENTATION-2026-09-13.md`
9. `ROADMAP.md`
10. exact current source/CI
