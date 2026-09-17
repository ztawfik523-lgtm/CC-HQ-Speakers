# CC:HQ Speakers — agent guide

Updated: 2026-09-17

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation/audit commits after that checkpoint do not change the source baseline. Green CI is not Minecraft runtime proof.

## Read before changing source

1. `docs/CURRENT-STATE.md`
2. `docs/M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/FUTURE-CLEANUP.md`
7. `docs/ARCHITECTURE.md`
8. `docs/ROADMAP.md`
9. exact current source/CI

Historical milestone/preparation/handoff documents preserve old checkpoints. They do not override current records.

## Product / architecture rules

This is a programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy. Do not create permanent Java music/effect/notification lanes or infer role from MP3/WAV/etc.

One physical speaker remains one mono positional source.

Preserve standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`; HQ RAW uses separate `hqspeaker_audio_empty`.

Locked M1G media/renderer choices:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Owner-selected later M1G scope:

- explicit server-authoritative decoder/re-anchor revision; do not infer restart intent from anchor movement;
- fixed 32-block core modern-finite listening/delivery radius; HQ volume changes gain/loudness, not core range;
- global HQ volume zero keeps canonical server time running but hibernates local decode/render/range work until unmuted;
- looping is ordinary local replay after physical EOF while authoritative state still says looping; a normal restart gap is acceptable;
- no gapless MP3 work, permanent-source loop engineering, dynamic volume-aware radius, general M1H listener lifecycle, or SPR range/acoustic integration in M1G.

Do not reopen L1/L2/L3, L4a/L4b, or dynamic-range alternatives unless the owner explicitly changes scope.

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

Current source is protocol **v6**. The selected explicit decoder/re-anchor revision is next-protocol work, likely v7.

## Current M1G source work

### KI-053 / KI-056 / KI-057 — decoder revision/re-anchor cluster

Solve these together using the selected explicit server-authoritative decoder/re-anchor revision.

Required properties:

- ordinary STATE preserves a healthy decoder/window;
- semantic seek is self-describing even with the same coarse anchor;
- stale worker identity is invalidated before cancellation can wake/report;
- correctness does not depend on CONTROL SEEK arriving before STATE.

One implementation-shape choice remains: keep finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional latency hints, or remove that duplicate path and use STATE as sole transition authority. Do not preserve duplicate v6 behavior merely because it already exists.

### KI-058 / KI-060 — fixed-range renderer behavior

M1G core radius is fixed at 32 blocks unless the owner explicitly changes the number. Volume affects gain, not that radius.

The live Minecraft channel must explicitly retain the fixed attenuation distance rather than native CC:T's volume-scaled distance. Global volume zero hibernates transport/rendering while server time continues; client-local MASTER/BLOCKS mute is separate. Harden the one-way renderer-start latch.

### KI-051 — ordinary replay

At physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration. A normal gap is acceptable. Do not add gapless/padding/prefetch/permanent-source work.

### KI-055 / KI-061

Add a real-MP3 progressive JLayer integration test across range sliding/starvation and focused `FinitePcmAudioStream` tests. Reclaim leftover staging files on whole staging-owner cleanup, not individual computer detach.

## Cross-cutting findings that must not be lost

### KI-062 — synchronized stream dispatch + blocking DNS

Dynamic legacy stream methods can hold the composite monitor while synchronous DNS runs. The server tick's `tickOwnership()` and synchronized composite `cleanup()` use the same monitor. Provider `forget`, `forgetLevel`, and `clearAll` reach cleanup during removal/Level unload/server stop.

A DNS-parked computer thread can therefore stall server tick ownership work or lifecycle cleanup waiting on that composite.

`audioPrepareStaged(...)` is **not** synchronized on the composite monitor; do not generalize KI-062 to every media operation.

Keep the fix narrow: preserve ownership ordering while moving/blocking DNS/I/O outside the shared monitor or otherwise removing server-thread dependence on it. Do not turn M1G into an M3 stream rewrite.

### KI-063 — replacement-before-admission

Rejected/failed RAW or prepared replacement can stop the current valid HQ source before the new source is known to be accepted. Validation/admission should precede destructive ownership transfer where practical.

### KI-054 / KI-064 — storage/shutdown hardening

- completed-file deletion failure can lose retry bookkeeping;
- range-service close can throw before store close, leaving the media-store root lock and stopped-server registry entry alive in the JVM;
- `writeExact()` can spin indefinitely on repeated zero reads;
- import lacks an unsupported-`ATOMIC_MOVE` fallback.

These are real but separate from decoder protocol design.

## Practical sequencing

The latest working recommendation is:

1. remove KI-062 before relying on legacy stream calls;
2. implement the explicit decoder/reanchor revision and fix KI-053/056/057 as one cluster;
3. implement fixed attenuation + global-volume-zero/renderer-start behavior;
4. implement ordinary replay;
5. close real-MP3 / `FinitePcmAudioStream` / cancellation/seek evidence gaps;
6. fix KI-061 staging cleanup;
7. place KI-063 and KI-054/KI-064 around that sequence according to patch cohesion;
8. run both NeoForge targets and focused Minecraft acceptance.

A broader safety-first batch closing KI-062/063/054/064 before v7 is also defensible. If choosing that route, record it explicitly. Do not silently pull HLS/legacy/CI/release work into either plan.

## Audit corrections — do not regress

A broad repository audit was useful but contained retracted claims. Exact source confirms:

- `FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`; STATE carries both;
- modern STATE does **not** carry world x/y/z; BEGIN carries initial world/block position;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- the provider WeakHashMap intentionally depends on explicit lifecycle eviction because cached values reference their Level;
- `HQFiniteMediaServer.tick()` does not itself perform the per-player/fanout work first attributed to it;
- inherited HTTP stream paths close their streams;
- release retry ticking is `ServerMediaAssets.tickPendingReleases()`.

The audit report/PR is supporting evidence, not current authority. Verify exact source before promoting any further audit claim.

## M1H / VS2 note

Modern BEGIN carries block coordinates; STATE does not carry live position updates. `FiniteSpeakerSound.updatePosition(...)` exists but modern M1G does not call it after renderer creation.

The legacy client already recomputes VS2 ship-transformed positions from packet block coordinates each tick. M1H may mirror that client-side path or add explicit authoritative position updates if later requirements justify it. Do not silently choose during M1G.

## Later work — keep out of M1G

- full late-entry/proactive-leave/return-rejoin/dimension/reload/general-underrun/VS2 lifecycle — M1H;
- native FLAC — M1I;
- multispeaker shared clocks/helpers — M1J/M1K;
- legacy finite migration/removal — M1L;
- broader RAW/OpenAL cleanup — M1M/M1N;
- Sound Physics Remastered compatibility/range/acoustics — M2;
- inherited live MP3/HLS/TS repair — M3;
- separate HQ block, CI hygiene, license provenance, release cleanup — M4.

Confirmed later issues include the inherited HLS refreshed-window index bug, misleading legacy capability lists, and legacy `playNoteAll`/`playSoundAll` semantic mismatch. Do not fix them incidentally during M1G unless a genuinely shared primitive requires it.

## M1G correctness rules

- server M1E state remains canonical;
- temporary encoded starvation is not decoder EOF;
- decoder/network/disk waits stay off Minecraft/audio threads;
- encoded and decoded memory stay bounded independently of duration;
- semantic seek creates fresh codec state even if the coarse anchor byte is unchanged;
- ordinary state snapshots do not gratuitously restart healthy decoding;
- seek/replacement/stop discard stale encoded wait/decoder/PCM/renderer state;
- one physical speaker remains one mono positional source.

## Evidence boundary

M1E final focused Minecraft acceptance was skipped/unrecorded. M1F focused Minecraft transport acceptance is unrecorded. M1G integrated source/tests/package is green at the source checkpoint above, but real-MP3 progressive integration coverage, focused renderer-adapter coverage, selected ordinary replay, and focused audible Minecraft acceptance remain incomplete.
