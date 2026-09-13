# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current preparation branch:

`codex/m1g-preparation`

Current continuation entry point:

`HANDOFF-2026-09-13-PRE-M1G.md`

Then read:

1. `PRE-M1G-PREPARATION.md`
2. `M1F-FINALIZATION-2026-09-13.md`
3. `CURRENT-STATE.md`
4. `KNOWN-ISSUES.md`
5. `TESTING.md`
6. `VERIFIED-FACTS.md`
7. `ROADMAP.md`
8. `M1E-FINITE-STREAMING-DESIGN.md`
9. `LUA-API.md`
10. exact current source/CI

Current status:

- M1E source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded;
- M1F source/test/CI/package + deterministic/component acceptance complete;
- final M1F source/test candidate `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`;
- final M1F CI `34763362365` passed both NeoForge targets;
- docs head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` passed run `34763711105`, and the requested fresh rerun passed both target jobs again;
- focused real-Minecraft M1F transport acceptance remains unrecorded;
- M1G is prepared but has **not started**;
- full dynamic late-entry/leave-return/rejoin remains M1H.

Before M1G implementation, the owner must choose the renderer path, WAV-layout ownership, and finite sample-rate policy documented in `PRE-M1G-PREPARATION.md`.

Do not implement M1G from old transitional architecture paragraphs or revive the whole-file finite bridge.
