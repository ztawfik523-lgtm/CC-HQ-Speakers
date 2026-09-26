# CC:HQ Speakers — agent guide

Updated: 2026-09-26

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`  
Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze checkpoint: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Current CI: `36205257110` — PASS, NeoForge 21.1.247 only  
Current artifact: `10892998704` / JAR SHA-256 `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`  
Protocol: **v10**, exactly 9 payloads.

## Build policy

Build, test and package **only against NeoForge 21.1.247**. That one artifact declares `[21.1,21.2)` and is used across the supported NeoForge 21.1.x line. Historical CI also exercised 21.1.248, but do not restore a duplicate 21.1.248 job or create a second release JAR.

## Authority order

1. `docs/API-FREEZE-V10.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/RUNTIME-ACCEPTANCE-V10.md`
5. `docs/RUNTIME-RESULTS-V10.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ARCHITECTURE.md`
9. `docs/LUA-API.md`
10. `docs/HANDOFF-2026-09-26-DIAGNOSTIC-RUNTIME.md`
11. exact current source and CI

Older dated handoffs and milestone documents are historical evidence only.

## Frozen product rules

- only normal `computercraft:speaker` is upgraded;
- preserve native CC:T speaker behavior;
- modern finite formats: MP3 + supported common WAV only;
- finite multispeaker: one shared authority, independent physical endpoints, start-time snapshot;
- shared controls: pause/resume/seek/loop and ordinary/All stop;
- endpoint-local volume/mute; `audioStopAt` is endpoint-local detach/stop;
- RAW: signed-16 mono 48 kHz, max 131072 samples/call, bounded backpressure, singular/All/At;
- MP3/ICY radio: singular/All/At, strict snapshot, no automatic late membership;
- grouped radio uses one shared decoder/prebuffer per client-local group;
- HLS and MPEG-TS are removed;
- movement resolver order remains Sable -> VS2 -> static;
- JLayer 1.0.1.4 and Sable Companion 1.6.0 are embedded;
- protocol remains v10 with 9 payloads.

Do not re-add retired formats/APIs, the standalone HQ block, expected-count live membership, or a second finite engine without a new explicit product decision.

## Built-in diagnostics

The normal release JAR carries dormant runtime diagnostics. They are enabled only by the master acceptance runner through `hqDiagEnable(true)`.

Diagnostics inspect the real client audio path: Minecraft/OpenAL channel creation/state, PCM delivery, buffer health, source position, playback offset/latency when available, decoder/recovery events, sound-engine reloads, multispeaker timing, Sable movement and Sound Physics direct-filter behavior. In the selected singleplayer test scope, client and integrated server share the diagnostic state in-process, so no protocol payload was added.

The master runner is `scripts/v10_acceptance.lua`. It automatically judges PASS/FAIL; the operator only performs physical actions the game cannot automate.

## Selected runtime scope

Release acceptance is:

- singleplayer;
- Sable/Aeronautics;
- Sound Physics Remastered;
- native CC:T + finite + RAW + MP3/ICY radio;
- 2-speaker core and 8+ speaker scale stress;
- range, dimension and F3+T recovery.

Dedicated-server/multiplayer and VS2 runtime validation are intentionally outside this acceptance scope and are not release blockers for this target.

## Current work

Implementation and diagnostic instrumentation are complete enough for runtime acceptance. Do not restart broad design/cleanup work.

The next meaningful step is to run the current master test on the current 21.1.247-built JAR. Only return to source when a runtime failure, build failure, security problem or concrete inconsistency provides evidence of a bug.

Green CI is not runtime proof.
