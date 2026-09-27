# Verified facts

Updated: 2026-09-27

## Product/package

- only `computercraft:speaker` is the block product;
- protocol is v10 with 9 payloads;
- build/test/package baseline is NeoForge 21.1.247 only;
- metadata accepts `[21.1,21.2)`;
- standalone HQ block, HLS/TS and retired generic whole-file aliases remain removed.

## Runtime-confirmed core

A1-A19 and R1-R9 have passed in real Minecraft on the current candidate.

Verified core facts include:

- native CC:T sound/DFPWM client channels work with Sable world-space positioning;
- finite MP3/WAV render on real OpenAL channels;
- 2-speaker finite synchronization and endpoint-local controls work;
- continuous producer-fed RAW continuation works;
- loop-boundary recovery works;
- listener leave/rejoin works;
- F3+T sound-engine teardown/rebuild recovers through authoritative finite rejoin without a decoder failure.

## Sable target evidence

Attempt 7 measured approximately:

- source 1 requested movement 53.224 blocks, actual movement 53.224;
- source 2 requested movement 52.267 blocks, actual movement 52.267.

Both ended PLAYING with zero decoder failures.

The runner's C1 FAIL is therefore not evidence that movement tracking failed; it is caused by a generic no-STOP-history assertion during a scenario which crossed listener relevance.

## Radio target evidence

C3 passed in attempt 7.

Verified:

- sustained 2-speaker grouped MP3/ICY radio;
- ~1.91 ms channel-start skew and 0.00 ms settled drift in the captured baseline;
- ICY metadata delivery;
- late speaker excluded from an already sealed group;
- rerun admits the new member;
- singular and indexed radio start paths.

## Finite scale finding

Attempt 7's eight finite endpoints all reached server PLAYING, but one real client source started roughly 2.219 seconds later than the others.

Current source facts:

- each client finite source can keep 2 requests in flight;
- server range service allows 4 outstanding requests per player;
- `OVER_LIMIT` admission is currently silent to the requesting client;
- client request expiry is 2 seconds.

This is a concrete scale defect which must be fixed before 8-speaker finite can be marked accepted.

8+ RAW was not executed in that attempt.

## Sound Physics evidence boundary

Attempt 7 confirms the current diagnostic observed SPR environment application on HQ sources.

It does **not** yet confirm that SPR's full `SoundPhysics.processSound` ray/world evaluation ran, because the diagnostic hook is at the environment-write stage.

Open-air and wall direct gain/HF both remained 1.0000 in that run. No final conclusion about SPR compatibility or Sable geometry should be drawn until actual process-call evidence is added.

## Evidence boundary

Final release acceptance remains incomplete. Current blockers are the finite scale admission bug, direct SPR integration proof/behavior, C1 harness correction and missing 8+ RAW evidence.
