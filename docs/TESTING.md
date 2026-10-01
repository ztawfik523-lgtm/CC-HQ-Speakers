# Testing

Updated: 2026-10-01

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does **not** prove real Minecraft/OpenAL/Sound Physics behavior.

Current candidate checkpoint: `cd9a3f67ac3449df975c00240d83ee8e33239fc9`  
Last production-code commit: `11a22e9f122ebf511744ac0aa1b17512fd69b6eb`  
CI: `36783159001` — PASS  
Artifact: `11128792552` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `ce91a67e8c20d82bb7e1bcc82bee94a025edfec426cecede2035fdfda4337d76`.
Runtime script checkpoint: `d90b0b536dfbb77239deb3739e1079069aa80ba9`; script CI `36933446358` — PASS.

Build/test/package only NeoForge 21.1.247.

## Deterministic/source checks now covered

The current CI suite includes checks for:

- protocol v11 and exactly 9 payload registrations;
- server-resolved gain/range carried by finite and RAW/radio packet shapes;
- anchor interpolation and invalid volume/range rejection;
- range/profile invariants;
- HQ SPR scheduler thresholds and priority behavior;
- physical speaker position used for scheduler decisions;
- private per-source HQ EFX rather than SPR shared filters;
- private EFX creation only after PLAYING/PAUSED;
- reflection-position persistence/stabilization wiring;
- packaged mod structure.
- Cobalt compilation of `v11_c2_spr.lua`, `v11_runtime_1.lua` and `v11_runtime_2.lua`;
- finite format surface locked to MP3 + common WAV;
- single composite speaker-group registry/discovery path;
- absence of the dead native RAW ready-event proxy path;
- narrow MP3-only radio capability advertisement.

These tests intentionally do not pretend to replace in-game OpenAL/SPR evidence.

## Historical runtime regression evidence

The v10 runtime campaign established:

- A1-A19 deterministic/runtime contract checks;
- R1-R9 real client/OpenAL checks;
- C1 Sable tracking;
- C3 grouped MP3/ICY radio, metadata and strict membership;
- C4 RAW at eight speakers;
- corrected C4 finite catch-up behavior after the range-admission fix;
- focused A8/A9/A18 rejection checks.

Keep those as regression evidence. Do not call them v11 acceptance because v11 changed gain/range transport and active SPR acoustics.

## Active v11 runtime plan

Use `RUNTIME-ACCEPTANCE-V11.md`.

Focused C2 already passed and is not repeated. The remaining runtime work is deliberately two integrated scripts on one exact JAR in one Minecraft launch:

1. `scripts/v11_runtime_1.lua` — exactly two normal-world speakers:
   - tuning/range interpolation and rejection;
   - endpoint-local controls;
   - live config reload;
   - simultaneous SPR private-filter isolation;
   - F3+T recovery;
   - 70-80 block SPR playback/occlusion.
2. `scripts/v11_runtime_2.lua` — exactly eight Sable/Aeronautics speakers:
   - finite scale/catch-up;
   - Sable movement;
   - RAW continuation/backpressure;
   - MP3/ICY radio + metadata.

Both scripts are self-judging and log PASS/FAIL. Manual actions are limited to physical game actions which cannot be initiated from Lua.

The final script recheck also covers the runner itself: all-eight RAW drain rather than anchor-only drain, early finite looping for short media, bounded polling for F3+T recovery, realistic long-range travel time, and mandatory config restore before Test 1 may exit.

## Release verdict

Do not mark v11 release-ready until the focused v11 checks pass and the docs are frozen to the runtime-tested artifact.

Dedicated multiplayer, VS2 runtime, >8 streamed-source guarantee and a separate NeoForge 21.1.248 build remain outside the selected release target.
