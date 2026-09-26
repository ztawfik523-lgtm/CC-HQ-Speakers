# CC:HQ Speakers — diagnostic runtime handoff

Updated: 2026-09-26

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

This is the current handoff. Older dated handoffs are historical.

## Current exact checkpoints

Frozen product/source checkpoint:
`c61b052beee03ec0f36fed725fb37483bfb57d83`

API/docs freeze:
`bb0d68c7031bf97c7c7efc10c6458992222cf394`

Current source/test checkpoint before this documentation pass:
`37755ccdb34ac72a27797dc2e6581463e85cbcd7`

Current source/test CI:
`36205257110` — **PASS**

Current CI artifact:
`10892998704` — `hqspeaker-neoforge-21.1.247`

Extracted JAR:
`hqspeaker-1.1.4-1.21.1-neoforge.jar`

JAR SHA-256:
`0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`

Network protocol:
**v10, exactly 9 payloads**

Documentation-only commits follow the source/test checkpoint above. A new chat should fetch the current branch head/CI once, but should not reinterpret documentation-only commits as new runtime code.

## Critical build policy

**Build/test/package only against NeoForge 21.1.247.**

The 21.1.247-built mod metadata accepts `[21.1,21.2)`, and that one artifact is the release candidate for the supported NeoForge 21.1.x line.

Historical CI also built 21.1.248 and passed. That is historical compatibility evidence only. Do **not** add a 21.1.248 matrix job, produce a second 21.1.248 JAR, or require a second runtime pass.

## Latest CI issue already fixed

Commit `25a235df0ad2543ab5d8b83a29ca4cbf2f23dc92` added a test which compiles `scripts/v10_acceptance.lua` with CC:T's Cobalt Lua parser, but that first version failed CI because Cobalt was not on the test compile classpath and `LoadState` was imported from the wrong package.

Fixed by:

- `66a80e2f49fac8ebb9f067ac48a8255db46da055` — adds test dependency `cc.tweaked:cobalt:0.9.9`;
- `37755ccdb34ac72a27797dc2e6581463e85cbcd7` — imports `org.squiddev.cobalt.compiler.LoadState`.

CI `36205257110` is green after those fixes.

## Read these first

1. `docs/API-FREEZE-V10.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/RUNTIME-ACCEPTANCE-V10.md`
5. `docs/RUNTIME-RESULTS-V10.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ARCHITECTURE.md`
9. `docs/LUA-API.md`
10. exact current source/CI only as needed

Do not restart a broad cleanup/rethink cycle. The project is at runtime validation.

## Frozen product contract

Only normal `computercraft:speaker` is upgraded. The standalone HQ block is removed.

Native CC:T behavior is preserved through the real CC:T speaker peripheral.

Modern finite:

- MP3 + supported common WAV only;
- prepared immutable server assets and compatibility byte frontends use one modern engine;
- multispeaker playback uses one shared authority and independent physical endpoints;
- membership is a start-time snapshot;
- pause/resume/seek/loop and ordinary/All stop are shared;
- volume/mute are endpoint-local;
- `audioStopAt(index)` stops/detaches only that endpoint.

RAW:

- signed-16 mono 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- `hqspeaker_audio_empty` retry event;
- singular/All/At;
- grouped All preflights the target snapshot and uses a common future start tick.

Radio:

- MP3/ICY only;
- `speakStream`, `speakStreamAll`, `speakStreamAt`;
- strict start-time membership;
- no automatic late membership;
- rerun the stream command to include newly connected speakers;
- one shared decoder/prebuffer per client-local grouped session;
- HLS/TS remain removed.

Movement:

- resolver order is Sable Companion -> VS2 -> static block center;
- selected runtime target is Sable/Aeronautics;
- VS2 is not required for this release acceptance.

## Built-in diagnostic system

The normal release JAR includes dormant self-diagnostics. This was chosen deliberately as the strongest practical option; there is no separate diagnostic mod or special test-only JAR.

Lua diagnostic methods:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

The diagnostics observe the real client audio path rather than only server/Lua state:

- Minecraft/OpenAL channel creation;
- PLAYING/PAUSED/STOPPED state;
- queued/processed buffers;
- playback offset/output latency when available;
- source position and settled position error;
- OpenAL source gain;
- finite/RAW/radio PCM input/read;
- starvation and unexpected mid-stream stop transitions;
- RAW channel wakeups;
- finite decoder restart/failure/rejoin counters;
- sound-engine reloads;
- multispeaker real channel-start skew;
- canonical finite playback drift;
- PCM feed spread;
- native CC:T streaming/static channels;
- Sable source movement;
- Sound Physics direct-filter attachment/change.

In singleplayer, the integrated server and client share diagnostic state in-process. Protocol v10 therefore stays at 9 payloads.

## Important RAW history

A real runtime bug was previously found: one RAW chunk worked, but producer-fed continuous RAW could stop after the client stream exhausted its queue.

Root cause: HQ's client stream did not have CC:T-style continuation behavior.

Fix: when later PCM arrives after local exhaustion, the existing Minecraft/OpenAL channel is pumped again on the sound executor instead of filling the gap with fake silence.

The implementation/build is green, and the diagnostics now specifically detect admitted/read bytes, wakeups, starvation and unexpected PLAYING->STOPPED transitions.

**Do not call the RAW bug runtime-confirmed fixed until R6 passes in the current master run.**

## One master test

User-facing runner:
`scripts/v10_acceptance.lua`

Command:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

No separate phases for the user.

### A1-A19 deterministic

- frozen API surface;
- discovery/limits;
- staged media lifecycle;
- native single/all/indexed/backpressure;
- MP3 lifecycle/EOF;
- loop-wrap authority;
- WAV/indexed paths;
- malformed finite media;
- argument/RAW bounds;
- RAW backpressure/retry;
- RAW All/At admission;
- shared finite authority;
- endpoint-local stop;
- gain/mute/clamp;
- shared all/indexed controls;
- concurrent control stress;
- repeated synchronized starts;
- stream security/metadata;
- final idle cleanup.

### R1-R9 actual client diagnostics

- native CC:T channels;
- MP3 pause/resume renderer;
- WAV renderer continuity;
- finite multispeaker synchronization;
- endpoint-local controls at the client;
- continuous RAW client delivery;
- loop-boundary recovery/sync;
- range leave/rejoin;
- F3+T recovery.

### C1-C6 target/environment

- dimension leave/rejoin;
- Sable/Aeronautics source tracking;
- Sound Physics Remastered processing;
- grouped MP3/ICY radio;
- strict radio membership snapshot/rerun;
- 8+ speaker scale stress.

## User's selected test scope

The user explicitly wants:

- singleplayer;
- Sable/Aeronautics;
- no VS2 testing;
- no multiplayer/dedicated-server testing;
- Sound Physics;
- the rest of the relevant master coverage.

Do not turn VS2 or dedicated-server checks into release blockers for this target.

## Physical test setup

Start with:

- one CC:T computer on the Sable contraption;
- 2 normal CC:T speakers connected to it;
- 6 more normal speakers nearby, not necessarily connected yet;
- the MP3 and WAV test files on the computer;
- Sound Physics enabled;
- walls/room/obstacles nearby;
- room to move/rotate the Sable contraption;
- room to walk more than 32 blocks away and return;
- a direct MP3/ICY URL if doing radio;
- a chunk-loading solution for the dimension check if that check is to be completed meaningfully.

The extra speakers are added when prompted. Do not require 8 connected from the start.

The radio strict-membership test is intentional: start radio with current membership, connect a new speaker, prove it does not join, rerun the command, then prove it joins.

## Test media

Known supplied assets:

MP3:
`cchq-speaker-runtime-test-48k-mono.mp3`
SHA-256 `59d096c10eb91c5b27cc8b81d948bcb5092b5bfc20023d516bffe22bccd88eb1`
size 640,940 bytes

WAV:
`cchq-speaker-runtime-test-48k-mono.wav`
SHA-256 `264bbd62c2ead7ef4201adf182b42bdcff8ce25c87819f47a03041988ac0acab`
size 3,840,044 bytes

Both are 40-second mono 48-kHz test media with recurring marker structure.

## Sound Physics nuance

Test Sable movement and Sound Physics independently first.

Sable test: diagnostics prove the HQ requested source and actual OpenAL source follow translation/rotation.

Sound Physics test: use a stable source and change the player/environment from open to behind a wall/inside the prepared room. Diagnostics prove Sound Physics attached/changed its filter.

Do not treat Sound Physics's own moving-sound reevaluation defaults as an HQ tracking failure.

## Human interaction

The user should not manually grade every sound.

When requested, they only:

- walk away/return;
- press F3+T;
- change dimension/return;
- move/rotate Sable;
- move behind an obstacle;
- connect speakers.

The diagnostic system decides PASS/FAIL.

One trivial real-world sanity check that the game/audio device is not globally muted is reasonable; subjective audio quality is not the primary acceptance mechanism.

## If the master fails

Ask for:

- `/v10-acceptance.log`;
- Minecraft `latest.log`;
- exact test name;
- exact JAR hash;
- what physical action was being performed.

Use the diagnostic metrics to identify the exact subsystem. If a diagnostic assertion itself is wrong, fix the test rather than changing working playback code.

Do not rationalize away a diagnostic failure or user-observed broken behavior.

Use smaller scripts only to isolate a concrete master-test failure.

## Result meaning

`TARGET FULL PASS` = selected runtime target is accepted.

`CORE PASS / TARGET INCOMPLETE` = core diagnostics passed but a target/environment check was skipped. It is not a failure, but it is incomplete evidence for that skipped part.

## After target FULL PASS

Then:

1. write final runtime results into `docs/RUNTIME-RESULTS-V10.md`;
2. reconcile any bug-fix docs;
3. final package/dependency check;
4. decide release version/changelog;
5. promote/merge `codex/m1j-multispeaker` to the intended default branch;
6. cut the single 21.1.247-built release artifact.

## Style/workflow preferences from the project owner

- be direct and concise during live testing;
- do not split user-facing testing back into phases;
- direct downloadable files are preferred;
- do not add speculative future-proofing/bloat;
- if there are genuinely meaningful technical alternatives, explain the tradeoffs briefly and let the user choose;
- strict radio membership/no automatic late join is intentional;
- `audioStopAt` must only stop the named/indexed endpoint;
- only change source when evidence shows a real bug.
