# CC:Tweaked 1.120.0 speaker compatibility contract

## Why this is mandatory

This mod injects into CC:Tweaked's `SpeakerBlockEntity` and returns `HQSpeakerPeripheral` in place of the normal peripheral.

`HQSpeakerPeripheral.getType()` returns:

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

## Standard peripheral identity

Required:

```text
peripheral type = "speaker"
```

Current HQ status: **PASS**

## `playNote`

CC:T surface:

```lua
speaker.playNote(instrument [, volume [, pitch]]) -> boolean
```

Required semantics:

- `instrument` identifies a real Minecraft note-block instrument;
- volume is optional and defaults to `1.0`;
- pitch is optional and defaults to `12` semitones;
- invalid instrument throws a Lua error;
- volume is clamped/validated as CC:T does;
- pitch must be finite;
- note playback is subject to CC:T's configured per-tick note limit;
- return value indicates whether the note was accepted.

Current HQ status: **FAIL**

Current source:

- requires primitive volume and pitch parameters;
- ignores `instrument`;
- synthesizes a sine wave;
- does not preserve CC:T note-limit behavior.

Compatibility requirement:

Restore actual CC:T note semantics. HQ finite/raw internals should not redefine this call.

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

Current HQ status: **FAIL**

Current source ignores `soundName` and delegates to the generated note path.

Compatibility requirement:

Restore real sound-event playback and normal CC:T collision behavior for this standard method.

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
- when omitted, documented behavior is previous `playAudio` volume.

Current HQ status: **PARTIAL / FAIL**

Preserved:

- signed 8-bit input convention;
- 48 kHz nominal raw format;
- maximum table size is effectively 131072.

Not preserved:

- single-pending-buffer/backpressure semantics;
- `speaker_audio_empty` meaning;
- previous-`playAudio` volume default.

Compatibility requirement:

Implement CC:T-compatible `playAudio` flow control even if HQ-specific `speakPCM` later has additional capabilities.

## `speaker_audio_empty`

Required semantics:

This event is backpressure notification for `playAudio`.

It should be queued when a previously accepted pending raw audio buffer has been pulled/freed such that the producer can submit another buffer.

It must not be emitted repeatedly merely because the server's packet queue is under a threshold.

Current HQ status: **FAIL**

At reviewed source, `speakerTick()` can mark ready every tick while queue size is below `SPEAKER_READY_MARK`.

## `stop`

CC:T surface:

```lua
speaker.stop()
```

Required semantics:

Stops the normal speaker's active queued audio/latest sound and clears standard speaker playback state.

Current HQ status: **FAIL**

Current HQ has `speakStop()` / `audioStop()`, but replacing a `speaker` requires the standard name too.

Compatibility requirement:

Expose `stop()` and make it coherent with HQ state. Whether HQ-only queued finite/live media is also stopped by standard `stop()` should follow the physical-speaker rule chosen for the extension; it must never leave stale client renderers.

## Collision rules with HQ extensions

CC:T does not define MP3/OGG/WAV/live-stream calls, so exact interaction between standard raw audio and HQ finite/live calls is an extension decision.

The choice must not violate the standard methods' behavior when used on their own.

See `P0-DESIGN-DECISIONS.md`.

## Volume/category notes

CC:T volume range is `0.0..3.0`.

HQ custom audio currently uses Minecraft's BLOCKS category. Standard CC:T note/sound behavior uses CC:T's native speaker network/rendering behavior and should not be replaced with a generated PCM approximation.

Any deliberate sound-category difference must be documented because it is user-visible.

## Compatibility acceptance tests

`scripts/p0_cc_speaker_contract.lua` is the runtime acceptance script for the core Lua contract.

It intentionally exposes current failures and should become green during M1A stabilization.

CI cannot fully prove these behaviors because actual `SpeakerBlockEntity`/client audio lifecycle requires Minecraft runtime evidence.
