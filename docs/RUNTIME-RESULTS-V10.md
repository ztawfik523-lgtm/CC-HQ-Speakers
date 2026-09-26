# Runtime results — protocol v10

Updated: 2026-09-26

Fill this during the final diagnostic master run.

## Build

- current code/test checkpoint: `1c46b6b4804b342ca02416171afd9bfc1c55d940`
- CI: `36237053212` — PASS
- GitHub artifact: `10905015122`
- JAR SHA-256: `c404ba80fae0910b5d3c9de4da25c5ea70718815a1abc863a6766c489f31562a`
- previous locked pre-runtime checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`
- built against NeoForge: **21.1.247**
- supported NeoForge metadata range: **[21.1,21.2)**
- Minecraft: 1.21.1
- CC:Tweaked: 1.120.0
- Sable/Aeronautics: Sable 2.0.5 / Create Aeronautics 1.3.2 confirmed in attempt 3
- Sound Physics Remastered: 1.21.1-1.5.1 confirmed loaded in attempt 3
- direct MP3/ICY radio URL supplied: `https://stream.nightride.fm/nightride.mp3`

Do not create a separate 21.1.248 artifact.

## Master acceptance

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

| Result | Status | Notes |
| --- | --- | --- |
| A1-A19 deterministic | PASS (attempts 2-3) | complete deterministic core passed on exactly 2 speakers |
| R1 native client channels | PENDING RERUN | attempt 3 proved the Sable position fix and real native static/streaming channels; runner attribution + OpenAL-safe diagnostics fixed afterward |
| R2 MP3 pause/resume renderer | PENDING | client/OpenAL |
| R3 WAV renderer continuity | PENDING | client/OpenAL |
| R4 finite multispeaker sync | PENDING | channel-start skew + canonical drift |
| R5 endpoint-local client controls | PENDING | stop/gain/mute isolation |
| R6 continuous RAW | PENDING | confirms producer-fed continuation fix in-game |
| R7 loop-boundary recovery/sync | PENDING | renderer recovery |
| R8 range leave/rejoin | PENDING | physical action; diagnostics judge |
| R9 F3+T recovery | PENDING | physical action; diagnostics judge |
| C1 dimension leave/rejoin | PENDING | run only with source chunk kept loaded |
| C2 Sable/Aeronautics tracking | PENDING | target check |
| C3 Sound Physics Remastered | PENDING | target check |
| C4 grouped MP3/ICY radio | PENDING | requires direct radio URL |
| C5 strict radio membership | PENDING | requires direct radio URL + extra speaker |
| C6 8+ speaker scale stress | PENDING | finite + RAW |

A **TARGET FULL PASS** requires every required runtime diagnostic and every selected target-scope check to pass. A **CORE PASS / TARGET INCOMPLETE** means the automatic core passed but one or more target checks were skipped.

## Evidence to keep

- `/v10-acceptance.log`
- Minecraft `latest.log` on any failure
- exact JAR hash
- any unusual event while performing a requested physical action

The operator performs requested actions but does not assign PASS/FAIL; built-in diagnostics do.

## Attempt 1 — 2026-09-26

- runner reached A9 in about 9.3 seconds;
- A1-A8 passed;
- A9 successfully rejected empty/out-of-range/non-number/non-finite RAW, non-finite volume, out-of-range speaker index, and maxSamples+1 before the runner itself failed;
- harness failure: `/v10_acceptance.lua:887: attempt to call global 'collectgarbage' (a nil value)`;
- fix: removed the unsupported call and added a regression guard against reintroducing it;
- Minecraft log also exposed `java.util.Optional` being returned directly by `getStreamUrl()`; CC:T converted that unknown Java type to nil;
- fix: `getStreamUrl()` now returns zero Lua values for inactive/nil and one string value when active, with a regression check;
- startup evidence showed 4 attached speakers. The corrected master runner now requires exactly 2 at startup so the 2-speaker core and later membership/8+ transitions are unambiguous;
- Sound Physics Remastered was not loaded in that launch, so C3 could not have produced a target full pass;
- no R1-R9/C1-C6 client diagnostic result was reached; those remain pending.

## Attempt 2 — 2026-09-26

- startup correctly saw exactly 2 attached speakers on the Sable contraption;
- A1-A19 all passed; the complete deterministic core is now runtime-confirmed;
- diagnostics enabled successfully, then R1 failed with `clientSeen=false`, `sourceCount=0`, and no OpenAL renderer/vendor evidence because no native CC:T sound channel was created;
- root cause: the real CC:T block speaker publishes its raw block-centre coordinates for native `playNote`, `playSound`, and `playAudio`. Inside a Sable sub-level those are plot-space coordinates (for this run around x/z 20,481,0xx), not the contraption's actual world-space position, so Minecraft culls the native source before a real client channel exists;
- fix: a narrow mixin replaces only CC:T's returned `SpeakerPosition` with the existing `MovingSourcePosition` Sable/VS2/static resolver. The real CC:T peripheral still owns native playback, buffering, packets, events, and stop semantics;
- static speakers retain the same block-centre coordinates through the resolver fallback;
- NeoForge 21.1.247 CI passed with the position fix;
- R2-R9 and C1-C6 were not reached and remain pending.

## Attempt 3 — 2026-09-26

- startup again saw exactly 2 attached speakers and A1-A19 all passed;
- the native Sable position fix worked: diagnostics became client-visible and observed OpenAL Soft, integrated singleplayer, Sound Physics Remastered, and 2 real native CC:T sources;
- native source positions were real world-space coordinates around x 53 / y 229 / z -24 rather than Sable plot-space coordinates around x/z 20,481,0xx;
- one real `native-static` source (CC:T `playSound`) and one real streaming `native` source (CC:T DFPWM `playAudio`) were observed healthy;
- R1 still failed because the runner incorrectly expected `playNote` to appear as a second `SpeakerSound`-backed static source. CC:T implements `playNote` with Minecraft's ordinary `ClientboundSoundPacket`, so it is not safely attributable through the SpeakerSound hook; A4 continues to cover native `playNote` deterministically while R1 now checks attributable real client channels for `playSound` and `playAudio`;
- `latest.log` also showed repeated OpenAL `Invalid enumerated parameter value` errors only after diagnostics enabled. Root cause was our diagnostic use of `alGetSourcei(AL_DIRECT_FILTER)`; OpenAL Soft treats that property as non-queryable;
- fix: diagnostics no longer query `AL_DIRECT_FILTER`. An optional SPR mixin records the exact direct gain/cutoff values when Sound Physics applies `setEnvironment`, so C3 remains automatic without contaminating OpenAL's error state;
- regression tests now guard both the CraftOS runner contract and the forbidden OpenAL getter;
- NeoForge 21.1.247 CI passed with the complete R1/SPR diagnostic fix;
- R2-R9 and C1-C6 were not reached and remain pending.

## Failures

For each failure record:

- exact test name;
- diagnostic message;
- relevant measured values;
- reproduction steps;
- `latest.log` excerpt if relevant;
- whether the failure repeats.

## Release verdict

Do not fill until the master runner has completed.

- master result:
- target-scope acceptance:
- release blocker(s):
- release-ready: yes / no
