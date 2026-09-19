# Known issues and risk register

Updated: 2026-09-20

This file records current unresolved risks and important resolved issues. Older numbered issue discussions in historical documents remain evidence for their checkpoints but do not override current source.

## Current unresolved / deferred

### KI-RUNTIME-001 — M1H listener lifecycle lacks focused Minecraft proof

Source/test/CI/package behavior exists for walk-in, walk-out, re-entry and cleanup, but focused live Minecraft acceptance is still deferred.

### KI-RUNTIME-002 — M1H renderer/reload/starvation recovery lacks focused Minecraft proof

Deterministic logic exists, but SoundEngine resource reload, lost renderer and sustained starvation need in-game acceptance.

### KI-RUNTIME-003 — Sable/Aeronautics + VS2 movement lacks focused Minecraft proof

Source/package support exists. Runtime movement acceptance remains deferred.

### KI-MOVE-004 — Sable parent-world relevance may be rejected by Level identity

`HQFiniteMediaServer.isRelevant(...)` calculates distance using the resolved world position but still passes `player.level() == level` into relevance validation.

If the speaker's server `level` is a Sable sublevel while the player is in the parent world, correct projected coordinates may still be rejected.

This is a concrete source risk, not a proven runtime bug. Test/fix before claiming Sable listener membership complete.

### KI-RUNTIME-005 — M1J multispeaker lacks focused Minecraft proof

Need real 2/4/8+ speaker tests for audibility/sync, pause/seek/loop, endpoint removal/replacement and per-endpoint volume/mute.

### KI-PERF-006 — multispeaker client decode duplication is unprofiled

Every audible endpoint keeps an independent decoder/PCM/render path. Server range traffic is already bounded per player.

Do not implement shared decode fan-out unless profiling shows meaningful cost.

### KI-LIVE-007 — HLS refreshed-playlist progression remains suspect

Inherited HLS logic keeps a monotonically increasing segment index while refreshed playlist arrays are new zero-based windows. A long-running stream can therefore stop finding new segment indices.

Live streaming is optional, so this is not a core release blocker unless live HLS is promoted.

### KI-LIVE-008 — grouped live streams still use expected-count synchronization

`speakStreamAll` / `speakHLSAll` / `speakTSAll` still create legacy sync groups. Client `SyncGroupState` waits on expected members.

Finite and RAW no longer use this barrier. Do not confuse the remaining live-only code with core multispeaker design.

### KI-API-012 — stale inherited grouped/indexed control aliases bypass composite ownership

`speakStopAll` / `speakStopAt` still call legacy stop directly, `speakVolumeAll` only changes legacy default volume, and `setLoopingAll` reaches a legacy no-op.

These aliases are not documented in the current Lua API. Decide whether to remove them as obsolete compatibility surface or preserve them with explicit ownership-aware semantics.

### KI-RELEASE-010 — final integrated runtime/stress acceptance remains

Before public release, batch M1H/M1J runtime backlog, malformed/extreme media, queue/memory/network/worker bounds, Sound Physics Remastered, and both NeoForge targets.

### KI-REPO-011 — repository/CI hygiene can still improve

Possible non-functional cleanup: docs-only CI path, concurrency cancellation, default-branch normalization, stale historical script labeling, and dead imports/helpers.

## Important resolved issues

- KI-046: M1G focused audible/core proof passed 2026-09-19 on NeoForge 21.1.247.
- KI-054: shutdown/root-lock retry resolved.
- KI-062: stream DNS/server-lock coupling resolved.
- KI-063: destructive replacement before admission resolved for modern finite and RAW.
- KI-064: import no-progress / atomic rename fallback resolved.
- duplicate legacy finite engine/API debt resolved by migration/removal.
- modern finite expected-member barrier removed.
- RAW expected-member barrier removed.
- grouped/indexed standard semantics corrected at the composite surface.
- dead legacy standard grouped/indexed bodies removed at `ef2a917de429c34409ae7866f59cb50f3c191aa1`.
- dead legacy RAW `speakPCMAll/speakPCMAt` duplicates/helpers removed at `395a41c1c91a4d1efa41cee9db89ba02fe767785`.
- inherited standalone HQ block removed.
- license metadata mismatch corrected to MPL-2.0.

## Scope reminders

Optional/future, not current blockers:

- modern OGG;
- FLAC;
- provider playback;
- live HLS/TS/radio repair;
- native ordinary Create contraption lifecycle;
- gapless playback.
