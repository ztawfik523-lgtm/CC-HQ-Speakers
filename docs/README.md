# Documentation index

## Start here

Current checkpoint: **M1E/M1F reevaluation hold; M1G not started.**

The earlier labels `M1E finalized` and `M1F source/test/CI complete` are superseded by the 2026-09-13 reevaluation. The architecture and green CI remain valid; correctness/acceptance work is reopened.

Read in this order:

1. `M1E-M1F-REEVALUATION-2026-09-13.md` — current audit and reopened correctness/acceptance findings;
2. `HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md` — current continuation handoff and owner decision gate;
3. `CURRENT-STATE.md` — current project snapshot;
4. `KNOWN-ISSUES.md` — active reopened issues plus later gaps;
5. `TESTING.md` — what current tests actually prove and what remains missing;
6. `VERIFIED-FACTS.md` — source/CI/runtime facts ledger;
7. `M1E-SERVER-AUTHORITY.md` — server-authoritative finite semantics plus reevaluated failure paths;
8. `M1F-IMPLEMENTATION-2026-09-13.md` — implementation facts with corrected provisional status;
9. `M1E-FINITE-STREAMING-DESIGN.md` — accepted finite architecture/milestone split;
10. `ROADMAP.md` — current sequencing gate and later milestones;
11. `LUA-API.md` — current ComputerCraft/Lua programming surface;
12. `ARCHITECTURE.md` — accepted product architecture;
13. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
14. `FUTURE-CLEANUP.md` — parked obsolete/legacy cleanup.

Then re-read exact current source and current CI before changing implementation.

## Current checkpoints/evidence

- M1D frozen: `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green both targets.
- M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`, CI `34725651930` green both targets. Final focused Minecraft M1E PASS was skipped/unrecorded.
- pre-M1F documentation base: `bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`.
- current M1F Java/source head: `934e74b8ff619178d703f73df8a16ee97b3fc2af`.
- M1F code-head CI: `34731827907` green NeoForge 21.1.247 and 21.1.248, including package verification/artifact upload.
- pre-reevaluation documentation head: `d748287fe2a5bfa38fe5a3f1bad2a864fc7fc591`.
- active branch: `codex/m1f-demand-driven-finite`.
- M1F Minecraft runtime transport acceptance: not recorded.
- M1G implementation: not started.

The CI facts above remain valid. Their interpretation is narrower after reevaluation: they do not prove the newly identified exception/lifetime failure paths or the missing deterministic acceptance items.

## What remains correct from M1E/M1F

```text
server MediaAsset
-> server-authoritative finite timeline
-> authoritative STATE + encoded anchor
-> bounded range requests
-> bounded off-thread reads
-> bounded client encoded RAM
```

Protocol v5 remains the current transport. The modern prepared client no longer uses whole-song `.part/.media` files. Old modern CHUNK/END packets are removed. `audioPlayStaged()` remains removed.

M1E normal-path server authority remains present. M1F range transport remains the correct direction.

## Why M1E/M1F are provisional again

The reevaluation found:

- network packet-send exceptions are not fully isolated from canonical server transitions;
- `playPrepared()` rollback can leave installed finite session state inconsistent after a thrown start;
- playback reference bookkeeping clears its ownership flag before final release succeeds;
- M1F's encoded window lacks a sliding consume/discard operation that preserves unread prefetched data;
- current deterministic M1F tests cover only part of the original acceptance matrix.

See the reevaluation document for exact source reasoning.

## Evidence precedence

When documents conflict, trust:

1. exact target-stack runtime evidence;
2. exact current source;
3. current reevaluation record;
4. exact current CI/package evidence;
5. verified facts/current state;
6. architecture/design docs;
7. roadmap;
8. older milestone/finalization/handoff docs.

A green build is never by itself Minecraft runtime proof or proof of behavior not exercised by tests.

## Historical context

`HANDOFF-2026-09-13-M1F.md`, `HANDOFF-2026-09-13-PRE-M1F.md`, `M1E-FINALIZATION-2026-09-13.md`, `PRE-M1F-PREPARATION.md`, and older milestone documents preserve the evidence/decisions at those checkpoints. Their old completion labels are historical and do not override the reevaluation.
