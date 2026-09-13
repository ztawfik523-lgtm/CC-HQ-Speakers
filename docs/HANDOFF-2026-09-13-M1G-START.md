# CC:HQ Speakers — M1G continuation handoff

Original handoff date: 2026-09-13

Post-audit reconciliation: 2026-09-14

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1g-progressive-finite-decode`

Preparation base: `aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`.

## Exact source checkpoint

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

Source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build/tests/package verification/artifact upload.

Artifacts:

- 21.1.247 artifact `10324148909` — ZIP SHA-256 `7555b34fe1c44e87b35161fa12ea67a7f38c6e409a879a8725863b78757d27db`;
- 21.1.248 artifact `10324273482` — ZIP SHA-256 `f308b9a52a5688e011e1e9d10b2da06d01cc5f5957b9368ed355c4fc302e12c1`.

Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` passed CI `34780519972` on both targets.

A later full repository/source/docs audit on 2026-09-14 changed documentation only and recorded KI-053 through KI-055. No implementation fix was made.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance is unrecorded.

## Status

M1E source/test/CI is complete; final focused Minecraft acceptance was skipped/unrecorded.

M1F source/test/CI/package + deterministic/component acceptance is complete at `d0acd41df690d02c9813ecd7e84d3115b44f6a3f` / CI `34763362365`; focused real-Minecraft transport acceptance is unrecorded.

M1G modern prepared playback has an integrated progressive decode/render path in source. Do not describe the modern client as transport-only/silent and do not restore the old whole-file bridge.

## Locked owner decisions

A1/B1/C1/D1/E1 remain authoritative:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Output is mono signed 16-bit PCM at source sample rate. One physical speaker remains one mono positional source.

## Current modern finite pipeline

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

## Post-audit open findings — do not silently fix around them

### KI-053 — same-anchor STATE/window reset

`HQFiniteMediaClient.state0()` can reset an already-slid encoded window back to an unchanged coarse anchor while preserving the existing decoder epoch. This can put the decoder cursor ahead of the reset window.

A semantic seek remains a separate case and must recreate decoder state even when the selected coarse anchor byte is unchanged.

### KI-054 — shutdown deletion retry

`MediaAssetStore.close()` clears completed-entry bookkeeping before completed-file deletion attempts. If a deletion fails, a later `close()` cannot retry that completed entry; next-start orphan pruning normally recovers it.

### KI-055 — evidence/script mismatch

Real-MP3 progressive JLayer integration across range sliding/starvation is not covered by the current unit suite, and there is no focused `FinitePcmAudioStreamTest`.

Historical scripts such as `m1d_media_analysis_test.lua` are not current M1G prepared-path acceptance. See `TESTING.md`.

## Remaining owner architecture question

Before implementing loop-wrap behavior, ask the owner to choose:

### L1 — client EOF refresh

At local physical EOF while server looping remains true, request fresh authoritative STATE and restart from the server-selected anchor.

Pros: simplest and strongest server-authority fit. Cons: possible roundtrip/prebuffer boundary gap.

### L2 — server wrap STATE

Server detects canonical loop-wrap crossings and proactively projects fresh STATE.

Pros: explicit server authority and potentially tighter boundary. Cons: added server wrap/fanout state and edge cases.

### L3 — client local modulo/restart

Client predicts wraps from duration + last authoritative snapshot and restarts locally, reconciling later.

Pros: lowest boundary latency. Cons: client drift/reconciliation becomes correctness state and weakens authority purity.

Do not implement L1/L2/L3 until the owner chooses.

## Remaining M1G work after owner choice

1. implement/test the chosen loop-wrap policy;
2. resolve KI-053 without breaking same-anchor semantic seek restart;
3. add real-MP3 progressive integration and focused renderer-adapter tests;
4. re-audit timing/cancellation;
5. run focused Minecraft audible acceptance for modern MP3/common WAV, seek/pause/resume/loop/stop, starvation/refill, bounded memory, positional attenuation, and standard CC:T compatibility.

KI-054 can be hardened in the appropriate storage/shutdown hardening pass; it must remain tracked until resolved/tested.

Do not pull full late-entry/leave-return/dimension/reload/general-underrun/final-VS2 lifecycle into M1G; that remains M1H.

## Evidence boundary

```text
M1E source/test/CI: complete
M1E final focused Minecraft: skipped / unrecorded
M1F source/test/CI/package/component: complete
M1F focused Minecraft transport: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G audit findings KI-053..055: documented, not fixed
M1G loop-wrap architecture: owner choice required
M1G audible Minecraft acceptance: unrecorded
```

## Read order

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. this handoff
6. `M1G-DESIGN-DECISIONS-2026-09-13.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. exact current source and CI
