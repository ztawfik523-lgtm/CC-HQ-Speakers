# Runtime results — protocol v10

Fill this during the frozen runtime pass.

## Build

- commit/JAR:
- NeoForge:
- CC:Tweaked:
- Sable/Aeronautics:
- VS2:
- Sound Physics Remastered:

## Automated scripts

| Test | 21.1.247 | 21.1.248 | Notes |
| --- | --- | --- | --- |
| v10_phase0_acceptance |  |  | Preferred combined runner; attach `/v10-phase0.log` on failure |
| p0_cc_speaker_contract |  |  |  |
| p0_finite_regression |  |  |  |
| v10_core_acceptance |  |  |  |
| v10_raw_acceptance |  |  |  |
| v10_multispeaker_stress 2 speakers |  |  |  |
| v10_multispeaker_stress 4 speakers |  |  |  |
| v10_multispeaker_stress 8+ speakers |  |  |  |
| v10_radio_acceptance |  |  |  |

## Manual gates

| Gate | Result | Notes |
| --- | --- | --- |
| native audibility |  |  |
| finite spatial sync |  |  |
| listener enter/leave/rejoin |  |  |
| resource reload/recovery |  |  |
| Sable movement finite |  |  |
| Sable movement RAW |  |  |
| Sable movement radio |  |  |
| VS2 movement finite |  |  |
| VS2 movement RAW |  |  |
| VS2 movement radio |  |  |
| RAW backpressure/audibility |  |  |
| radio strict late membership |  |  |
| radio long-run drift |  |  |
| ICY metadata |  |  |
| malformed/extreme media |  |  |
| storage/range/worker bounds |  |  |
| SPR native/finite/RAW/radio |  |  |
| realistic 2/4/8+ performance |  |  |

## Failures

For each failure record exact reproduction steps, expected/observed behavior, latest.log excerpt, and performance profile when relevant.

## Release verdict

Do not fill until every required gate has evidence.

- 21.1.247:
- 21.1.248:
- release blocker(s):
