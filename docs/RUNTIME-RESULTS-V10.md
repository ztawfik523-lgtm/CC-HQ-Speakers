# Runtime results — protocol v10

Updated: 2026-09-26

Fill this during the final diagnostic master run.

## Build

- current code/test checkpoint: `1213c5a67469011449f637cd78bdcd543e6e607f`
- CI: `36235585325` — PASS
- GitHub artifact: `10903453956`
- JAR SHA-256: `0e1af590054cc0065aabfc09ba1454dfd8f97455b17517420fbdf5fb0724a74f`
- previous locked pre-runtime checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`
- built against NeoForge: **21.1.247**
- supported NeoForge metadata range: **[21.1,21.2)**
- Minecraft: 1.21.1
- CC:Tweaked: 1.120.0
- Sable/Aeronautics: Sable 2.0.5 / Create Aeronautics 1.3.2 observed in first attempted run
- Sound Physics Remastered: expected 1.21.1-1.5.1; **not loaded in first attempted run**
- direct MP3/ICY radio URL supplied: `https://stream.nightride.fm/nightride.mp3`

Do not create a separate 21.1.248 artifact.

## Master acceptance

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

| Result | Status | Notes |
| --- | --- | --- |
| A1-A8 deterministic | PASS (attempt 1) | API through malformed finite-media rejection passed before harness failure |
| A9-A19 deterministic | PENDING RERUN | A9 reached and passed all rejection checks through `RAW maxSamples+1`, then old runner called unavailable CraftOS `collectgarbage()`; runner fixed |
| R1 native client channels | PENDING | real CC:T client audio evidence |
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
