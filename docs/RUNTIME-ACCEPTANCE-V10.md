# Runtime acceptance — protocol v10

Updated: 2026-09-26

## Goal

Run one master acceptance against the current release JAR. Built-in diagnostics judge client/Minecraft/OpenAL behavior automatically; the operator only performs physical actions Minecraft cannot perform by itself.

Chosen release-test scope: **singleplayer + Sable/Aeronautics + Sound Physics Remastered**. Dedicated-server/multiplayer and VS2 are intentionally outside this acceptance scope.

## Current candidate

Code/test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
CI: `36205257110` — PASS  
Build baseline: NeoForge **21.1.247 only**  
Artifact: `10892998704`  
JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`  
Minecraft: 1.21.1  
Java: 21  
CC:Tweaked: 1.120.0  
Protocol: **v10**, exactly 9 payloads.

Do not build a separate 21.1.248 JAR. The one 21.1.247-built artifact declares `[21.1,21.2)`.

## Built-in diagnostic surface

The normal release JAR exposes:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

Diagnostics are dormant until explicitly enabled by the acceptance runner.

While enabled, the client records real HQ/CC:T audio behavior: channel creation/state, queued/processed OpenAL buffers, source position, source gain, playback offset/output latency when supported, PCM input/delivery, RAW wakeups, decoder/recovery activity, sound-engine reloads, multispeaker timing and Sound Physics filter application/change.

The selected singleplayer test uses an in-process diagnostic bridge; protocol v10 remains at 9 payloads.

## Setup

Prepare:

- the current release-candidate JAR above;
- one CC:T computer;
- **2 attached normal CC:T speakers** initially;
- **6 additional normal speakers nearby** so the scale test can reach 8 total;
- the supplied 40-second mono 48-kHz MP3 and WAV assets;
- Sable/Aeronautics available, with the computer + initial speakers on the contraption;
- Sound Physics Remastered 1.21.1-1.5.1 enabled;
- a solid wall/room/obstacle for the Sound Physics comparison;
- enough space to walk more than 32 blocks away and return without destroying the setup;
- one direct public MP3/ICY URL if radio acceptance is being completed;
- for the dimension check, a way to keep the source chunk loaded while the player changes dimension.

Keep Minecraft `latest.log` if anything fails. The runner writes `/v10-acceptance.log`.

## Run

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

There are no user-facing phases.

## Test matrix

### Deterministic A1-A19

1. frozen API surface;
2. discovery + declared limits;
3. staged-media lifecycle;
4. native CC:T single/all/indexed + backpressure;
5. MP3 lifecycle + EOF;
6. loop-wrap authority;
7. WAV + indexed finite starts;
8. malformed finite-media rejection;
9. argument and RAW bounds;
10. RAW backpressure + retry;
11. RAW All/At admission;
12. group finite shared authority;
13. endpoint-local stop;
14. singular/all/indexed gain + mute + clamp;
15. shared all/indexed pause/resume/seek/loop;
16. concurrent control stress;
17. repeated synchronized starts;
18. stream security + metadata surface;
19. final deterministic idle.

### Real-client R1-R9

1. native CC:T real client channels;
2. MP3 pause/resume renderer;
3. WAV renderer continuity;
4. multispeaker finite synchronization;
5. endpoint-local controls reaching the actual client sources;
6. continuous RAW client delivery;
7. loop-boundary client recovery + synchronization;
8. range leave/rejoin;
9. F3+T sound-engine reload recovery.

### Target checks C1-C6

1. dimension leave/rejoin while source stays loaded;
2. Sable/Aeronautics translation/rotation source tracking;
3. Sound Physics Remastered processing;
4. grouped MP3/ICY radio;
5. strict radio membership snapshot/rerun;
6. 8+ speaker finite + RAW stress.

Radio checks require the third argument. If radio is intentionally omitted, they are recorded as skipped rather than silently assumed.

## What diagnostics prove

Depending on the test, PASS requires measured evidence such as:

- the expected client sources/channels existed;
- each source reached OpenAL PLAYING;
- real channel-start skew stayed within the runner threshold;
- canonical playback clocks remained synchronized;
- PCM feed divergence stayed bounded;
- RAW admitted bytes were actually delivered through the client stream;
- no unexpected mid-stream PLAYING->STOPPED transition occurred;
- endpoint-local stop/gain/mute affected only the intended client source;
- loop/recovery produced the expected new renderer epoch without decoder failure;
- F3+T was observed as a real sound-engine reload and playback recovered;
- Sable movement changed requested and live OpenAL source positions together;
- Sound Physics attached/changed its direct filtering across the prepared environment comparison;
- late radio speakers did not join the already-running strict group until rerun.

The log records measured values instead of relying on a human claim that something “sounded synchronized.”

## Human actions still required

The operator may be asked to:

- walk out of range and return;
- press F3+T and wait for reload;
- change dimension and return;
- move/rotate the Sable contraption;
- move behind the prepared wall;
- connect an extra radio speaker;
- connect enough speakers to reach 8 total.

Press ENTER after completing the requested action. The operator does **not** choose PASS/FAIL.

## Result meaning

A **TARGET FULL PASS** means every required diagnostic and every selected target check passed.

A **CORE PASS / TARGET INCOMPLETE** means the automatic core passed but one or more target checks were skipped. That is not a runtime failure, but it is incomplete evidence for whatever was skipped.

## Failure evidence

On failure provide:

- `/v10-acceptance.log`;
- Minecraft `latest.log`;
- exact JAR hash;
- the exact test name and requested physical action;
- anything unusual that happened.

Do not mark runtime acceptance complete from CI alone.
