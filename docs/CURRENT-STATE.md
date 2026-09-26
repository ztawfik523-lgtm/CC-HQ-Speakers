# Current state

Updated: 2026-09-26

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Current diagnostic/runtime-test checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Current CI: `36238699768` — PASS  
Build baseline: NeoForge **21.1.247 only**  
Artifact: `10905071824`  
JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Protocol: **v10**, 9 payloads.

## Build/release policy

The project now builds/tests/packages one artifact against NeoForge 21.1.247. Its metadata accepts `[21.1,21.2)`, so that one artifact is the supported 21.1.x release candidate. Historical CI exercised 21.1.248, but there is no current second build or second runtime pass.

The current runtime candidate includes the concrete fixes found during live acceptance: native CC:T Sable-world positioning, OpenAL-safe Sound Physics diagnostics, correct native R1 attribution, and F3+T recovery diagnostics which no longer classify renderer-close cancellation as a decoder fault. CI `36238699768` is green.

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

Implementation is not waiting on another planned redesign. Live acceptance has now passed **A1-A19 and R1-R8** on the selected NeoForge 21.1.247 candidate.

Runtime-confirmed evidence now includes:

- native CC:T real client channels on Sable;
- MP3/WAV real renderer continuity;
- 2-speaker finite synchronization and endpoint-local client controls;
- the producer-fed continuous RAW fix;
- loop-boundary recovery/sync;
- real >32-block range leave/rejoin.

Remaining acceptance work:

- rerun R9 F3+T on the corrected harness/diagnostic classification;
- C1 dimension leave/rejoin if the source can remain loaded;
- C2 actual Sable translation/rotation tracking;
- C3 actual Sound Physics open-air/wall processing;
- C4/C5 grouped radio + strict membership;
- C6 8+ speaker finite/RAW stress.

Only a concrete runtime failure should send work back into source changes.
