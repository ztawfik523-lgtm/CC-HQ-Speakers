# CC:HQ Speakers — agent guide

Updated: 2026-09-22

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`  
Frozen source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
Source-freeze CI: `35655973164` — NeoForge 21.1.247 and 21.1.248 PASS.  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`.  
Runtime-prep head: `a234ba02b80532daf32f6849061b76f23c0eb4d3` / CI `35657182389` — PASS.  
Protocol: **v10**, 9 payloads.
Current build policy: build/test/package only against NeoForge 21.1.247. The resulting artifact declares `[21.1,21.2)` and is the single release artifact for the supported 21.1.x range; do not add a duplicate 21.1.248 CI job.

## Authority order

1. `docs/API-FREEZE-V10.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/LUA-API.md`
8. `docs/HANDOFF-2026-09-22-RUNTIME.md`
9. exact current source and CI

Older dated handoffs/milestone docs are historical.

## Frozen product rules

- only normal `computercraft:speaker` is upgraded;
- preserve native CC:T speaker methods/events;
- modern finite formats: MP3 + supported common WAV only;
- finite multispeaker: one shared authority, independent physical endpoints, start-time snapshot;
- shared controls: pause/resume/seek/loop and ordinary/All stop;
- endpoint-local volume/mute; `audioStopAt` is endpoint-local detach/stop;
- RAW: signed-16 mono 48 kHz, max 131072 samples/call, bounded backpressure, singular/All/At;
- MP3/ICY radio: `speakStream`, `speakStreamAll`, `speakStreamAt`; strict snapshot; no automatic late membership;
- grouped radio uses one shared decoder/prebuffer per client-local group;
- HLS and MPEG-TS are removed;
- all HQ movement uses Sable -> VS2 -> static fallback;
- protocol v10;
- JLayer 1.0.1.4 and Sable Companion 1.6.0 are embedded.

Do not re-add OGG/generic whole-file aliases, stale legacy control aliases, HLS/TS, a standalone speaker block, a second finite engine, or shared finite decode fan-out without a new explicit product/performance decision.

## Freeze rule

The public v10 surface is frozen for runtime acceptance. Do not add/remove methods or alter semantics unless runtime testing exposes a release-blocking bug. Bug fixes should preserve the freeze wherever possible.

Green CI is not runtime proof.
