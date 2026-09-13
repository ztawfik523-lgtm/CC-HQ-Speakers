# Documentation index

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G implementation started.**

Active branch:

`codex/m1g-progressive-finite-decode`

M1G preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

Current green M1G start source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI `34773448121` passed both NeoForge 21.1.247 and 21.1.248 including tests, packaged-mod verification, and artifact upload.

Final M1F source/test candidate remains:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Focused Minecraft M1F transport acceptance is not recorded. M1G audible runtime acceptance is also not recorded.

## Read in this order

1. `M1G-DESIGN-DECISIONS-2026-09-13.md` — locked A1/B1/C1/D1/E1 decisions and implementation constraints;
2. `HANDOFF-2026-09-13-M1G-START.md` — current continuation handoff;
3. `CURRENT-STATE.md` — current project snapshot;
4. `TESTING.md` — current deterministic evidence and remaining M1G proof;
5. `KNOWN-ISSUES.md` — active gaps;
6. `VERIFIED-FACTS.md` — fact ledger;
7. `PRE-M1G-PREPARATION.md` — historical tradeoff/source audit; its owner-choice gate is superseded;
8. `M1F-FINALIZATION-2026-09-13.md` — exact final M1F evidence;
9. `ROADMAP.md` — milestone sequence;
10. `M1E-FINITE-STREAMING-DESIGN.md` — server-authority/transport/decoder boundary;
11. `LUA-API.md` — ComputerCraft programming surface;
12. `ARCHITECTURE.md` — broader product architecture, with historical transitional paragraphs;
13. `CC-T-COMPATIBILITY-CONTRACT.md` — standard speaker compatibility;
14. exact current source and CI.

## M1G decisions are resolved

Do not reopen these without new substantive correctness evidence:

- A1 — Minecraft `AudioStream` / normal `SoundManager` positional renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate while output representation becomes mono S16;
- D1 — narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` compatibility;
- E1 — coarse conservative MP3 pre-roll using an earlier existing seek point.

If a genuinely new architecture tradeoff appears, ask the owner before selecting it.

## M1G source started

The green start checkpoint implements and tests:

- normalized common-WAV physical layout metadata;
- bounded classic common-WAV + narrow WAVEX parsing;
- modern prepared/local MP3 + common-WAV acceptance gate;
- decoder-facing MP3/WAV-only descriptor;
- exact WAV frame anchor mapping and E1 MP3 pre-roll anchor selection;
- decoder-worker `FiniteEncodedInputStream` over the M1F range window where starvation waits instead of becoming EOF;
- fixed-capacity `FinitePcmQueue` with producer backpressure and nonblocking STARVED/EOF/CANCELLED renderer reads;
- cancellation/wakeup and lost-wakeup protection;
- RIFF declared-bound and complete-frame validation.

Newly prepared assets already use the M1G MP3/common-WAV gate. The modern client has **not** yet wired these primitives into a progressive decoder/renderer session, so M1G is not audible yet.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded by explicit owner decision.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1G source implementation: started/in progress.
- M1G audible Minecraft PASS: unrecorded.
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
-> starvation-aware decoder-worker input
-> progressive MP3/common-WAV decoder/converter (next integration)
-> bounded mono S16 PCM queue
-> Minecraft AudioStream / positional renderer (next integration)
```

## Historical context

Older M1E/M1F reevaluation/preparation/handoff documents preserve their checkpoint history. `PRE-M1G-PREPARATION.md` and `HANDOFF-2026-09-13-PRE-M1G.md` also preserve the pre-choice state and do not override the locked M1G decisions/current source.
