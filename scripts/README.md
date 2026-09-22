# Runtime scripts

Current frozen-v10 release-candidate scripts:

- `p0_cc_speaker_contract.lua` — native CC:T contract/backpressure.
- `p0_finite_regression.lua <mp3>` — finite loop/end-state regressions.
- `v10_core_acceptance.lua <mp3> [wav]` — frozen API + finite/RAW/core multispeaker checks.
- `v10_raw_acceptance.lua` — RAW bounded backpressure/retry and group admission.
- `v10_multispeaker_stress.lua <mp3> [cycles]` — repeated 2+ endpoint control/replacement stress.
- `v10_radio_acceptance.lua [mp3-radio-url]` — direct/At/strict-All MP3/ICY checks.
- `v10_runtime_observer.lua [seconds]` — timer-driven event/status observer with monitor dashboard and `/v10-runtime-observer.log`.

Run them in the order defined by `docs/RUNTIME-ACCEPTANCE-V10.md`.

Historical milestone scripts (`m0-*`, `m1_*`, `m1a_*`, `m1c_*`, `m1d_*`, `m1e_*`) are not current release acceptance unless that document explicitly says otherwise.
\n\nFor Phase 0, prefer `v10_phase0_acceptance <mp3> <wav>`. The four smaller P0/core/RAW scripts remain useful for isolating a failure. Automated PASS never substitutes for the manual audibility/spatial/movement/SPR gates.\n