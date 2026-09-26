# Verified facts

Updated: 2026-09-26

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Current CI: `36205257110` — PASS on NeoForge 21.1.247.

## Product/package

- only `computercraft:speaker` is the block product;
- standalone `hqspeaker:hq_speaker` is removed;
- internal sound resource is `hqspeaker:hq_audio_source`;
- license is MPL-2.0;
- protocol is v10 with exactly 9 payloads;
- JLayer 1.0.1.4 and Sable Companion 1.6.0 are embedded;
- mp3spi/Tritonus are removed;
- build/test/package baseline is NeoForge 21.1.247 only;
- mod metadata accepts `[21.1,21.2)`;
- historical CI also exercised 21.1.248, but current release policy produces one artifact.

Current artifact `10892998704` contains required NeoForge metadata, mixin config, embedded JLayer, embedded Sable Companion and the CC:T ROM module. Extracted JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`.

## Native CC:T

- singular native calls use the real CC:T `SpeakerPeripheral`;
- native All/At helpers dispatch to real selected CC:T speakers;
- native `speaker_audio_empty` remains CC:T-owned;
- diagnostics can observe both native streaming `playAudio` and native static note/sound channels during acceptance.

## Finite

- supported formats are MP3 + supported common WAV;
- `speakMp3/speakWav` singular/All/At use the modern finite engine;
- prepared media and byte compatibility frontends share that engine;
- one shared finite authority represents multispeaker playback;
- endpoints keep independent source/listener/transport/renderer/gain/mute state;
- membership is a start-time endpoint snapshot;
- pause/resume/seek/loop and ordinary/All stop are shared;
- volume/mute are endpoint-local;
- `audioStopAt` detaches only the selected endpoint;
- core finite relevance radius is 32 blocks;
- range transport remains bounded.

## RAW

- signed-16 mono PCM at 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- observed rejection uses `hqspeaker_audio_empty`;
- All preflights the target snapshot and uses one future start tick;
- the producer-fed continuation fix re-pumps the existing Minecraft/OpenAL channel when later PCM arrives after local stream exhaustion;
- current diagnostics count admitted/read PCM, wakeups and unexpected mid-stream PLAYING->STOPPED transitions.

The continuation fix is not yet claimed as final runtime-proven until R6 passes in Minecraft.

## MP3/ICY radio

- surviving methods are `speakStream`, `speakStreamAll`, `speakStreamAt`;
- HLS/TS are removed;
- grouped radio is strict snapshot/no-auto-membership;
- a local grouped session uses one shared decoder/prebuffer;
- radio gain is applied once at the Minecraft sound source;
- URL policy allows HTTP/HTTPS only and rejects local/private/reserved targets and disallowed ports; redirects are disabled;
- `isStreaming` is server-side stream ownership/request state, not proof of client audibility.

## Movement

- all HQ positional paths use `MovingSourcePosition`;
- order is Sable Companion, then VS2, then static block center;
- the selected runtime target tests Sable/Aeronautics;
- VS2 runtime testing is explicitly outside this release acceptance target.

## Built-in diagnostics

The normal release JAR contains dormant diagnostics exposed through:

- `hqDiagEnable`
- `hqDiagReset`
- `hqDiagSnapshot`
- `hqDiagCapabilities`

The diagnostic system records real client channel state, OpenAL position/gain/timing, PCM delivery, decoder/recovery counters, reloads, group timing, Sable tracking and Sound Physics filter behavior. It uses an in-process bridge in singleplayer and does not add a v10 payload.

## Master test

`scripts/v10_acceptance.lua` is the sole user-facing release acceptance runner. It is syntax-compiled in CI with the same Cobalt Lua parser family CC:T uses.

The runner automatically judges A1-A19, R1-R9 and C1-C6. Human input is limited to physical actions.

## Removed

OGG/generic whole-file aliases, HLS, TS, duplicate finite engine/payloads, expected-count live membership barriers, stale control aliases, fake standard/RAW legacy bodies and standalone HQ block remain removed.

## Evidence boundary

CI/package evidence is green. Final runtime acceptance is still pending.
