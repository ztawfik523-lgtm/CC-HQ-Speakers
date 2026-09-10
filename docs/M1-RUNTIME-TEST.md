# M1 consolidated runtime test

> **Do not use this as the next action on reviewed `fba84a3`.**
>
> P0 source review found compatibility/lifecycle blockers which should be fixed first. Run the consolidated M1 session after M1A/M1B stabilization, using `P0-TEST-MATRIX.md` as the gate.

Run one Minecraft 1.21.1 client with Java 21, CC:T 1.120.0, NeoForge 21.1.247, and the candidate JAR.

Generate deterministic nominal eight-second fixtures:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate_m1_test_audio.ps1
```

This writes:

- `m1.mp3` — 44.1 kHz
- `m1.ogg` — 48 kHz
- `m1.wav` — 32 kHz

Copy the files plus runtime scripts to the ComputerCraft computer.

## Gate 1 — standard speaker contract

Run:

```text
p0_cc_speaker_contract
```

This must pass before HQ-specific acceptance is meaningful.

## Gate 2 — finite edge regressions

Run:

```text
p0_finite_regression m1.mp3
```

It must prove:

- loop wrap;
- loop-disable position continuity before any compensating seek;
- exact-duration seek -> ended;
- `speakIsPlaying()` false after end.

## Gate 3 — broader finite M1 helper

Run:

```text
m1_player_test m1.mp3 m1.ogg m1.wav
```

Expected coverage:

- status/observation;
- duration/position;
- pause freeze/resume;
- live finite volume;
- forward/backward/paused seek;
- loop;
- natural end;
- MP3/OGG/WAV decoded sample-rate correctness;
- raw PCM conventions;
- multiple finite calls;
- stop while loading/replacement;
- F3+T manual phase.

The existing helper should be updated when D1 mixed-input semantics are chosen; do not preserve old finite/raw behavior accidentally.

## Gate 4 — range/lifecycle

Using at least one client:

- start finite/raw/stream playback;
- move outside normal speaker send radius;
- issue stop/control;
- verify no stale playback survives;
- return to range;
- disconnect/rejoin;
- shut down world.

If D2 uses recipient tracking, explicitly verify the former listener remains a control recipient after leaving range.

## Gate 5 — multiple renderers/speakers

Where practical:

- two players observe one finite speaker;
- remove/disconnect the anchor renderer and verify state can still complete;
- two or more speakers use All/sync calls;
- test the chosen D4 partial-group behavior from a position which can receive only a subset.

## Gate 6 — streams

After M1C stream stabilization:

- direct MP3;
- pause then resume/reconnect to current live point;
- stop after moving out of range;
- live volume once (not squared);
- metadata;
- HLS sliding live playlist for longer than one initial window;
- direct TS incremental output;
- unsupported codec -> clean error.

## Manual audio checks

- BLOCKS and MASTER sliders continue scaling HQ custom audio;
- finite file pitch/speed is correct at 32/44.1/48 kHz fixture rates;
- F3+T re-prime does not create stale playback;
- VS2-moving speaker position updates if available.

## Record

- exact commit;
- JAR SHA-256;
- target versions;
- test-instance vs full ATM10;
- pass/fail by gate;
- relevant logs.

Do not repeat broad client launches without first diagnosing any failure.
