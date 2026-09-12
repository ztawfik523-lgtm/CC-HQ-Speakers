# Documentation index

## Start here

For a new chat or implementation handoff, read these in order:

1. `M1E-FINALIZATION-2026-09-13.md` — **current checkpoint**. M1E semantics are re-reviewed; one final focused Minecraft PASS is required before M1F;
2. `CURRENT-STATE.md` — current implementation truth and immediate gate;
3. `VERIFIED-FACTS.md` — source/CI/runtime facts already frozen there;
4. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` — exact earlier runtime findings and evidence boundary;
5. `ARCHITECTURE.md` — accepted target architecture;
6. `M1E-SERVER-AUTHORITY.md` — exact M1E authority contract;
7. `M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ finite-streaming implementation contract;
8. `ROADMAP.md` — milestone order and acceptance boundaries;
9. `KNOWN-ISSUES.md` — unresolved defects and where they are scheduled;
10. `TESTING.md` — evidence and testing rules;
11. `FUTURE-CLEANUP.md` — old/temporary code and release cleanup to remember without expanding current scope;
12. `PRE-M1F-PREPARATION.md` — superseded preparation checkpoint preserving the clean-break/decoder decisions;
13. `NEXT-CHAT-HANDOFF.md` and `CHAT-HANDOFF-2026-09-12.md` — older deep-context handoffs.

Always re-read the exact current branch source and current CI before changing implementation. Current source/runtime evidence overrides older handoff wording.

## Documentation authority

Use this precedence when documents overlap:

1. successful exact-target Minecraft runtime evidence;
2. exact current source / current CI evidence;
3. `M1E-FINALIZATION-2026-09-13.md` for the current checkpoint;
4. `VERIFIED-FACTS.md` for facts already recorded from source/CI/runtime;
5. `CURRENT-STATE.md` for the current implementation snapshot;
6. `CC-T-COMPATIBILITY-CONTRACT.md` for standard speaker compatibility;
7. `ARCHITECTURE.md` and `M1E-FINITE-STREAMING-DESIGN.md` for accepted design;
8. `ROADMAP.md` for future milestone ordering;
9. milestone-specific historical docs;
10. older preparation/handoff/P0/prototype docs.

A green build is not Minecraft runtime proof. M1E remains runtime-pending until its focused final script actually produces PASS on the target stack.

## Current checkpoints

- frozen M1D implementation: `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green on both target NeoForge versions;
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`, CI `34658958488` green on both targets;
- diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`, CI `34686003774` green on both targets;
- M1E finalization code/test candidate before docs-only follow-up: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`;
- active branch: `codex/m1e-server-authoritative-finite`;
- M1E Minecraft runtime acceptance: final PASS still required;
- M1F implementation: not started.

## Current decisions to preserve

- the old JavaSound/mp3spi prepared decoder is temporary and not expected to work correctly before M1G;
- its reported MP3 duration is not authoritative;
- M1F should make a clean break from the modern whole-file `.part/.media` prepared path;
- M1F acceptance does not require audible finite playback;
- M1F must provide a bounded codec-agnostic encoded range/window contract which distinguishes temporary missing data from true asset EOF;
- M1G owns progressive MP3/common-WAV decode, pre-roll, bounded PCM, and actual audible rendering.

## Index

| Need | File |
|---|---|
| What is the current M1E finish gate? | `M1E-FINALIZATION-2026-09-13.md` |
| What exists now? | `CURRENT-STATE.md` |
| What did the earlier runtime diagnostic show? | `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` |
| What did M1E implement? | `M1E-SERVER-AUTHORITY.md` |
| How is the final M1E run tested? | `TESTING.md` |
| What old/temporary code should be cleaned later? | `FUTURE-CLEANUP.md` |
| What were the pre-M1F preparation decisions? | `PRE-M1F-PREPARATION.md` |
| What do we build next after M1E PASS? | `ROADMAP.md` |
| What is the target architecture? | `ARCHITECTURE.md` |
| What is the concrete M1F+ finite-streaming contract? | `M1E-FINITE-STREAMING-DESIGN.md` |
| What is the exact CC:T compatibility contract? | `CC-T-COMPATIBILITY-CONTRACT.md` |
| What does the M1A output/ownership slice guarantee? | `M1A-OUTPUT-OWNERSHIP.md` |
| How does reusable server-side media-asset storage work? | `M1B-MEDIA-ASSETS.md` |
| How do CC-local files become prepared reusable server assets? | `M1C-LOCAL-IMPORT.md` |
| What did frozen M1D media analysis implement? | `M1D-MEDIA-ANALYSIS.md` |
| What storage safety settings can the server owner configure? | `SERVER-CONFIG.md` |
| What is already verified? | `VERIFIED-FACTS.md` |
| What are the known problems? | `KNOWN-ISSUES.md` |
| Previous large fresh-chat handoff | `NEXT-CHAT-HANDOFF.md` |
| Handoff pointer | `CHAT-HANDOFF.md` |
| Longer dated handoff / deep context | `CHAT-HANDOFF-2026-09-12.md` |

Do not treat an old milestone or older handoff as the current product contract when newer exact source/runtime evidence exists.
