# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed both targets in CI `34780519972`. Later repository/source/docs audits and owner scope decisions changed documentation only; implementation remains at the source checkpoint above.

Green CI is not Minecraft runtime proof.

## Read before changing source

1. `docs/CURRENT-STATE.md`
2. `docs/KNOWN-ISSUES.md`
3. `docs/TESTING.md`
4. `docs/VERIFIED-FACTS.md`
5. `docs/M1G-SCOPE-DECISIONS-2026-09-14.md`
6. `docs/HANDOFF-2026-09-13-M1G-START.md`
7. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
8. exact current source/CI

Historical milestone/preparation/handoff documents do not override current records.

## Locked product/architecture rules

This is a programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy. Do not create permanent Java music/effect/notification lanes or infer role from MP3/WAV/etc.

One physical speaker remains one mono positional source.

Preserve standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`; HQ RAW uses separate `hqspeaker_audio_empty`.

Locked M1G choices:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Owner-selected M1G scope additionally requires:

- explicit server-authoritative decoder/re-anchor revision semantics; do not infer restart intent from anchor movement;
- one fixed 32-block core listening/delivery radius for M1G; HQ volume changes gain/loudness, not the core radius;
- global HQ volume zero keeps canonical server time running but hibernates local decode/render/range requests until unmuted;
- looping is ordinary local replay after physical EOF while authoritative state still says looping; a normal restart gap is acceptable;
- no gapless MP3 work, permanent-source loop engineering, dynamic volume-aware radius, general M1H listener lifecycle, or SPR range/acoustic integration in M1G.

## Current modern prepared path

```text
server MediaAsset
-> server-authoritative playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded range transport / sliding encoded window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder / ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

Modern prepared playback is MP3 + supported common WAV. It does not use the inherited complete-file JavaSound/mp3spi bridge, complete client song files, or modern CHUNK/END whole-file transfer.

Server owns finite generation/state/time/control/EOF. Client decoder/render failures are local diagnostics.

## Current open M1G issues

### KI-051 — selected ordinary replay, not implemented

At local physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration. A normal restart gap is acceptable. Do not reopen L1/L2/L3 or gapless/continuous-source loop design unless the owner explicitly changes scope.

### KI-053 / KI-056 / KI-057 — decoder revision/re-anchor cluster

Solve these together using the selected explicit server-authoritative decoder/re-anchor revision. Ordinary STATE must preserve a healthy decoder; semantic seek must be self-describing even with the same coarse anchor; old worker identity must be invalidated before cancellation can wake it.

Before coding protocol v7, compare whether PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets are still worth retaining as hints. STATE may be simpler as the sole correctness authority because v6 is unreleased/internal.

### KI-058 / KI-059 / KI-060 — fixed-range renderer contract

M1G core radius is fixed at the existing 32 blocks unless the owner explicitly changes the number. Volume affects gain, not that radius. The live Minecraft channel must enforce the fixed attenuation distance rather than native CC:T's volume-scaled distance. Global volume zero hibernates transport/rendering while server time continues; client-local MASTER/BLOCKS mute is different and may need `canStartSilent()`. Harden the one-way renderer-start latch.

### KI-055 — missing focused evidence

Add a real-MP3 progressive JLayer integration test across range sliding/starvation and focused `FinitePcmAudioStream` tests before treating deterministic M1G coverage as complete.

Historical scripts such as `m1d_media_analysis_test.lua` are not current modern-prepared M1G gates.

### KI-061 — staging cleanup

Persistent per-speaker staging mounts can leave unreachable files. Whole-staging cleanup should remove leftovers after attachments are unmounted; ordinary one-computer detach must not clear the shared mount.

### KI-054 — shutdown cleanup / lock lifetime

`MediaAssetStore.close()` loses retry bookkeeping for completed-file deletion failures. A full-repository audit also found that if `FiniteRangeReadService.close()` throws before `store.close()` is reached, the store root lock can remain held and the stopped-server registry entry can remain reachable. Treat shutdown lock release as correctness hardening, not just leftover-file cleanup.

## Additional confirmed cross-cutting findings

The 2026-09-16 full-repository review surfaced issues outside the narrow M1G decoder cluster. Do not silently fold all of them into M1G, but do not lose them:

- the synchronized dynamic peripheral path can hold the composite monitor across blocking stream URL DNS resolution while the server tick acquires that same monitor;
- `startRaw` and `audioPlayPrepared` transfer/stop the current HQ owner before all rejection/failure conditions are known, so a failed replacement can destroy valid playback;
- the modern `FiniteSpeakerSound` has an `updatePosition` method but the M1G client does not call it, so moving VS2 speakers require later lifecycle/position work;
- inherited live HLS, legacy advertised format lists, legacy `*All`/`*At` note/sound semantics, and the separate registered `hqspeaker:hq_speaker` block surface contain later cleanup/product-surface issues.

The audit itself is not automatically authoritative: verify exact source before promoting any further claim. In particular, current `FiniteDecodeAnchorSelector.Anchor` contains only encoded offset + seconds, and modern STATE does not carry world position updates.

## M1G correctness rules

- server M1E state remains canonical;
- temporary encoded starvation is not decoder EOF;
- decoder/network/disk waits stay off Minecraft/audio threads;
- encoded and decoded memory stay bounded independently of duration;
- semantic seek creates fresh codec state even if the coarse anchor byte is unchanged;
- seek/replacement/stop discard stale encoded wait/decoder/PCM/renderer state;
- one physical speaker remains one mono positional source.

## Evidence boundary

M1E final focused Minecraft acceptance was skipped/unrecorded. M1F focused Minecraft transport acceptance is unrecorded. M1G integrated source/tests/package is green at the source checkpoint above, but real-MP3 progressive integration coverage, focused renderer-adapter coverage, selected ordinary loop replay, and focused audible Minecraft acceptance remain incomplete.

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H. Future Sound Physics Remastered compatibility owns deliberate extended range/acoustic behavior and matching transport relevance.
