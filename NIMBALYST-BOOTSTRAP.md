# Nimbalyst bootstrap

> **Historical bootstrap record. Do not use this file as current implementation guidance.**
>
> The branch/project setup below predates M1E/M1F completion and the integrated M1G progressive engine. Current work must start from `docs/CURRENT-STATE.md`, `docs/KNOWN-ISSUES.md`, `docs/TESTING.md`, and exact current source/CI on `codex/m1g-progressive-finite-decode`.

Recommended local folder at the original bootstrap point:
`D:\Games\Codex\CC-HQ-Speakers-repo`

Original recommended first branch:
`codex/bootstrap-atm10-player`

The original bootstrap instructions were to create a brand-new Nimbalyst project pointed at this repository root and not reuse the old HighAudio project memory.

Historical project-context goals:
- fork improvement, not clean-room rewrite;
- larger media, real player controls, reliable loop, SPR;
- preserve codecs/streaming/old Lua compatibility where sensible;
- exact stack: MC 1.21.1 / Java 21 / CC:T 1.120.0 / NF 21.1.247 + .248;
- existing SPR V7.1 work as prior evidence;
- reuse HighAudio facts selectively, not its architecture wholesale;
- batch manual tests.

The original bootstrap rule remains generally useful: source-grounded claims must be verified against the checked-out branch, and current source/current-state docs override imported historical notes.

The original first substantial task list was:
1. inspect inherited source;
2. verify/adapt the doc pack against actual code;
3. update exact target dependencies;
4. clean repo hygiene separately;
5. establish .247/.248 build validation;
6. do not redesign the player yet;
7. produce a baseline JAR and consolidated smoke-test plan.

Those tasks are historical and have been superseded by the current milestone state.
