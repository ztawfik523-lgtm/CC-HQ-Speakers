# Pre-M1F preparation checkpoint — superseded

This was the preparation checkpoint written before M1E finalization. It is retained for history, but it is no longer the current handoff.

Read these instead:

1. `HANDOFF-2026-09-13-PRE-M1F.md`
2. `LUA-API.md`
3. `CURRENT-STATE.md`

## Decisions from this checkpoint that still apply

- M1E owns server-authoritative finite state/timeline.
- M1F owns bounded client-requested encoded transport.
- M1G owns progressive MP3/common-WAV decoding and audible rendering.
- Do not repair the disposable whole-file decoder just to keep M1F audible.
- M1F should make a clean break from the modern whole-file client `.part/.media` path.
- M1F can pass without audible MP3/WAV playback.
- M1F must support arbitrary encoded offsets and bounded temporary client RAM.
- Temporary missing bytes must be distinguishable from true asset EOF.
- Do not introduce a persistent client song cache.

## Status changes after this checkpoint

M1E source/tests/CI were finalized and re-reviewed at code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

CI run `34725651930` passed both target NeoForge versions.

The final manual Minecraft M1E test was prepared but was not run. The project owner chose to skip that manual test and move forward later.

Therefore M1E has **no recorded final Minecraft PASS**. The missing manual PASS remains an evidence gap, but it is no longer treated as a required sequencing gate by current project decision.

M1F implementation has **not started** at the current documentation checkpoint.

## Later API decision

`audioPlayStaged()` was confirmed to be this project's own staged-file prototype API, not original HQ Speakers compatibility.

Current decision: remove it when M1F implementation begins. New programs should use `hq.playFile()` or prepare/play/release.

## Plain-language M1F target

```text
server owns the whole file
    -> client asks for a small encoded piece near the current playback point
    -> server sends that bounded piece
    -> client keeps only a bounded temporary RAM window
    -> old pieces are discarded
    -> seek asks for a different piece instead of downloading everything in between
```

For exact current truth, use the current handoff/source/CI rather than this historical checkpoint.