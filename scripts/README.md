# Runtime scripts

Updated: 2026-09-29

## Current v11 focused acceptance

The active first runtime check is:

```
v11_c2_spr /cchq-speaker-runtime-test-48k-mono.mp3
```

`v11_c2_spr.lua` validates the current long-lived Sound Physics/acoustic integration with:

- normal Minecraft ground;
- one speaker/computer;
- one solid normal-world wall;
- SPR Update Moving Sounds OFF.

It automatically requires repeated HQ SPR processing, progressive direct probes, private per-source EFX and a measurable open->wall occlusion change on the same continuously-playing finite source.

It writes `/v11-c2-spr.log`.

See `docs/RUNTIME-ACCEPTANCE-V11.md` for the rest of the v11 validation plan.

## Historical v10 master/regression scripts

`v10_acceptance.lua` and its focused v10 probes remain useful regression/debug evidence, but they are **not** the final acceptance runner for protocol v11 because v11 changed gain/range transport and SPR/acoustic behavior.

Useful isolation scripts include:

- `v10_rejection_recheck.lua`;
- `v10_c4_finite.lua`;
- `v10_core_acceptance.lua`;
- `v10_raw_acceptance.lua`;
- `v10_raw_audio_probe.lua`;
- `v10_multispeaker_stress.lua`;
- `v10_radio_acceptance.lua`;
- `v10_runtime_observer.lua`.

Historical M0/M1 scripts remain milestone evidence only.

Do not make the user rerun broad old phases before the focused v11 checks unless a concrete regression needs isolation.
