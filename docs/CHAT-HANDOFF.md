# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Current continuation entry point:

`M1E-FINAL-HARDENING-2026-09-13.md`

Then read:

- `CURRENT-STATE.md`
- `KNOWN-ISSUES.md`
- `TESTING.md`
- `VERIFIED-FACTS.md`
- `M1E-SERVER-AUTHORITY.md`
- `M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit; M1E findings are resolved
- `M1F-IMPLEMENTATION-2026-09-13.md`
- `M1E-FINITE-STREAMING-DESIGN.md`
- `ROADMAP.md`
- `LUA-API.md`
- `ARCHITECTURE.md`
- `FUTURE-CLEANUP.md`

Current hardening branch:

`codex/m1e-final-hardening`

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Exact code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build/tests/package verification/artifact upload.

Current status:

- M1E semantic design/source hardening/deterministic tests are complete;
- M1E final focused Minecraft acceptance remains skipped by owner decision / no recorded PASS;
- M1F range architecture remains implemented, but its sliding-window boundary and remaining acceptance matrix are provisional;
- M1F focused Minecraft runtime transport acceptance is unrecorded;
- M1G has not started.

Do not reopen the old M1E packet-send/ghost-session/release-retry/error-clock findings: they were fixed by the final hardening sequence ending at `521d4323...`.

Do not infer M1F completion from M1E finalization. Exact current source and the final-hardening/current-state documents take precedence over older checkpoint wording.
