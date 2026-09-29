# CC:T compatibility contract

Updated: 2026-09-29

The normal `computercraft:speaker` remains the product surface. HQ functionality wraps the exposed peripheral while retaining the real CC:T `SpeakerPeripheral` for native behavior.

## Native behavior that must remain native

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

Grouped/indexed native helpers select physical endpoints but still dispatch through the real CC:T speaker implementation.

## HQ logical volume/range

HQ playback has its own v11 tuning contract and does not change native CC:T volume semantics.

HQ logical volume is server-validated and resolved to independent source gain + range. Explicit HQ range is in blocks and affects HQ playback only.

Native CC:T methods continue to use CC:T's own speaker implementation and attenuation behavior.

## HQ RAW

RAW is a separate extension:

- signed 16-bit;
- mono;
- 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- singular/All/At;
- optional HQ logical volume + explicit range;
- no fake seek/duration/loop state.

When a producer-fed stream locally exhausts and later PCM arrives, the existing Minecraft channel is pumped again rather than padding the gap with fake silence.

A continuous RAW lifetime keeps the server audio profile it started with.

## Modern finite

HQ finite is separate from native CC:T audio:

- MP3 + supported common WAV;
- server-resolved gain/range;
- range controls listener relevance and client fade-to-zero distance;
- pause/resume/seek/loop and ordinary shared stop;
- `audioStopAt(index)` intentionally detaches only the selected endpoint;
- multispeaker shared playback authority;
- endpoint-local volume/range/mute.

Calling `audioSetRange()` without a value returns that endpoint to automatic volume-derived range.

## Radio

HQ MP3/ICY radio is separate from native CC:T behavior. Group membership is strict at start; late speakers require a rerun.

## Diagnostics

Dormant HQ diagnostics observe both HQ and selected native CC:T client channels for acceptance. They do not replace native behavior and do not add a network payload.

Historical v10 runtime acceptance already proved native CC:T client-channel behavior on the selected target. V11 runtime work must preserve that regression while validating the new HQ tuning/acoustic path.
