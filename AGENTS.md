# CC:HQ Speakers — agent guide

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed both targets in CI `34780519972`. A 2026-09-14 full repository/source/docs audit then reconciled current documentation and recorded KI-053 through KI-055 without changing implementation.

Green CI is not Minecraft runtime proof.

## Read before changing source

1. `docs/CURRENT-STATE.md`
2. `docs/KNOWN-ISSUES.md`
3. `docs/TESTING.md`
4. `docs/VERIFIED-FACTS.md`
5. `docs/HANDOFF-2026-09-13-M1G-START.md`
6. `docs/M1G-DESIGN-DECISIONS-2026-09-13.md`
7. exact current source/CI

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

### KI-051 — loop-wrap owner choice

Before implementing loop-wrap behavior, ask the owner to choose:

- L1 client EOF refresh — strongest server-authority fit, possible roundtrip/prebuffer gap;
- L2 proactive server wrap STATE — potentially tighter, more server wrap/fanout state;
- L3 client local modulo/restart — lowest latency, adds client drift/reconciliation state.

Do not silently choose one.

### KI-053 — same-anchor STATE/window reset

Current `HQFiniteMediaClient.state0()` can reset a slid encoded window back to an unchanged coarse anchor while preserving the existing decoder epoch. A fix must prevent ordinary STATE from invalidating the live decoder cursor while preserving the requirement that semantic seek recreates codec state even when the selected anchor byte is unchanged.

### KI-055 — missing focused evidence

Add a real-MP3 progressive JLayer integration test across range sliding/starvation and focused `FinitePcmAudioStream` tests before treating deterministic M1G coverage as complete.

Historical scripts such as `m1d_media_analysis_test.lua` are not current modern-prepared M1G gates.

### KI-054 — shutdown cleanup

`MediaAssetStore.close()` does not retain failed completed-file shutdown deletions for another close retry. Track for storage/shutdown hardening; next-start orphan pruning normally recovers the managed file.

## M1G correctness rules

- server M1E state remains canonical;
- temporary encoded starvation is not decoder EOF;
- decoder/network/disk waits stay off Minecraft/audio threads;
- encoded and decoded memory stay bounded independently of duration;
- semantic seek creates fresh codec state even if the coarse anchor byte is unchanged;
- seek/replacement/stop discard stale encoded wait/decoder/PCM/renderer state;
- one physical speaker remains one mono positional source.

## Evidence boundary

M1E final focused Minecraft acceptance was skipped/unrecorded. M1F focused Minecraft transport acceptance is unrecorded. M1G integrated source/tests/package is green at the source checkpoint above, but real-MP3 progressive integration coverage, focused renderer-adapter coverage, loop-wrap behavior, and focused audible Minecraft acceptance remain incomplete.

Full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/final-VS2 lifecycle remains M1H.
