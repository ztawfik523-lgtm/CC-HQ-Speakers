# Roadmap

This roadmap is player-visible and deliberately short.

## M0 — exact ATM10 baseline

Goal:
Make the inherited NeoForge port clean and reproducible on the exact target stack without redesigning playback.

Work:
- CC:T 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- repository cleanup
- CI/build validation
- source-grounded baseline documentation
- one consolidated runtime smoke test of representative inherited features

User result:
> Existing CC:HQ functionality works on the actual pack stack.

Automated status: exact dependency/build, packaging, CI, and dedicated-server
startup gates are complete. Final M0 acceptance awaits one consolidated ATM10
client smoke test.

## M1 — real player controls and truthful state

Goal:
Turn the current command-style audio API into something suitable for a real ComputerCraft player.

Work:
- define truthful playback states
- real looping/repeat
- pause
- resume
- stop/replay lifecycle
- live volume
- duration where knowable
- position
- seek where truthfully supported
- useful error/end state
- retain old `speak*` methods as compatibility wrappers where practical

Known source problems this milestone must fix:
- inherited `setLooping` is not transported to the client;
- inherited client sound explicitly has looping disabled;
- inherited `speakIsPlaying()` is not actual finite renderer state.


Scope note:
Player controls such as pause/resume/seek/position/duration target finite media
first. Live-stream semantics are a separate later decision. Do not silently
treat finite-file controls as if they have the same meaning for live HLS/TS/MP3
streams.

User result:
> A Lua program can build a proper audio player.

## M2 — larger finite media

Goal:
Remove the practical 8 MiB finite-media wall without replacing it with unbounded memory use.

Phase 1 investigation must quantify:
- Lua/file input constraints;
- one-shot packet limitations;
- encoded copies;
- 64 MiB decoded PCM ceiling;
- network limits;
- concurrent playback memory.

Then choose between meaningful implementation options:

A. chunked encoded transfer + existing whole-file decode
Faster/smaller change, but decoded-memory scaling remains.

B. chunked encoded transfer + incremental OGG/MP3 decode
Better long-media scaling, but more lifecycle/backpressure complexity.

Do not choose A/B silently if both remain reasonable.

User result:
> Normal long songs are usable without tiny arbitrary limits.

## M3 — SPR productization

Goal:
Bring the already-developed compatibility work into the real product.

Work:
- preserve frozen V7.1 acoustics;
- connect real player pause/resume/seek/stop lifecycle;
- retain decode/cache/OpenAL hardening;
- optional behavior when SPR is absent;
- resolve companion-JAR vs integrated-module packaging;
- clean experimental release/config naming with migration care;
- one consolidated acoustic/lifecycle regression.

User result:
> HQ playback works properly with Sound Physics Remastered.

## M4 — quality of life

Potential work:
- queue
- next / previous
- metadata polish
- stream reconnect/error reporting
- multi-speaker player controls
- sync improvements
- moving-speaker polish

User result:
> CC:HQ behaves like a polished music/audio system.

## Rule

No large refactor without a concrete player-visible reason.
