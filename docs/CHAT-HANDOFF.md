# CC:HQ Speakers — handoff pointer

Date: 2026-09-14

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

Current source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f` / CI `34778546164`.

A full repository/source/docs audit on 2026-09-14 reconciled current docs and recorded KI-053 through KI-055. No implementation fix was made.

## Read first

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `HANDOFF-2026-09-13-M1G-START.md`
6. `M1G-DESIGN-DECISIONS-2026-09-13.md`
7. exact current source/CI

## Current state

M1G progressive MP3/common-WAV decode, bounded PCM, and Minecraft positional rendering are integrated in source. The modern prepared path is not transport-only and does not use the inherited complete-file bridge.

Focused audible Minecraft M1G acceptance is still unrecorded.

Open current items:

- KI-051 — loop-wrap rejoin owner choice: L1 client EOF refresh, L2 server wrap STATE, or L3 client local modulo/restart;
- KI-053 — ordinary same-anchor STATE/window reset hazard;
- KI-055 — missing real-MP3 progressive integration and focused renderer-adapter deterministic coverage;
- KI-054 — shutdown-only MediaAssetStore completed-file deletion retry gap.

Before implementing loop-wrap behavior, ask the owner to choose L1/L2/L3. Do not silently select one. Any subsequent M1G implementation should also resolve KI-053 and close the missing deterministic coverage before claiming runtime readiness.

Historical milestone/handoff documents preserve earlier checkpoints and do not override the current records above.
