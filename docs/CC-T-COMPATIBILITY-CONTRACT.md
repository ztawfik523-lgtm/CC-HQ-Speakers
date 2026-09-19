# CC:Tweaked 1.120.0 speaker compatibility contract

Updated: 2026-09-19

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

Exact target source wins for version-specific internals.

## Current implementation boundary

M1A keeps CC:T's actual `SpeakerPeripheral` inside `SpeakerBlockEntity` and exposes it through `HQSpeakerCompositePeripheral` for the standard calls.

Consequently the standard CC behavior does not synthesize or duplicate notes, Minecraft sounds, DFPWM buffering, or the native readiness event.

Status labels in this document distinguish:

- **SOURCE IMPLEMENTED** — current Java delegates to the exact CC:T implementation and compiles in CI;
- **RUNTIME PASS** — only after the Minecraft acceptance script has actually passed.

The standard contract is source-implemented; focused runtime acceptance remains unrecorded.

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
- volume/pitch validation follows exact CC:T behavior;
- note playback is subject to CC:T's configured per-tick note limit;
- return value indicates whether the note was accepted.

### Exact 1.120.0 pitch-default discrepancy

The official speaker documentation says omitted pitch defaults to `12` semitones.

The exact Minecraft 1.21.1 / CC:T 1.120.0 source tag calls `pitchA.orElse(1.0)` before converting semitones with `(pitch - 12) / 12`.

Those sources therefore disagree. Do not silently rewrite this discrepancy as settled.

Current HQ source status: **SOURCE IMPLEMENTED**

M1A delegates directly to CC:T's original `SpeakerPeripheral.playNote`, preserving actual instruments, native note limits, validation, and exact-version pitch behavior.

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
- method returns false when normal CC:T sound/audio conflict rules prevent playback;
- requested sound is actually the sound which is played.

Current HQ source status: **SOURCE IMPLEMENTED**

When no HQ continuous source owns the physical speaker, M1A delegates directly to the original CC:T method.

Extension collision rule: while an HQ RAW/finite/live continuous source is active, standard `playSound` returns `false` instead of overlapping that source. This does not alter native behavior when standard methods are used on their own.

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

## Collision / ownership rules with HQ extensions

CC:T does not define MP3/OGG/WAV/live-stream calls, so their interaction with standard audio is an extension rule.

Accepted single-speaker rule:

- `playNote` remains independent;
- one HQ continuous source owns HQ output at a time;
- a new incompatible HQ start replaces the previous HQ source;
- repeated `speakPCM` calls continue the same RAW feed;
- an HQ start requests native CC:T sound/audio stop but does not remove notes;
- standard `playSound` / `playAudio` return `false` while HQ continuous ownership is active;
- Java does not create a playlist or automatic finite-media queue.

### KI-063 replacement-admission behavior — resolved

The intended “new HQ start replaces old HQ source” rule does **not** mean a failed/rejected start should destroy valid current playback.

Pre-hardening source transferred/stopped ownership too early in some RAW and prepared replacement paths. Post-M1G hardening resolved KI-063 for those known paths: RAW input is validated/converted first, and prepared playback is retained/constructed through a `PreparedStart` admission token before destructive ownership transfer. Preserve that admit-first behavior in future compatibility work.

The inherited multi-speaker `*All` / `*At` helpers are not covered by this single-speaker guarantee. They are scheduled for later replacement/migration. Source review also confirmed that legacy `playNoteAll`/`playSoundAll` do not preserve requested normal note/sound semantics.

## HQ RAW capability notes

`speakPCM` is not the standard `playAudio` API.

Current source behavior:

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

## Modern HQ finite range / volume contract

This section applies to the **HQ modern finite extension**, not to native CC:T methods.

CC:T native volume range is `0.0..3.0`, and exact target CC:T client code increases its live attenuation distance when volume is above 1.

The owner deliberately selected a different M1G policy for modern HQ finite playback:

- fixed 32-block core listening/delivery radius;
- positional attenuation inside that radius;
- HQ finite `volume` changes gain/loudness, not core radius;
- future Sound Physics Remastered compatibility owns intentional range/acoustic extension and matching transport relevance.

Therefore M1G modern finite code should **not** copy CC:T's `Math.max(volume, 1) * attenuationDistance` behavior for its custom finite renderer. Standard CC:T methods remain on the native CC:T path and retain native behavior.

Final M1G source explicitly installs/retains the selected fixed 32-block attenuation distance on the modern finite live channel. KI-058 is resolved.

## Modern HQ volume zero

For the modern finite extension, current M1G behavior is:

- global HQ volume zero does not pause canonical server time;
- local modern finite decoder/renderer/range requests hibernate while globally muted;
- server range delivery is suppressed while globally muted;
- unmute rebuilds/rejoins current authoritative time.

A player's own Minecraft MASTER/BLOCKS slider remains client-local and does not change server transport policy.

## Standard versus inherited legacy helpers

The standard singular CC:T methods above are the compatibility contract.

Inherited HQ capability lists and `*All` / `*At` helpers are legacy surfaces and are not proof of standard semantics or modern prepared format support.

Examples confirmed by source review:

- legacy `speakSupportedFiles()` advertises formats broader than modern prepared support;
- legacy `playNoteAll(...)` ignores the requested instrument and synthesizes a sine;
- legacy `playSoundAll(...)` ignores the requested sound name by routing into that sine path.

These are later migration/cleanup issues; do not use them to redefine the standard singular contract.

## Compatibility acceptance tests

Core native contract:

```text
scripts/p0_cc_speaker_contract.lua
```

M1A extension/output contract:

```text
scripts/m1a_output_contract.lua [optional-small-mp3]
```

These scripts are historical/current only for the surfaces they actually exercise. They do not prove the modern M1G prepared path.

CI proves compilation/tests/package structure, not actual Minecraft sound and peripheral lifecycle behavior.

Do not mark this document **RUNTIME PASS** until focused target-stack runtime checks have actually passed.
