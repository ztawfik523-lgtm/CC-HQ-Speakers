# CC:T compatibility contract

Updated: 2026-09-26

The normal `computercraft:speaker` remains the product surface. HQ functionality wraps the exposed peripheral while retaining the real CC:T `SpeakerPeripheral` for native behavior.

## Native behavior that must remain native

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

Grouped/indexed helpers select physical endpoints but still dispatch through the real CC:T speaker implementation.

## HQ RAW

RAW is a separate extension:

- signed 16-bit;
- mono;
- 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- singular/All/At;
- no fake seek/duration/loop state.

The client continuation fix deliberately mirrors CC:T's streaming behavior: when a producer-fed stream has locally exhausted and later PCM arrives, the existing Minecraft channel is pumped again rather than padding the gap with fake silence.

## Modern finite

Separate extension:

- MP3/common WAV;
- fixed 32-block core relevance/delivery;
- volume affects gain, not core radius;
- pause/resume/seek/loop and ordinary/All stop;
- `audioStopAt(index)` intentionally detaches only the selected physical endpoint;
- multispeaker shared authority;
- endpoint-local volume/mute.

## Diagnostic acceptance

The final master runner now includes automated native CC:T client-channel checks as well as HQ finite/RAW checks. Built-in diagnostics observe the actual client sound channels, so native compatibility is no longer left as an unimplemented future runtime gate.

The current release still requires the final in-game master PASS; CI alone is not runtime proof.
