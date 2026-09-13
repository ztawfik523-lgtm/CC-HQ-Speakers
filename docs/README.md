# Documentation index

## Start here

Current checkpoint: **M1F source/test/CI complete; M1G not started.**

Read in this order:

1. `M1F-IMPLEMENTATION-2026-09-13.md` — exact M1F implementation/evidence boundary;
2. `HANDOFF-2026-09-13-M1F.md` — fresh plain-language continuation handoff;
3. `LUA-API.md` — current ComputerCraft/Lua programming surface;
4. `CURRENT-STATE.md` — current project snapshot;
5. `VERIFIED-FACTS.md` — source/CI/runtime facts ledger;
6. `ARCHITECTURE.md` — accepted product architecture;
7. `M1E-SERVER-AUTHORITY.md` — server-authoritative finite semantics retained under M1F;
8. `M1E-FINITE-STREAMING-DESIGN.md` — finite streaming design and milestone split;
9. `ROADMAP.md` — next milestone ordering;
10. `KNOWN-ISSUES.md` — unresolved problems;
11. `TESTING.md` — evidence/testing rules;
12. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
13. `FUTURE-CLEANUP.md` — parked obsolete/legacy cleanup.

Then re-read exact current source and current CI before changing implementation.

## Current checkpoints

- M1D frozen: `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green both targets.
- M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`, CI `34725651930` green both targets. Final manual Minecraft M1E PASS was skipped and must not be claimed.
- Pre-M1F documentation base: `bd6d6c68cf46bf16a702a0f6c4d0a3075fd09969`.
- M1F final code/test head before docs: `934e74b8ff619178d703f73df8a16ee97b3fc2af`.
- M1F CI: `34731827907` green NeoForge 21.1.247 and 21.1.248, including package verification/artifact upload.
- active branch: `codex/m1f-demand-driven-finite`.
- M1F Minecraft runtime acceptance: not recorded.
- M1G implementation: not started.

## What M1F means

The modern prepared-file client no longer downloads/writes the complete song before future decode.

```text
server MediaAsset
-> authoritative STATE + encoded anchor
-> bounded range requests
-> bounded off-thread reads
-> bounded client encoded RAM
```

Protocol v5 replaces old finite CHUNK/END whole-file transfer with range request/data packets.

The old project-specific `audioPlayStaged()` direct-play command is removed. The intended API remains `hq.playFile()` or prepare/play/release.

M1G will add progressive MP3/common-WAV decode and actual sound.

## Evidence precedence

When documents conflict, trust:

1. exact target-stack runtime evidence;
2. exact current source;
3. exact current CI/package evidence;
4. current implementation checkpoint docs;
5. verified facts;
6. current state;
7. architecture/design docs;
8. roadmap;
9. older milestone/handoff docs.

A green build is never by itself Minecraft runtime proof.

## Historical context

`HANDOFF-2026-09-13-PRE-M1F.md`, `M1E-FINALIZATION-2026-09-13.md`, `PRE-M1F-PREPARATION.md`, older dated handoffs, and milestone documents preserve the decisions/evidence that existed at those checkpoints. They are historical once newer current-state documents supersede them.
