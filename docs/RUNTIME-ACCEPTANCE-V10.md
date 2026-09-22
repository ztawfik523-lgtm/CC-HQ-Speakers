# Runtime acceptance — frozen protocol v10

Prepared: 2026-09-21

Run this only against the frozen v10 surface in `API-FREEZE-V10.md`.

## Goal

Prove the parts CI cannot: real Minecraft/SoundManager/OpenAL behavior, spatial synchronization, moving-source projection, recovery, backpressure timing, Sound Physics integration and realistic performance.

Do not redesign the API during this pass. A failure should first be treated as a bug against the frozen contract.

## Test build

Use the candidate JAR produced from the current branch after the freeze/runtime-prep commits. Client and server must use the same JAR/protocol.

Target matrix:

| Target | Full pass |
| --- | --- |
| NeoForge 21.1.247 | required |
| NeoForge 21.1.248 | required |

Run the full deep pass on .247 first. On .248, rerun the automated scripts plus focused finite/radio/movement/SPR smoke unless .247 exposes a version-specific concern requiring broader repetition.

## Test assets/setup

Prepare:

- one valid MP3 at least 30 seconds long;
- one supported common WAV;
- one public direct MP3/ICY radio URL;
- 1, then 2, 4 and ideally 8 speakers attached to the same ComputerCraft computer/peripheral network;
- Sable/Aeronautics moving-sublevel setup;
- VS2 moving-ship setup;
- a client profile with Sound Physics Remastered for the SPR phase.

Keep `latest.log` for failures. For performance problems, capture a Spark profile rather than guessing.

## Phase 0 — native and frozen-surface preflight

Preferred complete run, with at least 2 attached speakers:

```
v10_phase0_acceptance <mp3> <wav>
```

The combined runner uses explicit timer-based waits, writes `/v10-phase0.log`, and displays a live dashboard on the first attached monitor when available. It requires both MP3 and WAV assets and refuses to declare complete Phase 0 with fewer than 2 speakers.

If it fails, isolate the subsystem with:

```
p0_cc_speaker_contract
p0_finite_regression <mp3>
v10_core_acceptance <mp3> <wav>
v10_raw_acceptance
```

Pass criteria:

- every script prints `[PASS]`;
- native `playNote/playSound/playAudio` are audibly correct;
- no idle `speaker_audio_empty` spam;
- no retired API reappears;
- finite MP3/WAV controls work;
- RAW rejection/retry event works;
- `audioStopAt` leaves finite survivors running.

If this phase fails, stop and fix before broader testing.

## Phase 1 — finite multispeaker and command stress

Run `v10_multispeaker_stress <mp3> 8` with 2 speakers, then 4, then 8+ where practical.

Listen while it runs.

Pass criteria:

- starts are spatially synchronized;
- no obvious phasing/drift caused by timeline mismatch;
- shared pause/resume/seek/loop acts as one playback;
- endpoint volume/mute affects only intended speakers;
- `audioStopAt(2)` silences only endpoint 2;
- repeated controls/replacements do not deadlock, crash or leave ghost playback;
- final stop always reaches idle.

For extra concurrency proof, run the stress script from one computer while a second computer attached to overlapping speakers issues ordinary `audioStop`, `speakMp3`, or native speaker commands. The final command should win without a hang or partial corrupt state.

## Phase 2 — finite listener lifecycle and recovery

Start a long finite MP3. In another terminal run:

```
v10_runtime_observer 180
```

The observer writes `/v10-runtime-observer.log` and uses the first attached monitor as a live status/event dashboard when available.

Perform:

1. begin outside 32 blocks, then enter;
2. stay in range;
3. leave beyond 32 blocks;
4. re-enter;
5. change dimension/disconnect/reconnect where practical;
6. trigger client resource reload;
7. reproduce renderer loss/starvation conditions if available.

Pass criteria:

- entry joins current playback rather than restarting from zero;
- staying in range does not restart repeatedly;
- leaving cleans local playback;
- re-entry resumes at current canonical time;
- reload/recovery rebuilds rather than permanently dying;
- terminal/stop/replacement cleanup is clean.

## Phase 3 — moving speakers

Test each of finite, RAW and MP3 radio on:

1. static speaker baseline;
2. Sable/Aeronautics moving sublevel;
3. VS2 moving ship.

Use `v10_runtime_observer` while moving.

Pass criteria:

- audio follows the moving physical speaker;
- attenuation/panning follow world position;
- no teleport to plot-space/static block coordinates;
- no continuous server position-packet requirement;
- stop/replacement still targets the right source while moving.

The source resolver is Sable -> VS2 -> static for every HQ positional path; runtime should match that contract.

## Phase 4 — RAW

Run `v10_raw_acceptance` first, then listen to a longer producer loop if desired.

Pass criteria:

- signed-16 PCM is audibly correct;
- repeated chunks are continuous enough for the chosen producer cadence;
- capacity rejection returns false;
- only a producer which observed rejection receives `hqspeaker_audio_empty`;
- retry succeeds after the event;
- All starts together without waiting for invisible/global members;
- At targets the selected endpoint;
- drain/grace releases HQ ownership.

## Phase 5 — MP3/ICY radio

Run:

```
v10_radio_acceptance <radio-url>
```

Then manually repeat with 4/8 speakers where practical.

Strict-membership test:

1. start `speakStreamAll` with the initial group;
2. add/attach a new speaker or move into a client-local situation that did not receive that start;
3. verify it does not join automatically;
4. rerun `speakStreamAll`;
5. verify the fresh snapshot now includes it.

Pass criteria:

- direct/At/All are audible;
- grouped members release together after prebuffer;
- no growing drift during a multi-minute listen;
- late/new members remain out until rerun;
- `audioStopAt` removes only one radio endpoint;
- `audioStopAll` stops the group;
- ICY metadata/event updates when the station provides metadata;
- bad URL, EOF and network loss do not crash/hang the client;
- stopping a blocked/slow startup is responsive.

Remember: `isStreaming()` is server-side request/ownership state. Do not fail a test merely because it stays true after a client-local remote failure until server stop/replacement.

## Phase 6 — malformed media and bounds

Exercise:

- wrong bytes passed to `speakMp3` / `speakWav`;
- truncated MP3/WAV;
- extreme but valid duration/file size within policy;
- repeated prepare/play/release;
- configured per-asset and total-store limits;
- repeated range/seek activity;
- server stop/restart after active media.

Pass criteria:

- invalid media rejects cleanly;
- no partial destructive replacement on failed admission;
- memory/disk/network workers remain bounded;
- shutdown does not strand the media root/store.

## Phase 7 — Sound Physics Remastered

Enable SPR and repeat:

- native sound;
- one finite source;
- 4/8 finite sources;
- RAW;
- grouped radio;
- moving source if compatible with the setup.

Pass criteria:

- HQ sounds are processed through the normal Minecraft sound pipeline;
- spatial/acoustic effect is present as expected;
- no duplicate/reprocessed sound;
- performance is acceptable.

If performance is questionable, capture Spark + client observations before changing architecture.

## Phase 8 — NeoForge 21.1.248 confirmation

Repeat Phase 0, finite multispeaker smoke, radio smoke, movement smoke and SPR smoke on 21.1.248.

## Evidence to record

Use `RUNTIME-RESULTS-V10.md`. For every failure include:

- exact NeoForge version;
- script/phase;
- speaker count;
- source type (finite/RAW/radio/native);
- whether Sable/VS2/SPR was enabled;
- observed vs expected behavior;
- relevant `latest.log` excerpt;
- Spark profile if performance-related.

Do not mark runtime acceptance complete from CI alone.
