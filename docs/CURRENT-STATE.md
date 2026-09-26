# Current state

Updated: 2026-09-26

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Current CI: `36205257110` — PASS  
Build baseline: NeoForge **21.1.247 only**  
Artifact: `10892998704`  
JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`  
Protocol: **v10**, 9 payloads.

## Build/release policy

The project now builds/tests/packages one artifact against NeoForge 21.1.247. Its metadata accepts `[21.1,21.2)`, so that one artifact is the supported 21.1.x release candidate. Historical CI exercised 21.1.248, but there is no current second build or second runtime pass.

The most recent CI failure was only the newly added Cobalt Lua-syntax test missing its explicit test dependency/import. Commits `66a80e2f49fac8ebb9f067ac48a8255db46da055` and `37755ccdb34ac72a27797dc2e6581463e85cbcd7` fixed that. CI `36205257110` is green.

## Product state

Only the normal `computercraft:speaker` is upgraded. Standalone HQ block is removed. Internal custom audio uses `hqspeaker:hq_audio_source`. License is MPL-2.0.

Frozen supported paths:

- native CC:T speaker behavior;
- modern finite MP3 + supported common WAV;
- prepared immutable finite media;
- modern finite multispeaker shared authority;
- signed-16 mono 48-kHz RAW;
- MP3/ICY radio singular/All/At with strict no-auto-membership grouping.

HLS, MPEG-TS, OGG/generic whole-file aliases, duplicate finite engine and stale legacy control aliases remain removed.

All HQ positional paths resolve Sable Companion -> VS2 -> static block center.

## Diagnostic subsystem

The release JAR now includes dormant built-in diagnostics. The master acceptance runner enables them only while testing.

Diagnostics observe actual client-side playback, including:

- Minecraft/OpenAL channel start and state;
- queued/processed buffer health;
- PCM admitted/read and starvation/underrun evidence;
- real source position and settled position error;
- OpenAL gain;
- playback offsets and output latency when supported;
- finite decoder restarts/failures and authoritative recovery;
- sound-engine reloads;
- multispeaker channel-start skew and playback drift;
- Sable source movement;
- Sound Physics direct-filter application/change;
- native CC:T streaming/static speaker channels.

Singleplayer diagnostics share state in-process with the integrated server, so protocol v10 remains exactly 9 payloads.

The diagnostic surface is `hqDiagEnable`, `hqDiagReset`, `hqDiagSnapshot`, `hqDiagCapabilities`.

## Master acceptance

There is now one user-facing test: `scripts/v10_acceptance.lua`.

It contains:

- A1-A19 deterministic/API/admission/control/bounds/security checks;
- R1-R9 real-client diagnostics;
- C1-C6 selected environment/scale checks.

The operator no longer grades the audio manually. When a physical action is required, the script asks for the action and then judges the result from diagnostics.

Selected release scope:

- singleplayer;
- Sable/Aeronautics;
- Sound Physics Remastered;
- native CC:T, finite, RAW, radio;
- 2-speaker normal tests and 8+ speaker scale stress;
- range, dimension and F3+T recovery.

Dedicated-server/multiplayer and VS2 are intentionally outside this acceptance scope.

## Remaining work

Implementation is not waiting on another planned redesign. The next step is **runtime testing** of the current JAR with the master runner.

Important evidence still not claimed until that run passes:

- the producer-fed RAW continuation fix is CI/build-verified but still needs current in-game diagnostic confirmation;
- actual finite/RAW multispeaker timing in the user's environment;
- real range/rejoin and F3+T recovery;
- actual Sable tracking;
- actual Sound Physics processing;
- grouped radio behavior if a direct MP3/ICY URL is supplied;
- 8+ speaker stress;
- optional dimension leave/rejoin if the source can remain loaded.

Only a concrete runtime failure should send work back into source changes.
