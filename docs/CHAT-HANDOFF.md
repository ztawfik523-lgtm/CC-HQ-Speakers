# CC:HQ Speakers — handoff pointer

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current implementation branch:

`codex/m1g-progressive-finite-decode`

Current continuation entry point:

`HANDOFF-2026-09-13-M1G-START.md`

## Next chat first action

**Before changing source, ask the owner to choose the remaining M1G loop-wrap architecture: L1, L2, or L3.**

- **L1 — client EOF refresh:** local EOF during looping asks server for fresh STATE, then restarts from the server-selected anchor.
- **L2 — server wrap STATE:** server detects canonical wrap crossings and proactively sends fresh STATE.
- **L3 — client local modulo/restart:** client predicts wrap locally and reconciles with server later.

Do not silently choose among them. The detailed pros/cons and stop condition are in `HANDOFF-2026-09-13-M1G-START.md` and `CURRENT-STATE.md`.

The previously selected A1/B1/C1/D1/E1 architecture is already locked and should not be reopened without new substantive evidence.

## Read order

1. `HANDOFF-2026-09-13-M1G-START.md`;
2. `M1G-DESIGN-DECISIONS-2026-09-13.md`;
3. `CURRENT-STATE.md`;
4. `TESTING.md`;
5. `KNOWN-ISSUES.md`;
6. `VERIFIED-FACTS.md`;
7. `M1F-FINALIZATION-2026-09-13.md`;
8. `ROADMAP.md`;
9. `LUA-API.md`;
10. exact current source/CI.

## Current status

- M1E source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded;
- M1F source/test/CI/package + deterministic/component acceptance complete;
- M1F focused real-Minecraft transport acceptance remains unrecorded;
- M1G integrated progressive decode/render source checkpoint is `957832348eaa6e497282d923f2312c9c7d7c550f`;
- source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248, including tests/package verification/artifact upload;
- documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both targets;
- protocol v6, MP3/common-WAV descriptor, codec-aware anchors, progressive WAV + JLayer MP3 decode, bounded PCM, and Minecraft `AudioStream`/`SoundManager` positional rendering are integrated in source;
- loop-wrap rejoin policy remains the active owner decision;
- focused audible Minecraft M1G acceptance remains unrecorded;
- full late-entry/leave-return/reload/general-underrun/VS2 lifecycle remains M1H.

Do not revive the whole-file finite bridge or treat client decoder EOF as canonical server EOF.