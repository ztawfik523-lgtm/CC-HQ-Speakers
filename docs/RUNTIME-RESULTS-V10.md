# Runtime results — protocol v10

Updated: 2026-09-26

Fill this during the final diagnostic master run.

## Build

- code/test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`
- CI: `36205257110` — PASS
- GitHub artifact: `10892998704`
- JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`
- built against NeoForge: **21.1.247**
- supported NeoForge metadata range: **[21.1,21.2)**
- Minecraft: 1.21.1
- CC:Tweaked: 1.120.0
- Sable/Aeronautics: _fill at runtime_
- Sound Physics Remastered: expected 1.21.1-1.5.1; _confirm at runtime_
- direct MP3/ICY radio URL used: yes / no

Do not create a separate 21.1.248 artifact.

## Master acceptance

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

| Result | Status | Notes |
| --- | --- | --- |
| A1-A19 deterministic | PENDING | API, staging, native CC:T, finite/RAW admission/bounds, controls, stress, security |
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
