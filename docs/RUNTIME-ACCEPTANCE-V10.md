# Runtime acceptance — protocol v10

Updated: 2026-09-23

## Goal

Run one master acceptance against the actual release JAR. The mod's built-in diagnostics judge client/Minecraft/OpenAL behavior automatically; the person running the test only performs physical actions Minecraft cannot perform by itself.

Chosen runtime scope for this release pass: **singleplayer + Sable/Aeronautics + Sound Physics Remastered**. Dedicated-server/multiplayer and VS2 are intentionally outside this acceptance scope.

## Build target

- Build and test one artifact against NeoForge 21.1.247.
- Use that same artifact across the supported NeoForge 21.1.x metadata range (`[21.1,21.2)`); do not produce a duplicate 21.1.248 build.
- Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0.

Protocol remains **v10 with 9 payloads**. Diagnostics use an in-process singleplayer bridge and do not add a network payload.

## Built-in diagnostic surface

The normal release JAR exposes:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

Diagnostics are dormant until explicitly enabled by the acceptance runner. They do not replace or change audio ownership/control semantics.

While enabled, the client records the real HQ/CC:T audio channels: play/pause/stop state, channel creation/detach, queued/processed OpenAL buffers, source position, playback offset and output latency when supported, PCM input/delivery, RAW wakeups, finite decoder/recovery activity, sound-engine reloads, multispeaker start skew and Sound Physics direct-filter application.

## Setup

Start with:

- the current diagnostic-enabled release candidate JAR;
- one ComputerCraft computer;
- 2 attached normal CC:T speakers;
- the supplied 40-second MP3 and WAV test assets;
- Sable/Aeronautics available for the moving-source check;
- Sound Physics Remastered 1.21.1-1.5.1 enabled;
- a solid wall/room for the SPR occlusion comparison;
- 6 extra speakers available so the final scale check can reach at least 8 total;
- one direct public MP3/ICY URL if radio acceptance is being completed;
- a way to keep the source chunk loaded for the optional dimension leave/rejoin check.

Keep `latest.log` if anything fails. The runner itself writes `/v10-acceptance.log`.

## Single master run

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

There are no separate user-facing phases. Smaller scripts are only isolation tools if the master run finds a bug.

The runner first performs deterministic API/admission/control/bounds tests. It then enables built-in diagnostics and runs real-client checks for:

- native CC:T `playAudio` reaching a real client channel;
- MP3 pause/resume;
- WAV renderer continuity;
- finite multispeaker synchronization;
- endpoint-local `audioStopAt` client detach behavior;
- continuous producer-fed RAW delivery;
- loop-boundary renderer restart/synchronization;
- leave-range/rejoin;
- F3+T sound-engine reload recovery.

For physical checks the script tells the operator what to do, then decides PASS/FAIL itself.

Target-scope environment/scale checks are:

1. dimension leave/rejoin while the source stays loaded;
2. Sable translation/rotation source tracking;
3. Sound Physics open-air vs behind-wall processing;
4. grouped MP3/ICY radio;
5. strict radio membership snapshot/rerun;
6. 8+ speaker finite + RAW stress.

Dedicated-server reconnect and VS2 are not missing gates for this chosen scope.

## What the diagnostics prove

A successful client diagnostic is not based only on server state. It requires evidence from the actual Minecraft/OpenAL source. Depending on the test this includes:

- the expected number of client sources actually existed;
- each source reached OpenAL PLAYING;
- group start skew stayed within the runner threshold;
- endpoints continued consuming comparable PCM;
- RAW delivered all admitted PCM through the client stream;
- endpoint-local stop detached only the selected client channel;
- loop/recovery created the expected new renderer epoch without decoder failure;
- F3+T was observed as a real sound-engine reload and playback rejoined;
- Sable movement changed both the HQ requested source position and the live OpenAL source position;
- SPR was detected and attached its direct filter to HQ sources, with the prepared wall producing a measurable occlusion change;
- late radio speakers did not receive a source until the group command was rerun.

The runner logs the measured values rather than relying on a human judgement such as "sounds synchronized".

## Human actions still required

The test process cannot move the player or contraption or press client key combinations. The operator may therefore be asked to:

- walk more than 32 blocks away and return;
- press F3+T and wait for reload;
- change dimension and return;
- move/rotate the Sable contraption;
- move behind the prepared wall;
- connect an extra speaker;
- connect enough speakers to reach 8 total.

Press ENTER after completing the requested action. The operator does **not** choose PASS/FAIL.

## Failure evidence

On failure provide:

- `/v10-acceptance.log`;
- Minecraft `latest.log`;
- exact NeoForge version;
- anything unusual that happened while performing the requested physical action.

The diagnostic log should already contain source counts, channel state, start skew, PCM delivery, renderer/recovery counters and relevant compatibility measurements.

Do not mark runtime acceptance complete from CI alone.
