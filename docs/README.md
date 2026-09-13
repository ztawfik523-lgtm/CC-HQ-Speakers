# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G prepared but not started.**

Preparation branch:

`codex/m1g-preparation`

Preparation base/finalized-M1F documentation head:

`8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365` — both NeoForge targets passed.

Documentation-head run `34763711105` also passed both targets, and the owner-requested fresh rerun passed both target jobs again.

Focused Minecraft M1F transport acceptance is not recorded.

## Read in this order

1. `HANDOFF-2026-09-13-PRE-M1G.md` — current continuation handoff and stop condition;
2. `PRE-M1G-PREPARATION.md` — M1G source audit, design boundaries, owner decision gates, and acceptance plan;
3. `M1F-FINALIZATION-2026-09-13.md` — exact final M1F implementation/test/CI evidence;
4. `CURRENT-STATE.md` — current project snapshot;
5. `KNOWN-ISSUES.md` — active gaps;
6. `TESTING.md` — completed M1F evidence + planned M1G proof;
7. `VERIFIED-FACTS.md` — fact ledger;
8. `ROADMAP.md` — milestone sequence;
9. `M1E-FINITE-STREAMING-DESIGN.md` — accepted semantic/transport/decoder boundary;
10. `LUA-API.md` — ComputerCraft programming surface;
11. `ARCHITECTURE.md` — broader product architecture, with the stale-paragraph warning below;
12. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
13. exact current source and CI.

## M1G implementation stop condition

M1G Java/resource implementation has **not** started.

Before it starts, the owner must choose the decision gates documented in `PRE-M1G-PREPARATION.md`:

- Minecraft `AudioStream`/SoundManager vs direct Channel/OpenAL renderer;
- server-carried WAV layout vs client progressive WAV parsing;
- source sample rate vs 48 kHz normalization.

Do not silently pick among them.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded by explicit owner decision.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1F audibility: not required.
- M1G implementation/runtime: not started.
- Green CI is not runtime proof.

## Current architecture

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-authoritative finite timeline
-> authoritative STATE + encoded anchor
-> bounded client range requests
-> bounded off-thread server reads
-> bounded sliding encoded RAM
-> M1G decoder/converter worker
-> bounded mono PCM
-> positional renderer
```

## Architecture-document drift warning

Two older transitional `ARCHITECTURE.md` statements are superseded by final M1F source/evidence:

- active max range is 128 KiB, not the older 256 KiB starting-point wording;
- modern prepared transport no longer uses whole-file server push or client `.part/.media` bridging.

Use `M1F-FINALIZATION-2026-09-13.md`, current source, and the pre-M1G docs for those facts.

## Historical context

Older M1E/M1F reevaluation, preparation, and handoff documents preserve their checkpoint history. Their provisional/reopened language does not override the final M1E/M1F records or this pre-M1G preparation.
