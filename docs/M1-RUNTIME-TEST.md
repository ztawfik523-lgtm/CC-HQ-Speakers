# M1 consolidated runtime test

This file is now a current integration guide, not the old `fba84a3`/pre-M1A gate.

For exact current evidence gaps and deterministic prerequisites, read `TESTING.md` first.

## Important script boundary

Do **not** treat every historical script in `scripts/` as a current modern-prepared M1G test.

Useful compatibility/history scripts:

- `p0_cc_speaker_contract.lua` — standard CC:T compatibility;
- `m1e_server_authority_test.lua` — server-authority semantics;
- `m0-smoke.lua` — broad inherited/legacy smoke coverage.

Not valid as current modern-prepared M1G acceptance:

- `m1d_media_analysis_test.lua` — expects the old broad M1D prepared format surface;
- `m1_player_test.lua` and `p0_finite_regression.lua` — primarily exercise inherited byte-taking finite APIs such as `speakMp3`/`speakOgg` rather than `hq.playFile()` / prepared playback.

## Preconditions

Before the consolidated Minecraft run:

1. KI-051 loop-wrap architecture must be chosen and implemented if loop behavior is part of the pass;
2. KI-053 same-anchor STATE/window-reset issue must be resolved and regression-tested;
3. real-MP3 progressive JLayer integration coverage and focused `FinitePcmAudioStream` coverage should be added;
4. both target NeoForge builds should be green.

KI-054 shutdown deletion retry is a separate storage/shutdown hardening item; keep it tracked even if the audible M1G run proceeds.

## Test environment

Run Minecraft 1.21.1 with Java 21, CC:T 1.120.0, NeoForge 21.1.247 baseline and ideally repeat critical coverage on 21.1.248.

Record exact source commit, documentation commit if relevant, JAR SHA-256, test-instance/full-modpack context, and fixture facts.

Generate deterministic MP3/WAV fixtures with `scripts/generate_m1_test_audio.ps1`, but note that the generated OGG fixture is for inherited/legacy coverage, not modern prepared M1G support.

## Gate 1 — standard CC:T contract

Run `p0_cc_speaker_contract` and listen/verify:

- `playNote` works;
- `playSound` honors the requested sound;
- `playAudio` backpressure/native `speaker_audio_empty` works;
- `stop` works;
- HQ additions have not replaced native semantics.

## Gate 2 — modern prepared MP3

Use the bundled module, not `speakMp3`:

```lua
local speaker = assert(peripheral.find("speaker"))
local hq = require("hqspeaker")
assert(hq.playFile(speaker, "/m1.mp3", { volume = 0.5 }))
```

Verify:

- sound becomes audible before the whole track would have transferred;
- `audioStatus()` reports the server-derived duration/rate/format;
- playback remains positional and follows BLOCKS/MASTER volume;
- no complete client `.part/.media` song file appears;
- long playback does not make encoded/decoded memory scale with track duration.

## Gate 3 — modern prepared common WAV

Repeat through `hq.playFile()` with supported common WAV.

Verify correct pitch/speed at the fixture's source sample rate and mono positional output.

## Gate 4 — controls and seek

For both MP3 and WAV where practical:

- pause freezes canonical position and audible playback;
- resume continues;
- live volume update works once;
- forward seek rejoins the current server time;
- backward seek recreates decoder state cleanly;
- repeated seeks do not revive stale PCM/old sound;
- MP3 seek does not corrupt after E1 pre-roll.

If a seek selects the same coarse MP3 anchor as before, verify the semantic seek still creates fresh codec state.

## Gate 5 — starvation/refill

Create a controlled slow/throttled transport condition or otherwise force bounded temporary starvation.

Verify the behavior M1G actually owns:

- temporary missing encoded data does not become terminal EOF;
- renderer starvation is temporary silence rather than immediate terminal sound EOF;
- when data returns, the existing live decoder/renderer epoch can continue without stale output from a cancelled/replaced epoch;
- server canonical time continues independently.

Do **not** require or claim a general current-server-time catch-up after a long already-started renderer underrun. Current M1G only performs catch-up before renderer start; robust long-underrun rejoin/catch-up remains M1H. Record any audible lag observed after a long starvation rather than treating it as an M1G pass/fail requirement.

## Gate 6 — loop

Only run this as a pass/fail gate after KI-051 L1/L2/L3 is chosen and implemented.

Verify multiple audible wraps, server/client synchronization, loop disable continuity, and exact-duration semantics.

Server `audioStatus().position` wrapping by itself is not proof of audible loop rejoin.

## Gate 7 — stop/replacement/lifecycle

Verify:

- stop while prebuffering/decoding cancels old work promptly;
- replacement does not allow stale decoder/PCM to revive;
- renderer close cannot leave a producer blocked forever;
- normal client disconnect/world teardown does not leave stale local sound.

Full late-entry/leave-return/dimension/resource-reload/final VS2 lifecycle remains M1H and is not required to close M1G.

## Gate 8 — positional audio

With one physical `computercraft:speaker`:

- move around it and verify direction/attenuation;
- adjust BLOCKS and MASTER sliders;
- if VS2 is available, record movement behavior but do not treat final VS2 lifecycle as M1G completion.

## Gate 9 — storage/shutdown observation

During normal stop/server shutdown, verify no obvious retained active media/range work remains. KI-054 specifically requires deterministic deletion-failure testing in code, including the failed-close registry-retention path; a normal runtime shutdown cannot prove that edge case resolved.

## Record

Record:

- exact commit/JAR SHA-256;
- NeoForge/CC:T/Minecraft/Java versions;
- fixture names, sizes, rates, durations;
- pass/fail per gate;
- relevant client/server logs;
- whether test instance or full ATM10/modpack;
- network compression/throttling conditions when relevant.

Do not call M1G audibly proven from CI alone or from legacy `speakMp3`/`speakOgg` scripts.
