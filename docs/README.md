# Documentation index

## Start here

Current checkpoint: **M1E source/test/CI complete; M1F completion/acceptance provisional; M1G not started.**

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Final M1E code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

The final focused Minecraft M1E acceptance script was explicitly skipped by the owner, so there is no recorded final runtime PASS.

Read in this order:

1. `M1E-FINAL-HARDENING-2026-09-13.md` — definitive M1E completion/evidence record;
2. `CURRENT-STATE.md` — current project snapshot;
3. `KNOWN-ISSUES.md` — active M1F/later gaps and resolved M1E findings;
4. `TESTING.md` — what current tests prove and the runtime evidence boundary;
5. `VERIFIED-FACTS.md` — exact source/CI/package facts ledger;
6. `M1E-SERVER-AUTHORITY.md` — current server-authoritative finite contract;
7. `M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit which found the M1E issues now fixed and M1F issues still open;
8. `HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md` — resolved handoff summary;
9. `M1F-IMPLEMENTATION-2026-09-13.md` — range transport implementation checkpoint;
10. `M1E-FINITE-STREAMING-DESIGN.md` — accepted finite architecture/milestone split;
11. `ROADMAP.md` — current sequencing and later milestones;
12. `LUA-API.md` — ComputerCraft/Lua programming surface;
13. `ARCHITECTURE.md` — accepted product architecture;
14. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
15. `FUTURE-CLEANUP.md` — parked obsolete/legacy cleanup.

Then re-read exact current source and CI before changing implementation.

## Current evidence

- M1D frozen: `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green both targets.
- historical first M1E finalization candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`, CI `34725651930` green both targets.
- M1F transport checkpoint before reevaluation: `934e74b8ff619178d703f73df8a16ee97b3fc2af`.
- final M1E hardening code: `521d4323d9216c8a99e8ec60426997c3330c4068`.
- final M1E hardening CI: `34757923455`, green both targets.
- baseline 21.1.247 final-hardening JAR SHA-256: `da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`.
- current hardening branch: `codex/m1e-final-hardening`.
- M1E final focused Minecraft runtime acceptance: skipped / no recorded PASS.
- M1F focused Minecraft range-transport acceptance: not recorded.
- M1G implementation: not started.

## What is settled

```text
server MediaAsset
-> server-authoritative finite timeline
-> authoritative STATE + encoded anchor
-> bounded range requests
-> bounded off-thread reads
-> bounded client encoded RAM
```

M1E server authority is now hardened against projection failures, ghost starts, failed final-release ownership loss, and ERROR clock advancement.

Protocol v5 remains the current M1F transport. The modern prepared client does not use whole-song `.part/.media` files. Old modern CHUNK/END packets and the project-prototype `audioPlayStaged()` route remain removed.

## What is still provisional

M1F still needs:

- a true sliding consume/discard encoded window which preserves useful unread prefetched bytes;
- remaining deterministic component acceptance around request identity/relevance/stale completion, shutdown integration, packet bounds/codecs, and client anchor gating;
- focused Minecraft range-transport acceptance if runtime proof is wanted.

M1G has not started and still owns progressive decoding and audible positional rendering.

## Evidence precedence

When documents conflict, trust:

1. exact target-stack runtime evidence;
2. exact current source;
3. current final-hardening/current-state records;
4. exact current CI/package evidence;
5. verified facts/testing docs;
6. architecture/design docs;
7. roadmap;
8. older checkpoint/finalization/handoff docs.

A green build is never by itself Minecraft runtime proof.

## Historical context

Older handoff/finalization/preparation documents preserve the evidence and assumptions at those checkpoints. In particular, the reevaluation was a valid audit, but its M1E "reopened" status has been superseded by the completed hardening pass. Its M1F completeness findings remain relevant.
