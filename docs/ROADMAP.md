# Roadmap

## Product rule

Build a better **programmable CC speaker peripheral**.

Do not build application roles into Java. Lua programs should be able to build music players, alarms, radios, soundboards, PA systems, speech systems, notifications, or other audio applications using the same capabilities.

## P0 — implementation readiness

Goal:

Make the repository reliable enough that implementation can proceed without re-auditing the whole project for every change.

Work:

- rewrite stale docs around exact current source;
- define the CC:T 1.120.0 speaker compatibility contract;
- enumerate the four material architecture choices with concrete tradeoffs;
- add regression/unit/runtime test scaffolding beyond `FiniteAudioTrack`;
- keep `fba84a3` as the immutable reviewed M1 reference.

Exit criteria:

- `CURRENT-STATE`, `VERIFIED-FACTS`, `KNOWN-ISSUES`, `ARCHITECTURE`, `ROADMAP`, `AGENTS` agree;
- `CC-T-COMPATIBILITY-CONTRACT.md` is authoritative for standard speaker behavior;
- user has chosen the unresolved items in `P0-DESIGN-DECISIONS.md`;
- `P0-TEST-MATRIX.md` maps blockers to tests;
- P0 test additions compile/pass where they test currently valid behavior.

User result:

> Future patches can be scoped, implemented, and regression-tested without repeatedly rediscovering the peripheral contract.

## M1A — speaker compatibility and lifecycle stabilization

Goal:

Make the physical HQ speaker trustworthy as a CC peripheral before adding more features.

Work:

- restore standard `playNote`, `playSound`, `playAudio`, `stop`;
- implement truthful `speaker_audio_empty` backpressure;
- implement the chosen heterogeneous raw/finite submission policy;
- implement chosen stop/control recipient ownership;
- give raw feed a bounded idle/lifecycle policy;
- fix provider cache/world lifecycle;
- make status truthful by source type.

User result:

> Existing CC speaker programs keep working, and HQ additions no longer corrupt the basic speaker lifecycle.

## M1B — finite control correctness

Goal:

Finish the already-good finite M1 foundation.

Work:

- loop-disable clock rebase;
- exact-duration seek -> clean end;
- anchor failover/authority;
- queue-head generation gating;
- no-renderer policy;
- chosen bounded decoder/cancellation strategy;
- additional state-machine/network tests;
- final one-session Minecraft acceptance.

User result:

> Finite media pause/resume/seek/loop/duration/state are reliable enough for arbitrary Lua applications.

## M1C — live/open-ended source stabilization

Goal:

Make streams follow the same capability-first philosophy without pretending they are finite files.

Work:

- single volume application;
- truthful stream start/failure/stop state;
- pause -> suspend/stop;
- resume -> reconnect to current live point;
- HLS media-sequence progression;
- incremental direct TS demux/playback;
- clean unsupported-codec failure;
- stream queue/session cleanup;
- chosen partial-group sync behavior.

User result:

> Lua can build reliable live-radio/stream applications with clear open-ended semantics.

## M2 — larger finite media

Goal:

Remove the practical 8 MiB wall without creating unbounded memory use.

Decision after measurement:

A. chunked encoded transfer + retained whole decode

or

B. chunked transport + incremental finite decode

Tradeoffs remain material; do not choose silently.

Measure and bound:

- Lua/server input copies;
- network chunks;
- queued decode work;
- decoder/native temporary memory;
- retained decoded PCM;
- simultaneous playback.

## M3 — Sound Physics Remastered productization

Goal:

Integrate existing `cchq-soundphysics-compat` work with the stabilized lifecycle.

Preserve frozen V7.1 acoustics and existing hardening. Decide companion-JAR vs integrated optional module explicitly.

## M4 — programmable-audio QoL

Potential capability additions:

- richer queue inspection/control;
- events for readiness/end/error;
- metadata improvements;
- better multi-speaker control;
- sync/moving-speaker polish;
- optional playback-instance/handle API if real concurrency requirements justify it.

These remain primitives for Lua, not a built-in end-user player.
