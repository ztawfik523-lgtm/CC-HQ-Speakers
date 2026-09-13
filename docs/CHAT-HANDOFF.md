# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Current continuation entry point:

`HANDOFF-2026-09-13-M1E-M1F-REEVALUATION.md`

Read the detailed audit next:

`M1E-M1F-REEVALUATION-2026-09-13.md`

Then read:

- `CURRENT-STATE.md`
- `KNOWN-ISSUES.md`
- `TESTING.md`
- `VERIFIED-FACTS.md`
- `M1E-SERVER-AUTHORITY.md`
- `M1F-IMPLEMENTATION-2026-09-13.md`
- `M1E-FINITE-STREAMING-DESIGN.md`
- `ROADMAP.md`
- `LUA-API.md`
- `ARCHITECTURE.md`
- `FUTURE-CLEANUP.md`

Current implementation branch:

`codex/m1f-demand-driven-finite`

Current Java/source head remains:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

No Java was changed during the M1E/M1F reevaluation.

Both-target CI/package evidence for that source head remains green (`34731827907`), but the old `M1E finalized` / `M1F source-test-CI complete` status wording is superseded.

Current status:

- M1E semantic design remains implemented, but exception/lifetime hardening and acceptance are reopened;
- M1E final focused Minecraft PASS remains skipped/unrecorded;
- M1F range architecture remains implemented and CI-green, but acceptance/completeness is reopened;
- M1F Minecraft runtime acceptance is unrecorded;
- M1G has not started and should not start until the owner chooses how to close/defer the reopened issues.

Older M1E/M1F completion documents are historical checkpoint records. Exact current source and the reevaluation documents take precedence for status.
