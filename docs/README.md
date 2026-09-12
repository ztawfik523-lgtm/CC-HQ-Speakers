# Documentation index

## Start here

For a new chat or implementation handoff, read these in order:

1. `CHAT-HANDOFF-2026-09-12.md` — complete continuation context, exact checkpoints, settled decisions, and next-step instructions;
2. `CURRENT-STATE.md` — current implementation truth and immediate next work;
3. `VERIFIED-FACTS.md` — source/CI/runtime facts only;
4. `ARCHITECTURE.md` — accepted target architecture;
5. `M1E-SERVER-AUTHORITY.md` — exact source/test/CI-complete M1E behavior;
6. `M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ finite-streaming implementation contract;
7. `ROADMAP.md` — milestone order and acceptance boundaries;
8. `KNOWN-ISSUES.md` — unresolved defects and where they are scheduled;
9. `TESTING.md` — evidence and testing rules.

Always re-read the current branch source and current CI before changing implementation. The handoff records the project state at the time it was written; source on the active branch wins if the branch later moves.

## Documentation authority

Use this precedence when documents overlap:

1. successful exact-target Minecraft runtime evidence;
2. exact current source / current CI evidence;
3. `VERIFIED-FACTS.md` for facts already recorded from source/CI/runtime;
4. `CURRENT-STATE.md` for the current implementation snapshot;
5. `CC-T-COMPATIBILITY-CONTRACT.md` for standard speaker compatibility;
6. `ARCHITECTURE.md` and `M1E-FINITE-STREAMING-DESIGN.md` for accepted design;
7. `ROADMAP.md` for future milestone ordering;
8. milestone-specific historical docs for the code they describe;
9. older P0/prototype docs as historical evidence only.

A green build is not audible Minecraft runtime proof. Any milestone marked runtime-pending remains runtime-pending until its focused Minecraft contract is actually executed successfully.

## Current checkpoints

- frozen M1D implementation: `4a2cd5de96228fc091226c7e72fb669b82be258c`, run `34635484316` green on both target NeoForge versions;
- M1E exact code-bearing implementation: `d0e66ab9135359627086c13647d5241ad778643f`, run `34658958488` green on both target NeoForge versions;
- active branch: `codex/m1e-server-authoritative-finite`;
- M1E Minecraft runtime acceptance: still pending;
- next implementation milestone: **M1F demand-driven finite transport**.

Later documentation-only commits may move the branch head. Keep the exact code-bearing milestone commit above as the implementation proof anchor.

## Index

| Need | File |
|---|---|
| Complete new-chat handoff | `CHAT-HANDOFF-2026-09-12.md` |
| What exists now? | `CURRENT-STATE.md` |
| What did M1E actually implement? | `M1E-SERVER-AUTHORITY.md` |
| What do we build next? | `ROADMAP.md` |
| What is the target architecture? | `ARCHITECTURE.md` |
| What is the concrete M1F+ finite-streaming contract? | `M1E-FINITE-STREAMING-DESIGN.md` |
| What is the exact CC:T compatibility contract? | `CC-T-COMPATIBILITY-CONTRACT.md` |
| What does the M1A output/ownership slice guarantee? | `M1A-OUTPUT-OWNERSHIP.md` |
| How does reusable **server-side** media-asset storage work? | `M1B-MEDIA-ASSETS.md` |
| How do CC-local files become prepared reusable server assets? | `M1C-LOCAL-IMPORT.md` |
| What did frozen M1D media analysis implement? | `M1D-MEDIA-ANALYSIS.md` |
| What storage safety settings can the server owner configure? | `SERVER-CONFIG.md` |
| What is proven by source/runtime evidence? | `VERIFIED-FACTS.md` |
| What are the known problems? | `KNOWN-ISSUES.md` |
| General testing rules | `TESTING.md` |
| Historical P0 design decisions | `P0-DESIGN-DECISIONS.md` |
| Historical/baseline P0 test matrix | `P0-TEST-MATRIX.md` |
| What did the M0.5 cleanup milestone do? | `M0.5-CLEANUP.md` |
| M0 runtime baseline | `M0-SMOKE-TEST.md` |
| Historical M1 runtime procedure | `M1-RUNTIME-TEST.md` |
| Historical staged finite prototype acceptance | `M1A-LOCAL-FINITE-TEST.md` |
| Source/provenance references | `SOURCES.md` |
| Transferable HighAudio work | `research/HIGHAUDIO-TRANSFERABLE-FINDINGS.md` |
| Existing SPR baseline | `research/SPR-INTEGRATION-BASELINE.md` |

The documentation is deliberately split between current implementation truth, verified facts, accepted architecture/design, future roadmap, and historical/prototype evidence. Do not treat an old milestone document as the current product contract.
