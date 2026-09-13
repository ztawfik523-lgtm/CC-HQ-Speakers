# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current implementation branch:

`codex/m1g-progressive-finite-decode`

Current continuation entry point:

`HANDOFF-2026-09-13-M1G-START.md`

Then read:

1. `M1G-DESIGN-DECISIONS-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `PRE-M1G-PREPARATION.md` for historical tradeoff analysis only
4. `M1F-FINALIZATION-2026-09-13.md`
5. `KNOWN-ISSUES.md`
6. `TESTING.md`
7. `VERIFIED-FACTS.md`
8. `ROADMAP.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `LUA-API.md`
11. exact current source/CI

Current status:

- M1E source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded;
- M1F source/test/CI/package + deterministic/component acceptance complete;
- final M1F source/test candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- final M1F CI `34763362365` passed both NeoForge targets;
- focused real-Minecraft M1F transport acceptance remains unrecorded;
- M1G **has started**;
- owner decisions are locked as A1/B1/C1/D1/E1 in `M1G-DESIGN-DECISIONS-2026-09-13.md`;
- first M1G production slice narrows newly prepared assets to MP3/common WAV and adds normalized WAV/WAVEX layout + codec-aware anchor foundations;
- progressive decode/PCM/renderer integration is still in progress, so there is no M1G audible PASS yet;
- full dynamic late-entry/leave-return/rejoin remains M1H.

Do not reopen the resolved renderer/WAV-layout/sample-rate/WAVEX/MP3-pre-roll decisions unless new evidence exposes a substantive correctness problem. Do not revive the whole-file finite bridge.
