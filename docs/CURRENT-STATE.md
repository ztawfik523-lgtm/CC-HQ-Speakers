# Current state

## Current checkpoint

M1E is complete at the source/test/CI level after the 2026-09-13 hardening pass.

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Branch:

`codex/m1e-final-hardening`

Final M1E CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, package verification, and artifact upload.

The final focused Minecraft M1E acceptance script was previously skipped by explicit owner decision. There is still **no recorded final M1E runtime PASS**. This is an evidence boundary, not an open source blocker.

Read `M1E-FINAL-HARDENING-2026-09-13.md` for the exact completion record.

## Architecture that remains current

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> bounded client-requested encoded ranges (M1F)
    -> progressive decode/PCM/rendering later (M1G)
```

M1E now has a tested production semantic state machine and hardened failure/lifetime boundaries:

- canonical playback starts immediately on the server;
- server owns PLAYING / PAUSED / ENDED / ERROR;
- server owns position, duration, seek, loop, volume, and natural EOF;
- client READY requests state only;
- client ERROR is diagnostic only;
- server-side ERROR freezes position;
- packet/client projection is best-effort and isolated per recipient;
- prepared start completes throwable construction before canonical session installation;
- logical MediaAsset release failures transfer to a retry-safe server owner;
- speaker cleanup happens before shared store shutdown;
- range IO drains before store cleanup.

## M1F status

M1F remains **implemented but provisional**. Its range architecture still stands:

- protocol v5 range request/data;
- server-selected encoded anchors in STATE;
- first demand waits for authoritative STATE;
- bounded packet/window/outstanding limits;
- bounded off-thread server reads;
- in-flight MediaAsset retain;
- current generation/asset/player/relevance recheck before range send;
- arbitrary encoded offsets;
- no modern client whole-song `.part/.media` cache;
- old modern CHUNK/END path removed;
- project-prototype `audioPlayStaged()` removed;
- missing network data is distinct from true asset EOF.

M1F still needs its own completion work:

- make `FiniteRangeWindow` a true sliding consume/discard window instead of only re-anchorable;
- close the remaining deterministic acceptance matrix around request identity/relevance/stale completion/shutdown/client anchor gating/packet integration;
- focused Minecraft range-transport acceptance remains unrecorded.

These M1F items do **not** reopen M1E.

## M1G status

M1G has not started.

M1G still owns progressive MP3/common-WAV decoding, starvation-vs-EOF behavior, MP3 pre-roll, bounded mono PCM, decoder cancellation, and actual positional Minecraft/OpenAL rendering.

Do not repair the obsolete JavaSound/mp3spi complete-file bridge merely for temporary audibility.

## M1H boundary

Full late-entry / leave-range cleanup / return-rejoin / dimension-reload listener lifecycle remains M1H.

## Current read order

1. `M1E-FINAL-HARDENING-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-M1F-REEVALUATION-2026-09-13.md` — historical audit, M1E findings now resolved
8. `M1F-IMPLEMENTATION-2026-09-13.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `ROADMAP.md`
11. `LUA-API.md`
12. exact current source/CI

Older M1E completion documents remain historical evidence. The final hardening record above is authoritative for current M1E status.
