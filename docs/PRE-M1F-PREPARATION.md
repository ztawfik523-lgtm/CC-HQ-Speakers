# Pre-M1F preparation checkpoint — superseded

This was the preparation checkpoint written before M1E finalization. It is retained for history and is **not** the current handoff.

Current authority is:

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `NEXT-CHAT-HANDOFF.md`
7. exact current source/CI

`HANDOFF-2026-09-13-PRE-M1F.md` is also historical now.

## Decisions from this checkpoint that still apply

- M1E owns server-authoritative finite state/timeline.
- M1F owns bounded client-requested encoded transport.
- M1G owns progressive MP3/common-WAV decoding and audible rendering.
- Do not repair the disposable whole-file decoder just to keep M1F audible.
- M1F made a clean break from the modern whole-file client `.part/.media` path.
- M1F supports arbitrary encoded offsets and bounded temporary client RAM.
- Temporary missing bytes are distinguishable from true asset EOF.
- Do not introduce a persistent client song cache.

## Historical status changes after this checkpoint

M1E source/tests/CI were finalized and re-reviewed at code/test candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

CI run `34725651930` passed both target NeoForge versions.

The final manual Minecraft M1E test was prepared but not run. Therefore M1E still has **no recorded final focused Minecraft PASS**.

At the time this file was written, M1F had not started. That is now historical: M1F is complete at source/test/CI/package/component level and M1G progressive decode/render is integrated in source.

## Later API decision — implemented

`audioPlayStaged()` was confirmed to be this project's staged-file prototype API rather than original HQ Speakers compatibility.

The selected decision was to remove it when M1F replaced direct staged playback. That removal is now implemented. New programs use `hq.playFile()` or prepare/play/release.

## Plain-language M1F target — now implemented

```text
server owns the whole file
    -> client asks for a small encoded piece near current playback need
    -> server sends that bounded piece
    -> client keeps only a bounded temporary RAM window
    -> old pieces are discarded
    -> seek asks for a different piece instead of downloading everything in between
```

Current transport/source details are in `CURRENT-STATE.md`, `ARCHITECTURE.md`, and `VERIFIED-FACTS.md`.

## Later M1G decisions not present at this checkpoint

Current M1G scope additionally selects:

- explicit server-authoritative decoder/re-anchor revision;
- fixed 32-block core modern-finite range with volume changing gain rather than radius;
- global-volume-zero local hibernation while canonical server time continues;
- ordinary non-gapless replay after local EOF while authoritative looping remains enabled;
- no dynamic volume-aware range or SPR acoustic/range integration in M1G.

For exact current truth, use the current authority list above rather than this historical checkpoint.
