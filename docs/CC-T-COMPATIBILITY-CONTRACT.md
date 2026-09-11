# CC:Tweaked 1.120.0 speaker compatibility contract

## Why this is mandatory

This mod injects into CC:Tweaked's `SpeakerBlockEntity` and still exposes peripheral type:

`"speaker"`

Therefore a normal ComputerCraft program is entitled to expect the standard speaker contract unless an incompatibility is explicitly unavoidable.

HQ APIs are extensions to this contract.

## Exact upstream basis

Target:

- CC:Tweaked 1.120.0
- Minecraft 1.21.1
- release tag `v1.21.1-1.120.0`
- release commit `98f3a71`

Primary source:

`projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java`

Official API documentation:

`https://tweaked.cc/peripheral/speaker.html`

This document records the contract needed by this fork. It does not copy implementation internals unnecessarily.

## Current implementation boundary

M1A keeps CC:T's actual `SpeakerPeripheral` inside `SpeakerBlockEntity` and exposes it through `HQSpeakerCompositePeripheral` for the standard calls.

Consequently the source implementation for standard CC behavior no longer attempts to synthesize or duplicate notes, Minecraft sounds, DFPWM buffering, or the native readiness event.

Status labels in this document distinguish:

- **SOURCE IMPLEMENTED** — current Java delegates to the exact CC:T implementation and compiles in CI;
- **RUNTIME PASS** — only after the Minecraft acceptance script has actually passed.

At the current M1A branch, the standard contract is source-implemented but runtime acceptance is still pending.

## Standard peripheral identity

Required:

```text
peripheral type = "speaker"
```

Current HQ source status: **SOURCE IMPLEMENTED**

## `playNote`

CC:T surface:

```lua
speaker.playNote(instrument [, volume [, pitch]]) -> boolean
```

Required semantics:

- `instrument` identifies a real Minecraft note-block instrument;
- volume is optional and defaults to `1.0`;
- pitch is optional;
- invalid instrument throws a Lua error;
- volume is clamped/validated as CC:T does;
- pitch must be finite;
- note playback is subject to CC:T's configured per-tick note limit;
- return value indicates whether the note was accepted.

### Exact 1.120.0 pitch-default discrepancy

The official speaker documentation says omitted pitch defaults to `12` semitones.

The exact Minecraft 1.21.1 / CC:T 1.120.0 source tag instead calls:

`pitchA.orElse(1.0)`

before converting semitones with `(pitch - 12) / 12`.

Those sources therefore disagree. Do not silently rewrite this discrepancy as a settled fact.

For drop-in compatibility work, the exact 1.120.0 runtime/source behavior is the version-specific reference unless the project explicitly decides to correct the upstream bug/documentation mismatch. The runtime contract checks that the optional argument may be omitted, not which audible default pitch is chosen.

Current HQ source status: **SOURCE IMPLEMENTED**

M1A delegates directly to CC:T's original `SpeakerPeripheral.playNote`, preserving actual instruments, the native note limit, validation, and exact-version pitch behavior.

Notes remain independent of HQ continuous-source ownership. Starting HQ RAW/finite/stream playback does not clear pending native notes.

## `playSound`

CC:T surface:

```lua
speaker.playSound(name [, volume [, pitch]]) -> boolean
```

Required semantics:

- `name` is a Minecraft/modded sound identifier;
- volume optional, default `1.0`;
- pitch optional, default `1.0`;
- malformed/invalid sound names are rejected according to CC:T behavior;
- method returns false when the speaker's normal sound/audio conflict rules prevent playback;
- requested sound is actually the sound which is played.

Current HQ source status: **SOURCE IMPLEMENTED**

When no HQ continuous source owns the physical speaker, M1A delegates directly to the original CC:T method.

Extension collision rule: while an HQ RAW/finite/live continuous source is active, standard `playSound` returns `false` instead of overlapping that source. This is an HQ extension policy; it does not alter native behavior when standard methods are used on their own.

## `playAudio`

CC:T surface:

```lua
speaker.playAudio(audio [, volume]) -> boolean
```

Required input semantics:

- signed 8-bit sample amplitudes, `-128..127`;
- 48 kHz playback;
- non-empty table;
- at most `128 * 1024` samples per call;
- malformed/out-of-range samples cause a Lua error.

Required flow-control semantics:

- only one pending `playAudio` buffer is accepted at a time;
- if there is no room for another buffer, return `false`;
- the program can wait for `speaker_audio_empty` and retry;
- `speaker_audio_empty` means capacity became available; it is not an idle heartbeat.

Required volume semantics:

- optional volume;
- when omitted, CC:T retains the previous native `playAudio` volume through its DFPWM state.

Current HQ source status: **SOURCE IMPLEMENTED**

M1A delegates to CC:T's exact native method, including the 131072-sample limit, one-pending-buffer behavior, native DFPWM state, and previous-volume behavior.

Extension collision rule: while an HQ continuous source owns the output, standard `playAudio` returns `false`. Once HQ ownership ends, native `playAudio` operates normally again.

## `speaker_audio_empty`

Required semantics:

This event is backpressure notification for standard `playAudio`.

It should be queued when a previously accepted pending native raw audio buffer has been pulled/freed such that the producer can submit another buffer.

It must not be emitted repeatedly merely because an HQ packet queue is under a threshold.

Current HQ source status: **SOURCE IMPLEMENTED**

The exact CC:T `SpeakerPeripheral` owns this event. The legacy HQ peripheral is attached through a filtered computer view which suppresses its inherited synthetic `speaker_audio_empty` heartbeat.

HQ `speakPCM` has a different bounded queue and therefore uses a distinct event:

```text
hqspeaker_audio_empty
```

That event is emitted only to a computer which actually observed `speakPCM(...) == false`, once the HQ server queue has capacity again. It must not be treated as the native CC:T event.

## `stop`

CC:T surface:

```lua
speaker.stop()
```

Native CC:T semantics clear standard arbitrary audio/latest sound state; pending note events are not cleared by `SpeakerPeripheral.stop()`.

Current HQ source status: **SOURCE IMPLEMENTED**

M1A calls the exact native `stop()` and also ends whichever HQ continuous source currently owns the physical speaker. This prevents stale HQ renderers from surviving a standard stop call while retaining native note behavior.

HQ `speakStop()` remains a full physical-speaker stop for inherited compatibility.

HQ `audioStop()` is a truthful stop capability for the current HQ continuous source, including RAW and live intent. It does not invent finite controls for open-ended sources.

## Collision rules with HQ extensions

CC:T does not define MP3/OGG/WAV/live-stream calls, so their interaction with standard audio is an extension rule.

Accepted M1A rule for the normal single-speaker path:

- `playNote` remains independent;
- one HQ continuous source owns HQ output at a time;
- a new incompatible HQ start replaces the previous HQ source;
- repeated `speakPCM` calls continue the same RAW feed;
- an HQ start requests native CC:T sound/audio stop but does not remove notes;
- standard `playSound` / `playAudio` return `false` while HQ continuous ownership is active;
- Java does not create a playlist or automatic finite-media queue.

The inherited multi-speaker `*All` / `*At` helpers are not covered by this M1A ownership guarantee. They are scheduled for replacement by the later shared-asset/sync-clock architecture.

## HQ RAW capability notes

`speakPCM` is not the standard `playAudio` API.

M1A source behavior:

- signed 16-bit samples as inherited by HQ;
- 48 kHz;
- real maximum contiguous table size reported as `131072`;
- bounded inherited server queue;
- boolean backpressure;
- `hqspeaker_audio_empty` when a rejected producer may retry;
- no fake duration/seek/loop;
- `audioStop()` / `speakStop()` stop RAW;
- drained RAW ownership is released after sample-derived server-tick duration plus a short idle grace.

Audible drain timing is still runtime-pending.

## Volume/category notes

CC:T native volume range is `0.0..3.0`.

Standard CC:T note/sound/audio now stay on CC:T's own rendering path.

HQ custom audio still uses the inherited HQ renderer category and will be normalized in a later milestone. This does not change the standard native methods because they no longer go through HQ's generated PCM approximation.

## Compatibility acceptance tests

Core native contract:

```text
scripts/p0_cc_speaker_contract.lua
```

M1A extension/output contract:

```text
scripts/m1a_output_contract.lua [optional-small-mp3]
```

CI proves compilation/tests/package structure, not actual Minecraft sound and peripheral lifecycle behavior.

Do not mark this document **RUNTIME PASS** until those runtime checks have actually passed on the target stack.
