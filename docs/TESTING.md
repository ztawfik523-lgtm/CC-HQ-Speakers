# Testing

Updated: 2026-09-26

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does not by itself prove real Minecraft/OpenAL playback, physical movement, listener-range recovery, Sound Physics behavior or realistic multi-speaker load.

Current tested code checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Current CI: `36205257110` — PASS  
Current build baseline: NeoForge 21.1.247 only  
Artifact: `10892998704`  
JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`

The artifact declares NeoForge `[21.1,21.2)`. Historical dual-version CI is compatibility history, not a reason to build 21.1.248 separately.

## Deterministic/CI coverage

The Java test suite covers finite analyzers/decoders/range transport/state machines/recovery/projection, storage/release limits, multi-lock behavior, RAW feed lifetime, radio group sealing, URL policy and diagnostic metric logic.

The shipped master Lua runner is compiled in CI using CC:T's Cobalt 0.9.9 parser. This caught an explicit dependency/import issue on 2026-09-23; it is fixed in the current green checkpoint.

The packaging check verifies required mod metadata, mixin config, embedded JLayer, embedded Sable Companion and the ROM Lua module.

## Runtime diagnostics

The normal JAR includes dormant built-in diagnostics. During acceptance they observe the actual client audio channels and feed measured state back to the integrated-server Lua test without adding a network payload.

Measured evidence includes channel creation/state, source position, source gain, PCM delivery, buffer health, playback offsets/latency where available, decoder/recovery events, sound-engine reloads, multispeaker timing and Sound Physics filters.

## One master runtime test

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

The master runner is the only normal user-facing acceptance workflow. Smaller scripts are developer isolation tools only after the master test identifies a specific failure.

Current master matrix:

- A1-A19: API surface, declared limits, staged media, native CC:T paths, MP3/WAV lifecycle, malformed media, argument/RAW bounds, RAW backpressure, finite shared authority, endpoint-local controls, gain/mute/clamp, shared controls, stress/restarts, stream security and final cleanup;
- R1-R9: real native channels, MP3/WAV renderer continuity, finite synchronization, endpoint-local client effects, continuous RAW, loop recovery/sync, range rejoin and F3+T recovery;
- C1-C6: dimension rejoin, Sable tracking, Sound Physics processing, grouped MP3/ICY radio, strict radio membership, 8+ speaker stress.

## Human involvement

The operator only performs actions Minecraft cannot automate:

- walk out of range and return;
- press F3+T;
- change dimension and return if the source remains loaded;
- translate/rotate the Sable contraption;
- move behind the prepared wall/obstacle;
- connect a late radio speaker;
- connect enough speakers to reach 8+.

The operator does **not** assign PASS/FAIL. The diagnostics do.

## Selected release scope

Required target is singleplayer + Sable/Aeronautics + Sound Physics Remastered. Dedicated-server/multiplayer and VS2 are intentionally not part of this release acceptance.

Use `RUNTIME-ACCEPTANCE-V10.md` for the exact procedure and `RUNTIME-RESULTS-V10.md` for results.
