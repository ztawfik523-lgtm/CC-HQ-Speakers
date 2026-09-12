# Documentation index

## Start here

For a new chat or implementation handoff, read these in order:

1. `PRE-M1F-PREPARATION.md` — **current preparation checkpoint**. Read this first before any attempt to finish M1E acceptance or start M1F;
2. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` — exact latest runtime findings, what they prove, and what they do not;
3. `CURRENT-STATE.md` — current implementation truth and immediate boundaries;
4. `VERIFIED-FACTS.md` — source/CI/runtime facts already frozen there;
5. `ARCHITECTURE.md` — accepted target architecture;
6. `M1E-SERVER-AUTHORITY.md` — exact M1E authority behavior and current runtime-pending status;
7. `M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ finite-streaming implementation contract;
8. `ROADMAP.md` — milestone order and acceptance boundaries;
9. `KNOWN-ISSUES.md` — unresolved defects and where they are scheduled;
10. `TESTING.md` — evidence and testing rules;
11. `FUTURE-CLEANUP.md` — old/temporary code and release cleanup to remember without expanding current scope;
12. `NEXT-CHAT-HANDOFF.md` — previous large standalone handoff, still useful for deep context but older than the current preparation checkpoint;
13. `CHAT-HANDOFF-2026-09-12.md` — longer dated deep-context handoff retained for historical detail.

The preparation checkpoint deliberately separates current decisions from implementation. At this moment, do not change M1E semantics, declare M1E runtime PASS, start M1F implementation, or repair the temporary decoder unless explicitly requested.

Always re-read the exact current branch source and current CI before changing implementation. Current source/runtime evidence overrides older handoff wording.

## Documentation authority

Use this precedence when documents overlap:

1. successful exact-target Minecraft runtime evidence;
2. exact current source / current CI evidence;
3. `PRE-M1F-PREPARATION.md` for current sequencing/scope decisions;
4. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` for the latest diagnostic evidence boundary;
5. `VERIFIED-FACTS.md` for facts already recorded from source/CI/runtime;
6. `CURRENT-STATE.md` for the current implementation snapshot;
7. `CC-T-COMPATIBILITY-CONTRACT.md` for standard speaker compatibility;
8. `ARCHITECTURE.md` and `M1E-FINITE-STREAMING-DESIGN.md` for accepted design;
9. `ROADMAP.md` for future milestone ordering;
10. milestone-specific historical docs for the code they describe;
11. older P0/prototype docs as historical evidence only.

A green build is not audible Minecraft runtime proof. Any milestone marked runtime-pending remains runtime-pending until its focused Minecraft contract is actually executed successfully.

## Current checkpoints

- frozen M1D implementation: `4a2cd5de96228fc091226c7e72fb669b82be258c`, run `34635484316` green on both target NeoForge versions;
- M1E semantic implementation checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`, run `34658958488` green on both target NeoForge versions;
- pre-preparation diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`, run `34686003774` green on both target NeoForge versions;
- active branch: `codex/m1e-server-authoritative-finite`;
- M1E Minecraft runtime acceptance: still pending;
- M1F implementation: not started;
- agreed next implementation sequencing when work resumes: finish/capture M1E authority acceptance, then M1F clean-break bounded range transport, then M1G progressive decoder/audio.

Important: commits after `d0e66ab...` are not all docs-only. Several later Java commits added runtime diagnostics without intentionally changing the authority architecture.

## Current decisions to preserve

- the old JavaSound/mp3spi prepared decoder is temporary and not expected to work correctly before M1G;
- its reported MP3 duration is not authoritative;
- M1F should make a clean break from the modern whole-file `.part/.media` prepared path rather than maintaining two prepared transports in parallel;
- M1F acceptance does not require audible finite playback;
- M1F must still provide a bounded codec-agnostic encoded range/window contract which distinguishes temporary missing data from true asset EOF;
- M1G owns progressive MP3/common-WAV decode, pre-roll, bounded PCM, and actual audible rendering.

## Index

| Need | File |
|---|---|
| What decisions/scope apply before implementation resumes? | `PRE-M1F-PREPARATION.md` |
| What did the latest M1E runtime diagnostic show? | `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` |
| What exists now? | `CURRENT-STATE.md` |
| What old/temporary code should we remember to clean later? | `FUTURE-CLEANUP.md` |
| Previous large plain-language fresh-chat handoff | `NEXT-CHAT-HANDOFF.md` |
| Handoff pointer | `CHAT-HANDOFF.md` |
| Longer dated handoff / deep context | `CHAT-HANDOFF-2026-09-12.md` |
| What did M1E implement? | `M1E-SERVER-AUTHORITY.md` |
| What do we build next? | `ROADMAP.md` |
| What is the target architecture? | `ARCHITECTURE.md` |
| What is the concrete M1F+ finite-streaming contract? | `M1E-FINITE-STREAMING-DESIGN.md` |
| What is the exact CC:T compatibility contract? | `CC-T-COMPATIBILITY-CONTRACT.md` |
| What does the M1A output/ownership slice guarantee? | `M1A-OUTPUT-OWNERSHIP.md` |
| How does reusable server-side media-asset storage work? | `M1B-MEDIA-ASSETS.md` |
| How do CC-local files become prepared reusable server assets? | `M1C-LOCAL-IMPORT.md` |
| What did frozen M1D media analysis implement? | `M1D-MEDIA-ANALYSIS.md` |
| What storage safety settings can the server owner configure? | `SERVER-CONFIG.md` |
| What is already recorded as verified source/runtime evidence? | `VERIFIED-FACTS.md` |
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

The documentation is deliberately split between current implementation truth, current preparation decisions, verified facts, accepted architecture/design, future roadmap, cleanup inventory, and historical/prototype evidence. Do not treat an old milestone document or older handoff as the current product contract when newer exact source/runtime evidence exists.
