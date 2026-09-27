# Documentation index

Updated: 2026-09-27

Build policy: **NeoForge 21.1.247 only**; the one artifact declares `[21.1,21.2)`.

Read current authority in this order:

1. `HANDOFF-2026-09-27-TARGET-RERUN.md`
2. `API-FREEZE-V10.md`
3. `CURRENT-STATE.md`
4. `KNOWN-ISSUES.md`
5. `RUNTIME-INVESTIGATION-2026-09-27.md`
6. `SPR-INTEGRATION-REEVALUATION-2026-09-27.md`
7. `RUNTIME-ACCEPTANCE-V10.md`
8. `RUNTIME-RESULTS-V10.md`
9. `TESTING.md`
10. `VERIFIED-FACTS.md`
11. `ARCHITECTURE.md`
12. `ROADMAP.md`
13. `LUA-API.md`
14. `CC-T-COMPATIBILITY-CONTRACT.md`
15. `SERVER-CONFIG.md`
16. `SOURCES.md`

Current runtime state: A1-A19 PASS and R1-R9 PASS remain historical runtime evidence; attempt-7 C3 passed. The current recheck source checkpoint `87d08d3a62857dfeba16756591560eaa0e62d193` is CI-green in run `36293523518` with artifact `10922887347`. It contains the C1 harness correction, fair shared finite-range admission, independent C4 RAW execution, and direct HQ-only SPR process diagnostics. C1-C4 require a fresh target rerun before their runtime status is upgraded.

Historical dated handoffs and M0/M1 milestone documents remain evidence for their old checkpoints and should not be rewritten as current authority.
