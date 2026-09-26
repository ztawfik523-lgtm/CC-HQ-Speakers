# Runtime scripts

Updated: 2026-09-26

## User-facing release acceptance

Use exactly one normal acceptance runner:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

`v10_acceptance.lua` combines deterministic checks with the mod's built-in client/OpenAL diagnostics. It writes `/v10-acceptance.log`.

The operator no longer presses PASS/FAIL for audio quality. The script may ask the operator to perform physical actions such as walking out of range, pressing F3+T, moving the Sable contraption, moving behind an obstacle, changing dimension or connecting speakers. The diagnostics decide the result.

The current master contains:

- A1-A19 deterministic/API/admission/control/bounds/security checks;
- R1-R9 real-client diagnostics;
- C1-C6 environment/scale checks.

Selected release scope is singleplayer + Sable/Aeronautics + Sound Physics Remastered. Dedicated-server/multiplayer and VS2 are outside scope.

## Isolation/debug scripts

These remain useful only if the master runner identifies a concrete failure:

- `p0_cc_speaker_contract.lua` — native CC:T contract isolation;
- `p0_finite_regression.lua` — finite regression isolation;
- `v10_core_acceptance.lua` — older core API/finite/RAW isolation;
- `v10_raw_acceptance.lua` — RAW admission/backpressure isolation;
- `v10_raw_audio_probe.lua` — focused RAW audible/continuation probe;
- `v10_multispeaker_stress.lua` — older focused multispeaker stress;
- `v10_radio_acceptance.lua` — focused radio isolation;
- `v10_runtime_observer.lua` — event/status observation.

`v10_phase0_acceptance.lua` and the old phased workflow are superseded. Do not make the user run phases unless isolating a master-test failure.

Historical milestone scripts (`m0-*`, `m1_*`, `m1a_*`, `m1c_*`, `m1d_*`, `m1e_*`) are not current release acceptance.
