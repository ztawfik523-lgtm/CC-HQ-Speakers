# Verified facts

Updated: 2026-09-21  
Checked against frozen source `c61b052beee03ec0f36fed725fb37483bfb57d83`.

## Product/package

- only `computercraft:speaker` is the block product;
- standalone `hqspeaker:hq_speaker` is removed;
- internal sound resource is `hqspeaker:hq_audio_source`;
- license is MPL-2.0;
- protocol is v10 with 9 payloads;
- JLayer 1.0.1.4 and Sable Companion 1.6.0 are embedded;
- mp3spi/Tritonus are removed;
- `c61b052beee03ec0f36fed725fb37483bfb57d83` passed CI `35655973164` on NeoForge 21.1.247 and 21.1.248.

## Native CC:T

- singular native calls use the real CC:T `SpeakerPeripheral`;
- native All/At helpers dispatch to real selected CC:T speakers;
- native `speaker_audio_empty` remains CC:T-owned.

## Finite

- supported formats are MP3 + supported common WAV;
- `speakMp3/speakWav` singular/All/At use the modern finite engine;
- one shared finite authority represents multispeaker playback;
- endpoints keep independent source/listener/transport/renderer/gain/mute state;
- membership is a start-time endpoint snapshot;
- pause/resume/seek/loop and ordinary/All stop are shared;
- volume/mute are endpoint-local;
- `audioStopAt` detaches only the selected endpoint;
- core finite relevance radius is 32 blocks;
- range transport limits are 128 KiB/response, 512 KiB client window, 4 outstanding requests and 512 KiB outstanding bytes/player, with 2 server IO workers and queue 64.

## RAW

- signed-16 mono PCM at 48 kHz;
- max 131072 samples/call;
- queue limit 16;
- observed rejection uses `hqspeaker_audio_empty`;
- All preflights the entire target snapshot and uses one future start tick without expected-member synchronization.

## MP3/ICY radio

- surviving methods are `speakStream`, `speakStreamAll`, `speakStreamAt`;
- HLS/TS are removed;
- grouped radio has strict no-auto-membership and no expected-member count;
- a local grouped session uses one shared decoder and prebuffer;
- radio gain is applied once at the Minecraft sound source;
- client/server URL validation allows HTTP/HTTPS only and rejects local/private/reserved targets and disallowed ports; redirects are disabled;
- `isStreaming` is server-side stream ownership/request state, not proof of remote-client audibility.

## Movement

- all HQ positional paths use the same `MovingSourcePosition` resolver;
- order is Sable Companion, then VS2, then static block center;
- Sable sublevel projection does not change the parent Minecraft Level identity.

## Removed

OGG/generic whole-file aliases, HLS, TS, duplicate finite engine/payloads, old expected-count group barriers, stale control aliases, fake standard/RAW legacy bodies, standalone HQ block.

## Evidence boundary

CI/package evidence is green. Focused Minecraft proof is still deferred for listener/recovery, movement, real multispeaker/radio sync, RAW audibility/timing, bounds/stress, Sound Physics Remastered and realistic performance.
