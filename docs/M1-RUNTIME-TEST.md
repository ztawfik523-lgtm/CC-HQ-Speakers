# M1 consolidated runtime test

Updated: 2026-09-19

This is a current integration guide. For exact deterministic prerequisites and evidence gaps, read `TESTING.md` first. Current source/state authority is `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `VERIFIED-FACTS.md`, and exact source.

## Important script boundary

Do **not** treat every historical script in `scripts/` as a current modern-prepared M1G test.

Useful compatibility/history scripts:

- `p0_cc_speaker_contract.lua` — standard CC:T compatibility;
- `m1e_server_authority_test.lua` — server-authority semantics;
- `m0-smoke.lua` — broad inherited/legacy smoke coverage.

Not valid as current modern-prepared M1G acceptance:

- `m1d_media_analysis_test.lua` — expects the old broad M1D prepared format surface;
- `m1_player_test.lua` and `p0_finite_regression.lua` — primarily exercise inherited byte-taking finite APIs rather than `hq.playFile()` / prepared playback.

## Preconditions

The M1G source prerequisites are already satisfied at final source checkpoint `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`:

1. protocol v7 explicit decoder/re-anchor revision is implemented;
2. KI-053/KI-056/KI-057 are closed at source/component level;
3. fixed-range renderer/start behavior KI-058/KI-060 is implemented;
4. ordinary replay KI-051 is implemented;
5. real-MP3 progressive JLayer coverage and renderer-read policy tests exist;
6. KI-061 whole-owner staging cleanup is implemented;
7. both supported NeoForge builds are green at CI `35297026277`.

A focused **M1G audible/core runtime PASS was recorded on 2026-09-19** on NeoForge 21.1.247 integrated singleplayer, resolving KI-046. This guide remains the regression/release rerun checklist rather than a source-completion checklist.

Strongly consider closing KI-062 before relying on legacy stream calls in the same test instance, because blocking DNS under the shared composite monitor can stall server tick/lifecycle cleanup.

KI-054/KI-064 storage hardening and KI-063 replacement-admission correctness remain tracked post-M1G. They should be tested when their respective fixes land, but they do not retroactively make the M1G core finite engine incomplete.

## Recorded M1G runtime result — 2026-09-19

The focused runtime kit recorded PASS for modern WAV/MP3 playback, pause/resume, seek/reanchor, MP3 seek, prepared-asset lifetime, float32 WAV, natural EOF, ordinary loop replay, positional attenuation/fixed-range behavior, stop, and global-volume-zero hibernation/unmute.

The dedicated mute retest used the 2-second looping WAV so EOF could not invalidate the result. While HQ volume was 0, authoritative status remained `playing` and `looping=true` for more than five seconds while the user confirmed complete silence. Restoring volume to 0.65 returned audible playback while the same generation remained active, then STOP returned the session to idle.

Environment: Minecraft 1.21.1, Java 21, CC:T 1.120.0, NeoForge 21.1.247, final M1G jar/source family. NeoForge 21.1.248 was not manually runtime-tested in this session.

This resolves KI-046 only. KI-062/063/054/064 and M1H lifecycle/recovery remain separate.

## Test environment

Run Minecraft 1.21.1 with Java 21, CC:T 1.120.0, NeoForge 21.1.247 baseline and repeat critical coverage on 21.1.248.

Record exact source commit, documentation commit if relevant, JAR SHA-256, test-instance/full-modpack context, and fixture facts.

Generate deterministic MP3/WAV fixtures with `scripts/generate_m1_test_audio.ps1`. Generated OGG remains inherited/legacy coverage, not modern prepared M1G support.

## Gate 1 — standard CC:T contract

Run `p0_cc_speaker_contract` and listen/verify:

- `playNote` works;
- `playSound` honors the requested sound;
- `playAudio` backpressure/native `speaker_audio_empty` works;
- `stop` works;
- HQ additions have not replaced native semantics.

Do not use legacy `playNoteAll`/`playSoundAll` as proof of singular CC:T semantics; those inherited helpers have known mismatches.

## Gate 2 — modern prepared MP3

Use the bundled module, not `speakMp3`:

```lua
local speaker = assert(peripheral.find("speaker"))
local hq = require("hqspeaker")
assert(hq.playFile(speaker, "/m1.mp3", { volume = 0.5 }))
```

Verify:

- sound becomes audible before the whole track would have transferred;
- `audioStatus()` reports server-derived duration/rate/format;
- playback remains positional and follows BLOCKS/MASTER volume;
- no complete client `.part/.media` song file appears;
- long playback does not make encoded/decoded memory scale with track duration.

## Gate 3 — modern prepared common WAV

Repeat through `hq.playFile()` with supported common WAV.

Verify correct pitch/speed at source sample rate and mono positional output.

## Gate 4 — controls and explicit reanchor revision

For both MP3 and WAV where practical:

- pause freezes canonical position and audible playback without restarting a healthy decoder;
- resume continues without gratuitous restart;
- live volume update works without restarting decoder state;
- forward/backward/repeated seek audibly rejoins current server time;
- semantic seek recreates codec state even when the selected coarse MP3 anchor byte is unchanged;
- expected cancellation from old decoder work never becomes a fatal session error;
- stale/out-of-order decoder revision work cannot revive old PCM/renderers;
- seek correctness does not depend on a CONTROL packet arriving before STATE;
- protocol v7 uses STATE as the sole nonterminal transition authority, so there is no obsolete SEEK/Pause/Resume/Volume/Loop CONTROL-order dependency to preserve.

## Gate 5 — starvation/refill

Create a controlled slow/throttled transport condition or otherwise force bounded temporary starvation.

Verify the behavior M1G owns:

- temporary missing encoded data does not become terminal EOF;
- renderer starvation is temporary silence rather than immediate terminal sound EOF;
- when data returns, the live epoch continues without stale output from cancelled/replaced work;
- server canonical time continues independently.

Do **not** require general current-server-time catch-up after a long already-started renderer underrun. Robust long-underrun rejoin remains M1H unless later source changes explicitly pull it forward.

## Gate 6 — selected ordinary replay loop

Looping is no longer an architecture-choice gate.

Verify:

- physical EOF while authoritative `looping=true` starts the same media again;
- WAV and MP3 both replay;
- a normal restart gap is acceptable;
- loop disable prevents the next replay without stale restart work;
- seek/replacement/stop/revision changes supersede stale local replay;
- server `audioStatus().position` wrapping aligns semantically with repeated playback.

Do **not** require sample-gapless MP3, encoder-delay/padding trimming, loop-head prefetch, or a permanent OpenAL source.

## Gate 7 — fixed range / volume / volume zero

The M1G range policy is selected: fixed 32-block core radius.

Verify:

- moving away from the physical `computercraft:speaker` attenuates sound;
- changing finite volume changes loudness;
- volume above 1 does **not** enlarge the modern finite core attenuation/delivery radius;
- behavior around the 32-block boundary matches the intended fixed range;
- global HQ volume 0 stops local decoder/render/range work while canonical server time keeps advancing;
- unmute rebuilds/rejoins current authoritative time rather than replaying stale buffered audio;
- client-local BLOCKS/MASTER mute does not alter server transport policy;
- local silent/muted-start conditions do not permanently latch `rendererStarted` with no active source.

Future SPR extended-range behavior is **not** part of this M1G gate.

## Gate 8 — stop/replacement/lifecycle

Verify:

- stop while prebuffering/decoding cancels old work promptly;
- successful replacement cancels old decoder/PCM/render state exactly once;
- **rejected/failed replacement leaves the previous valid HQ source alive** after KI-063 is fixed;
- renderer close cannot leave a producer blocked forever;
- normal client disconnect/world teardown does not leave stale local sound.

Full late-entry/leave-return/dimension/resource-reload/final VS2 lifecycle remains M1H.

## Gate 9 — positional / VS2 observation

With one physical `computercraft:speaker`:

- verify direction/attenuation while moving around it;
- adjust BLOCKS and MASTER sliders;
- if VS2 is available, record current moving-source behavior but do not treat final VS2 movement as M1G completion.

Modern STATE does not carry live x/y/z. M1H may later mirror the legacy client-side ship transform from BEGIN block coordinates or add explicit authoritative position updates. Do not treat either future design as an M1G gate.

## Gate 10 — staging/storage observation

For normal operation:

- confirm successful high-level `hq.playFile()` consumes its temporary staging copy;
- create an interrupted/low-level leftover and verify the implemented whole staging-owner cleanup removes it while ordinary one-computer detach does not erase a still-shared mount;
- confirm no obvious active range/media work remains on normal shutdown.

KI-054 and KI-064 need deterministic failure-injection proof in code: normal shutdown cannot prove root-lock recovery, zero-read no-progress handling, or atomic-move fallback.

## Optional safety regression — KI-062

When a deterministic resolver hook/test exists, block DNS for a legacy stream start and verify the server tick and provider cleanup paths do not wait on the composite monitor held by that lookup.

Do not use an uncontrolled real DNS outage as the only acceptance method.

## Record

Record:

- exact commit/JAR SHA-256;
- NeoForge/CC:T/Minecraft/Java versions;
- fixture names, sizes, rates, durations;
- pass/fail per gate;
- relevant client/server logs;
- test instance or full modpack;
- network compression/throttling conditions when relevant.

Do not call M1G audibly proven from CI alone or from legacy `speakMp3`/`speakOgg` scripts.
