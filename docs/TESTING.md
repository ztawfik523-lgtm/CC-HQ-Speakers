# Testing

Updated: 2026-09-29

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does **not** prove real Minecraft/OpenAL/Sound Physics behavior.

Current implementation checkpoint: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`  
CI: `36467453786` — PASS  
Artifact: `10989724714` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`.

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

### First: C2 live SPR/acoustics

Requirements:

- one computer + one speaker on normal Minecraft ground;
- one solid normal-world wall;
- Sound Physics Remastered 1.21.1-1.5.1;
- SPR **Update Moving Sounds OFF**;
- the current v11 candidate JAR.

Command:

```
v11_c2_spr /cchq-speaker-runtime-test-48k-mono.mp3
```

The script automatically requires:

- real SPR `processSound` evidence;
- at least one long-lived re-evaluation beyond startup;
- progressive direct probes;
- a private HQ direct EFX filter;
- additional reevaluation after listener movement;
- measurable open->wall direct gain/HF reduction on the same continuously-playing source.

Send back `/v11-c2-spr.log`, `latest.log` and `debug.log`.

### Then: volume/range/config

Verify representative automatic anchors/interpolation, explicit range override, Lua error boundaries and live config behavior:

```text
existing playback keeps old profile
new playback after reload uses new profile
```

### Then: regression/scale

Recheck finite/RAW/radio gain+range, pause/resume/seek/loop, endpoint-local controls and eight-speaker finite + RAW behavior under v11.

Also verify simultaneous occluded/clear speakers do not cross-contaminate private SPR filter state.

## Release verdict

Do not mark v11 release-ready until the focused v11 checks pass and the docs are frozen to the runtime-tested artifact.

Dedicated multiplayer, VS2 runtime, >8 streamed-source guarantee and a separate NeoForge 21.1.248 build remain outside the selected release target.
