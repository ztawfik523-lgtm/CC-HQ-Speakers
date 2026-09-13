# Documentation index

## Start here

For a new chat or implementation handoff, read these in order:

1. `HANDOFF-2026-09-13-PRE-M1F.md` — **current handoff and checkpoint**, plain-language first;
2. `LUA-API.md` — current ComputerCraft/Lua programming surface, recommended vs low-level vs legacy/prototype;
3. `CURRENT-STATE.md` — current implementation truth and exact pre-M1F status;
4. `VERIFIED-FACTS.md` — source/CI/runtime facts already frozen there;
5. `ARCHITECTURE.md` — accepted target architecture;
6. `M1E-SERVER-AUTHORITY.md` — exact M1E authority contract;
7. `M1E-FINITE-STREAMING-DESIGN.md` — concrete M1F+ finite-streaming implementation contract;
8. `ROADMAP.md` — milestone order and acceptance boundaries;
9. `KNOWN-ISSUES.md` — unresolved defects and where they are scheduled;
10. `TESTING.md` — evidence and testing rules;
11. `FUTURE-CLEANUP.md` — old/temporary code and release cleanup to remember without expanding current scope;
12. `M1E-FINALIZATION-2026-09-13.md` — M1E finalization review and candidate evidence;
13. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` — exact earlier runtime findings and evidence boundary;
14. `PRE-M1F-PREPARATION.md` — older preparation checkpoint preserving earlier clean-break/decoder decisions;
15. older handoffs such as `NEXT-CHAT-HANDOFF.md` and `CHAT-HANDOFF-2026-09-12.md` for deep historical context.

Always re-read the exact current branch source and current CI before changing implementation. Current source/runtime evidence overrides older handoff wording.

## Current checkpoint

The project is at a **documentation-only pre-M1F checkpoint**.

M1E server-authority source/tests/CI are finalized and re-reviewed. The project owner chose not to perform the final manual Minecraft M1E acceptance run.

Therefore:

- M1E must not be described as having a recorded Minecraft-runtime PASS;
- the missing manual PASS remains an evidence gap;
- by explicit project decision, that skipped manual run is no longer treated as a blocker before later M1F work;
- M1F implementation has **not started** at this checkpoint.

The last pre-documentation branch head was `ff8fc52e8660249150e056d1dff4307377afe7c4`. Documentation-only commits after that head must not be mistaken for M1F implementation.

## Documentation authority

Use this precedence when documents overlap:

1. successful exact-target Minecraft runtime evidence;
2. exact current source / current CI evidence;
3. `HANDOFF-2026-09-13-PRE-M1F.md` for current sequencing/decisions;
4. `LUA-API.md` for the current intended user-facing programming surface;
5. `VERIFIED-FACTS.md` for source/CI/runtime facts already recorded;
6. `CURRENT-STATE.md` for the current implementation snapshot;
7. `CC-T-COMPATIBILITY-CONTRACT.md` for standard speaker compatibility;
8. `ARCHITECTURE.md` and `M1E-FINITE-STREAMING-DESIGN.md` for accepted design;
9. `ROADMAP.md` for milestone ordering;
10. milestone-specific historical docs;
11. older preparation/handoff/P0/prototype docs.

A green build is not Minecraft runtime proof. The project may deliberately proceed without a manual milestone runtime check, but documentation must preserve that distinction rather than calling it a PASS.

## Current checkpoints

- frozen M1D implementation: `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green on both target NeoForge versions;
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`, CI `34658958488` green on both targets;
- diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`, CI `34686003774` green on both targets;
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`, CI `34725651930` green on both targets;
- last pre-documentation head: `ff8fc52e8660249150e056d1dff4307377afe7c4`, CI `34725867558` green on both targets;
- active branch: `codex/m1e-server-authoritative-finite`;
- M1E Minecraft runtime acceptance: **not performed / not recorded as PASS**;
- M1F implementation: **not started**.

## Current decisions to preserve

- explain behavior in normal ComputerCraft/Minecraft terms before class/packet/code detail;
- the old JavaSound/mp3spi prepared decoder is temporary and not expected to work correctly before M1G;
- its reported MP3 duration is not authoritative;
- M1F should make a clean break from the modern whole-file `.part/.media` prepared path;
- M1F acceptance does not require audible finite playback;
- M1F must provide a bounded codec-agnostic encoded range/window contract which distinguishes temporary missing data from true asset EOF;
- M1G owns progressive MP3/common-WAV decode, pre-roll, bounded PCM, and actual audible rendering;
- `audioPlayStaged()` is our old prototype API, not original HQ Speakers compatibility, and is approved for removal when M1F implementation begins;
- new user programs should use `hq.playFile()` or prepare/play/release instead.

## Index

| Need | File |
|---|---|
| Fresh continuation handoff | `HANDOFF-2026-09-13-PRE-M1F.md` |
| Lua/ComputerCraft API reference | `LUA-API.md` |
| What exists now? | `CURRENT-STATE.md` |
| What did M1E implement? | `M1E-SERVER-AUTHORITY.md` |
| What did the earlier runtime diagnostic show? | `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` |
| What was the M1E finalization candidate/evidence? | `M1E-FINALIZATION-2026-09-13.md` |
| What old/temporary code should be cleaned later? | `FUTURE-CLEANUP.md` |
| What were the older pre-M1F preparation decisions? | `PRE-M1F-PREPARATION.md` |
| What do we build next? | `ROADMAP.md` |
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
| General testing/evidence rules | `TESTING.md` |
| Previous large fresh-chat handoff | `NEXT-CHAT-HANDOFF.md` |
| Handoff pointer | `CHAT-HANDOFF.md` |
| Longer dated handoff / deep context | `CHAT-HANDOFF-2026-09-12.md` |

Do not treat an old milestone or older handoff as the current product contract when newer exact source/runtime evidence or current project decisions exist.