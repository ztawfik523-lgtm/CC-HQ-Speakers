# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Current continuation entry point:

`M1F-FINALIZATION-2026-09-13.md`

Then read:

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `M1F-IMPLEMENTATION-2026-09-13.md`
6. `M1E-FINITE-STREAMING-DESIGN.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `ARCHITECTURE.md`
10. exact current source/CI

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch:

`codex/m1f-finalization`

Current M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Current status:

- M1E source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded by owner decision;
- M1F source/test/CI/package complete; focused Minecraft transport acceptance unrecorded;
- M1F uses bounded off-thread range reads + bounded sliding client encoded RAM and intentionally does not decode/render;
- M1G progressive MP3/common-WAV decode + audible positional rendering is next and not started;
- full dynamic late-entry/leave-return/rejoin remains M1H.

Do not reopen the M1E/M1F reevaluation findings without new source/test/runtime evidence. Older reevaluation/handoff documents are historical checkpoint records.
