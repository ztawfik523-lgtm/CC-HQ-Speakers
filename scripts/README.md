# Runtime scripts

Current frozen-v10 release-candidate scripts:

- `v10_phase0_acceptance.lua <mp3> <wav>` — preferred complete Phase 0 runner. Requires at least 2 attached speakers, uses explicit timer-based waits, shows a live dashboard on the first attached monitor, and writes `/v10-phase0.log`.
- `p0_cc_speaker_contract.lua` — native CC:T contract/backpressure isolation.
- `p0_finite_regression.lua <mp3>` — finite loop/end-state regression isolation.
- `v10_core_acceptance.lua <mp3> [wav]` — frozen API + finite/RAW/core multispeaker isolation.
- `v10_raw_acceptance.lua` — RAW bounded backpressure/retry and group-admission isolation.
- `v10_multispeaker_stress.lua <mp3> [cycles]` — repeated 2+ endpoint control/replacement stress.
- `v10_radio_acceptance.lua [mp3-radio-url]` — direct/At/strict-All MP3/ICY checks.
- `v10_runtime_observer.lua [seconds]` — timer-driven event/status observer with monitor dashboard and `/v10-runtime-observer.log`.

For Phase 0, run `v10_phase0_acceptance <mp3> <wav>`. Use the smaller P0/core/RAW scripts only to isolate a failure.

Automated PASS never substitutes for the manual audibility, spatial synchronization, listener/recovery, movement, radio synchronization, Sound Physics Remastered, or performance gates.

Run the later phases in the order defined by `docs/RUNTIME-ACCEPTANCE-V10.md`.

Historical milestone scripts (`m0-*`, `m1_*`, `m1a_*`, `m1c_*`, `m1d_*`, `m1e_*`) are not current release acceptance unless that document explicitly says otherwise.
