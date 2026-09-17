# M1A — compatibility and output ownership

> **Historical M1A milestone record with still-active single-speaker ownership rules.** Later milestones replaced the staged/whole-file finite architecture and later audits found cross-cutting ownership/threading bugs. Current issue status lives in `KNOWN-ISSUES.md`; current compatibility contract lives in `CC-T-COMPATIBILITY-CONTRACT.md`.

## Scope

M1A stabilized the normal physical `computercraft:speaker` boundary before the modern MediaAsset/range/decode rewrite.

It deliberately did not repair the old retained-PCM finite engine, live streaming, or inherited multispeaker helpers.

## Standard CC:T speaker methods

The composite delegates standard singular methods to CC:T 1.120.0's real `SpeakerPeripheral`:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

This remains the required compatibility model.

Consequences:

- native note instruments/limits, sound identifiers, signed-8 `playAudio`, native buffering/backpressure, and native stop behavior come from CC:T rather than HQ reimplementations;
- `playNote` remains independent of HQ continuous playback;
- HQ RAW uses separate `hqspeaker_audio_empty` rather than synthesizing native `speaker_audio_empty`.

Focused runtime compatibility PASS remains unrecorded; source/CI is not enough.

## HQ continuous-source ownership

For one physical speaker, current composite ownership categories are conceptually:

- RAW;
- legacy finite;
- modern prepared/staged finite playback;
- legacy stream intent;
- NONE.

There is no Java playlist or automatic finite-media queue. Lua owns sequencing/priorities.

Repeated accepted `speakPCM` while RAW owns the speaker continues the same feed.

An accepted incompatible HQ start replaces previous HQ continuous output. Standard `playSound` / `playAudio` do not overlap active HQ continuous ownership; standard `playNote` remains independent.

### Later correctness finding — KI-063

M1A's intended replacement rule is **not** “destroy current playback even when the attempted replacement fails.”

Current source transfers/stops ownership too early in some RAW and modern-prepared replacement paths:

- RAW can stop the current source before capacity/admission rejects the new chunk;
- `audioPlayPrepared` can stop current playback before all asset/start failure paths complete.

A failed/retryable replacement should leave valid current playback alive unless destructive replacement is explicitly intended. This is KI-063 and remains unfixed.

## Synchronization / later KI-062

M1A serialized ownership-changing composite calls to prevent interleaved stop/start/owner changes across ComputerCraft threads.

Later audit found a cost in the current implementation: dynamic legacy stream dispatch is synchronized on the composite and may perform blocking DNS (`InetAddress.getAllByName`) while holding that monitor.

Server-tick `tickOwnership()` and synchronized `cleanup()` use the same monitor. Provider removal/Level unload/server stop can reach cleanup. Therefore a DNS-parked ComputerCraft thread can stall server tick ownership work or lifecycle cleanup waiting on that composite.

This is KI-062. `audioPrepareStaged(...)` is not synchronized on that monitor; do not generalize the issue to every media operation.

The fix should narrow/remove blocking work under the ownership monitor without discarding the need for coherent ownership transitions.

## Stop semantics

Current design intent remains:

- standard `stop()` stops native continuous CC:T sound/audio plus current HQ continuous source;
- `speakStop()` remains inherited full physical-speaker stop compatibility;
- `audioStop()` stops whichever HQ continuous source owns output;
- RAW/live do not gain fake finite duration/seek/loop semantics.

## HQ RAW backpressure

The inherited single-speaker queue is bounded and M1A added duration-aware RAW admission.

Key values remain:

```text
max contiguous speakPCM call: 131072 samples
sample rate: 48000 Hz
drain: 2400 samples/server tick
admission allowance: 131072 + 4800 = 135872 samples
```

`speakPCM()` returns false when packet queue or duration allowance cannot accept the requested valid-sized chunk. A waiting producer is later notified via:

```text
hqspeaker_audio_empty
```

This is distinct from native CC:T `speaker_audio_empty`.

Current KI-063 means a failed RAW replacement path must be audited/fixed so capacity rejection does not destroy another currently valid HQ source.

## RAW renderer lifetime

M1A tracks accepted RAW samples in server ticks. Once queue/outstanding samples drain and a short idle grace expires, inherited HQ stop is sent and RAW ownership is released.

This remains a source-level lifecycle mechanism; focused audible timing remains runtime-pending.

## Status/control routing

`audioStatus()` routes according to current HQ owner rather than stale state from another subsystem.

RAW reports truthful non-finite capability; no-owner reports idle. Finite controls route only to the finite owner which actually owns playback, except stop which is meaningful for every HQ continuous source.

Modern prepared finite state is now server-authoritative through `HQFiniteMediaServer`, not the historical staged/whole-file prototype semantics.

## Legacy multispeaker helpers — later work

Inherited `*All` / `*At` helpers still do not define the modern single-speaker ownership architecture.

Later source review specifically confirmed:

- `playNoteAll(...)` synthesizes a sine and ignores the requested instrument;
- `playSoundAll(...)` routes through that sine path and ignores the requested sound name.

Do not use these helpers as proof of standard CC:T semantics. Multispeaker redesign remains later M1J/M1K work.

## Other later boundaries

Since M1A:

- M1B/M1C introduced reusable server MediaAssets/import ownership;
- M1E made finite timeline server-authoritative;
- M1F replaced modern whole-file transfer with bounded demand-driven ranges;
- M1G integrated progressive MP3/common-WAV decode + positional renderer.

M1G now deliberately uses a fixed 32-block core modern-finite range, while full late-entry/leave-return/recovery/moving-source lifecycle remains M1H. SPR acoustic/range integration remains later work.

## Evidence

Useful compatibility runtime scripts remain:

```text
scripts/p0_cc_speaker_contract.lua
scripts/m1a_output_contract.lua [optional-small-mp3]
```

They exercise historical/compatibility surfaces only; they do not prove the modern M1G prepared path. Use `TESTING.md` / `M1-RUNTIME-TEST.md` for current acceptance.
