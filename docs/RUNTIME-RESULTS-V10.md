# Runtime results — protocol v10

Fill this during the final diagnostic master run.

## Build

- commit/JAR:
- built against NeoForge: **21.1.247**
- supported NeoForge metadata range: **[21.1,21.2)**
- CC:Tweaked:
- Sable/Aeronautics:
- Sound Physics Remastered:
- direct MP3/ICY radio URL used: yes / no

The release is built once against NeoForge 21.1.247. Do not create a separate 21.1.248 artifact.

## Master acceptance

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

| Result | Status | Notes |
| --- | --- | --- |
| deterministic A1-A19 |  | API, staged media, native CC:T, finite/RAW bounds, control stress, security |
| real-client R1-R7 |  | native client channel, MP3/WAV continuity, sync, endpoint stop, RAW, loop |
| R8 range leave/rejoin |  | physical action; diagnostics judge result |
| R9 F3+T recovery |  | physical action; diagnostics judge result |
| C1 dimension leave/rejoin |  | target check; may be skipped only if source cannot remain loaded |
| C2 Sable/Aeronautics tracking |  | target check |
| C3 Sound Physics Remastered |  | target check |
| C4 grouped MP3/ICY radio |  | target check; requires radio URL |
| C5 strict radio membership |  | target check; requires extra speaker + radio URL |
| C6 8+ speaker scale stress |  | target check |

A **TARGET FULL PASS** requires every required runtime diagnostic and every selected target-scope check to pass. A **CORE PASS / TARGET INCOMPLETE** means the automatic core passed but one or more target checks were skipped.

## Evidence

Keep:

- `/v10-acceptance.log`
- Minecraft `latest.log` if anything fails
- the exact JAR hash
- any unusual event while performing a requested physical action

The operator performs requested actions but does not assign PASS/FAIL; the built-in diagnostics do that.

## Failures

For each failure record the exact test name, diagnostic message, relevant measured values, reproduction steps and `latest.log` excerpt.

## Release verdict

Do not fill until the master runner has completed.

- master result:
- target-scope acceptance:
- release blocker(s):
