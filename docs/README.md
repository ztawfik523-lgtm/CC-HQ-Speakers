# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G next and not started.**

Current branch:

`codex/m1f-finalization`

Current M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365` — NeoForge 21.1.247 and 21.1.248 both passed build/tests/package verification/artifact upload.

Focused real-Minecraft M1F transport acceptance is not recorded.

Read in this order:

1. `M1F-FINALIZATION-2026-09-13.md` — exact final M1F implementation/test/CI evidence;
2. `M1E-FINAL-HARDENING-2026-09-13.md` — final M1E authority/failure-path evidence;
3. `CURRENT-STATE.md` — current project snapshot;
4. `KNOWN-ISSUES.md` — active gaps after M1F;
5. `TESTING.md` — what deterministic/component tests prove;
6. `VERIFIED-FACTS.md` — fact ledger;
7. `M1F-IMPLEMENTATION-2026-09-13.md` — M1F architecture/implementation record;
8. `M1E-FINITE-STREAMING-DESIGN.md` — accepted M1E/M1F/M1G boundary;
9. `ROADMAP.md` — next milestones;
10. `LUA-API.md` — ComputerCraft programming surface;
11. `ARCHITECTURE.md` — accepted product architecture;
12. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
13. `FUTURE-CLEANUP.md` — parked obsolete/legacy cleanup.

Then re-read exact current source and current CI before changing implementation.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded by explicit owner decision.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1F audibility: not required; M1G owns decoding/rendering.
- Green CI is not runtime proof.

## Current architecture

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-authoritative finite timeline
-> authoritative STATE + encoded anchor
-> bounded client range requests
-> bounded off-thread server reads
-> bounded sliding client encoded RAM
-> M1G progressive decoder/PCM/renderer
```

## Historical context

`M1E-M1F-REEVALUATION-2026-09-13.md`, `HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`, older M1E/M1F handoffs, and pre-M1F preparation documents preserve the state/evidence at those checkpoints. Their reopened/provisional labels are historical and do not override the final M1E/M1F records.
