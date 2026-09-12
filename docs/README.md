# Documentation index

## Start here

For a new chat or implementation handoff, read these in order:

1. `CHAT-HANDOFF-2026-09-12.md` — complete continuation context and settled decisions;
2. `CURRENT-STATE.md` — current implementation truth and immediate next work;
3. `VERIFIED-FACTS.md` — source/CI facts only;
4. `ARCHITECTURE.md` — accepted target architecture;
5. `M1E-FINITE-STREAMING-DESIGN.md` — concrete finite-streaming implementation contract;
6. `ROADMAP.md` — milestone order and acceptance boundaries;
7. `KNOWN-ISSUES.md` — unresolved defects and where they are scheduled.

Always re-read the current branch source before changing implementation. The handoff records the state at the time it was written; source on the active branch wins if the branch later moves.

## Documentation authority

Use this precedence when documents overlap:

1. exact current source / current CI evidence;
2. `VERIFIED-FACTS.md` for facts already recorded from source/CI/runtime;
3. `CURRENT-STATE.md` for the current implementation snapshot;
4. `ARCHITECTURE.md` and `M1E-FINITE-STREAMING-DESIGN.md` for accepted design;
5. `ROADMAP.md` for future milestone ordering;
6. milestone-specific historical docs for the code they describe;
7. older P0/prototype docs as historical evidence only.

A green build is not audible Minecraft runtime proof. Any milestone marked runtime-pending remains runtime-pending until its focused Minecraft contract is actually executed successfully.

## Index

| Need | File |
|---|---|
| Complete new-chat handoff | `CHAT-HANDOFF-2026-09-12.md` |
| What exists now? | `CURRENT-STATE.md` |
| What do we build next? | `ROADMAP.md` |
| What is the target architecture? | `ARCHITECTURE.md` |
| What is the concrete M1E+ finite-streaming contract? | `M1E-FINITE-STREAMING-DESIGN.md` |
| What did M1E actually implement? | `M1E-SERVER-AUTHORITY.md` |
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
