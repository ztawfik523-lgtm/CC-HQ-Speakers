# CC:HQ Speakers — M1G continuation handoff

> **Historical M1G-start handoff. Superseded for current planning.**
>
> This file preserves the September 13/14 checkpoint and evidence. It contains old open option lists which are now settled. Current work must start from `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, and exact current source.
>
> Current selected M1G scope: explicit decoder/re-anchor revision (current source remains v6); fixed 32-block core radius with volume changing gain rather than range; global-volume-zero local hibernation while canonical server time continues; ordinary non-gapless replay after local EOF. Do **not** ask the owner to choose L1/L2/L3 or dynamic volume-range behavior based on the historical text below.

Original handoff date: 2026-09-13

Post-audit reconciliation at this checkpoint: 2026-09-14

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1g-progressive-finite-decode`

Preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`.

## Exact source checkpoint

Current green integrated M1G source checkpoint established by this handoff: `957832348eaa6e497282d923f2312c9c7d7c550f`.

Source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build/tests/package verification/artifact upload.

Artifacts:

- 21.1.247 artifact `10324148909` — ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482` — ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both targets.

Green CI was not Minecraft runtime proof. Focused audible M1G Minecraft acceptance remained unrecorded.

## Status at this checkpoint

M1E source/test/CI was complete; final focused Minecraft acceptance was skipped/unrecorded.

M1F source/test/CI/package + deterministic/component acceptance was complete at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`; focused real-Minecraft transport acceptance was unrecorded.

M1G modern prepared playback had an integrated progressive decode/render path in source. This remains true: do not describe the modern client as transport-only/silent and do not restore the old whole-file bridge.

## Locked owner decisions from this checkpoint

A1/B1/C1/D1/E1 remain authoritative:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Output is mono signed 16-bit PCM at source sample rate. One physical speaker remains one mono positional source.

## Modern finite pipeline at this checkpoint

```text
server MediaAsset
-> canonical server playback clock/state
-> protocol v6 BEGIN decoder descriptor
-> codec-aware STATE anchor
-> M1F bounded encoded ranges / sliding window
-> starvation-aware FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

Current source includes exact WAV anchors, E1 MP3 pre-roll anchors, progressive common-WAV conversion, progressive JLayer MP3 decoding with pre-target discard, decoder-epoch cancellation, bounded prebuffer/catch-up, positional rendering, and pause/resume/volume channel projection.

## Findings known at this historical checkpoint

### KI-053 — same-anchor STATE/window reset

`HQFiniteMediaClient.state0()` can reset an already-slid encoded window back to an unchanged coarse anchor while preserving the existing decoder epoch. This can put the decoder cursor ahead of the reset window.

A semantic seek remains a separate case and must recreate decoder state even when the selected coarse anchor byte is unchanged.

### KI-054 — shutdown deletion retry

This handoff originally recorded only the completed-file deletion retry gap. Later source recheck strengthened KI-054: a `FiniteRangeReadService.close()` failure can occur before `MediaAssetStore.close()`, leaving the root lock and stopped-server registry entry alive in the JVM. Use `KNOWN-ISSUES.md` for the current form.

### KI-055 — evidence/script mismatch

Real-MP3 progressive JLayer integration across range sliding/starvation is not covered by the current unit suite, and there is no focused `FinitePcmAudioStreamTest`.

Historical scripts such as `m1d_media_analysis_test.lua` are not current M1G prepared-path acceptance. See `TESTING.md`.

## Historical loop option list — superseded

At this checkpoint the owner had not yet selected loop-wrap behavior, so this handoff listed:

- L1 — client EOF refresh;
- L2 — proactive server wrap STATE;
- L3 — client local modulo/restart.

**This choice is now resolved.** Current M1G looping is deliberately ordinary local replay: at physical EOF, while authoritative state still says `looping=true`, start a fresh local decoder/render iteration from the beginning. A normal restart gap is acceptable. No gapless/padding/permanent-source loop machinery is required.

Do not use this historical L1/L2/L3 list as a current owner-choice gate.

## Later findings not present at the original handoff

Current docs additionally track:

- KI-056 expected seek-cancellation race;
- KI-057 STATE snapshot versus decoder-reanchor intent;
- selected explicit decoder/re-anchor revision;
- KI-058 selected fixed attenuation contract;
- KI-060 renderer-start / volume-zero behavior;
- KI-061 staging leftovers;
- KI-062 blocking DNS under shared composite monitor;
- KI-063 replacement-before-admission;
- KI-064 import zero-read/atomic-move hardening.

The September 16 audit also contained retracted claims; current docs preserve the corrected forms. Do not infer richer anchor fields or live STATE coordinates from old audit material.

## Current pointer

For current remaining work, sequencing, M1H/VS2 clarification, and evidence boundaries, read:

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `NEXT-CHAT-HANDOFF.md`
7. exact current source/CI

This historical handoff does not override those files.
