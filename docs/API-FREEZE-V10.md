# API freeze — protocol v10

Updated: 2026-09-26  
Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze checkpoint: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Target: MC 1.21.1 / Java 21 / CC:T 1.120.0 / NeoForge 21.1.x  
Build baseline: **NeoForge 21.1.247 only**, metadata range `[21.1,21.2)`

The playback/product contract is frozen. Runtime testing may justify bug fixes, but feature/API redesign is not active work.

## Native CC:T surface

Preserved: `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`.

Added grouped/indexed native dispatch: `playNoteAll/At`, `playSoundAll/At`, `playAudioAll/At`. These still invoke real CC:T speaker peripherals.

## Modern finite

Prepared-media methods:

- `audioMountPath`
- `audioPrepareStaged`
- `audioPreparedInfo`
- `audioPreparedFormats`
- `audioPlayPrepared`
- `audioPlayPreparedAll`
- `audioReleasePrepared`
- `audioMaxStagedBytes`

Compatibility byte frontends using the same engine:

- `speakMp3`, `speakWav`
- `speakMp3All`, `speakWavAll`
- `speakMp3At`, `speakWavAt`

Supported finite formats: MP3 and supported common WAV only.

Finite controls:

- singular: `audioStatus`, `audioPause`, `audioResume`, `audioSeek`, `audioSetVolume`, `audioSetLooping`, `audioStop`, `audioSetMuted`
- All: `audioStatusAll`, `audioPauseAll`, `audioResumeAll`, `audioSeekAll`, `audioSetVolumeAll`, `audioSetLoopingAll`, `audioStopAll`, `audioSetMutedAll`
- At: `audioStatusAt`, `audioPauseAt`, `audioResumeAt`, `audioSeekAt`, `audioSetVolumeAt`, `audioSetLoopingAt`, `audioStopAt`, `audioSetMutedAt`

Pause/resume/seek/loop and ordinary/All stop are shared-playback operations. Volume/mute are endpoint-local. `audioStopAt(index)` is intentionally endpoint-local detach/stop.

## RAW PCM

- `speakPCM`, `speakPCMAll`, `speakPCMAt`
- signed 16-bit mono PCM, 48 kHz
- max 131072 samples per call
- bounded queue/backpressure
- retry event: `hqspeaker_audio_empty`

Helpers retained: `speakStop`, `speakVolume`, `speakIsPlaying`, `speakQueueSize`, `speakSampleRate`, `speakMaxAudioBytes`, `speakMaxSamples`, `speakSupportedFiles`.

`speakVolume` sets the default gain used by later compatibility/RAW/radio starts; it is not a current-playback volume control.

## MP3/ICY radio

- `speakStream(url [, volume])`
- `speakStreamAll(url [, volume])`
- `speakStreamAt(index, url [, volume])`
- `isStreaming`, `getStreamUrl`, `getStreamFormats`
- `getStreamMeta`, `getStreamTitle`, `getStreamArtist`, `getStreamSong`, `getStreamStation`, `getStreamGenre`, `getStreamMetaSerial`
- event: `hqspeaker_metadata`

Grouped radio is strict snapshot/no-auto-membership. Late/new speakers wait for a rerun. Each client uses one shared decoder/prebuffer for its accepted local group.

Radio volume is applied once as Minecraft sound-source gain.

`isStreaming()` is server-side requested/owned stream state. It does not prove that every client successfully connected to or is currently hearing the remote URL.

URL policy: HTTP/HTTPS only, no userinfo, bounded length, restricted ports, local/private/reserved targets rejected, redirects are not followed.

HLS and MPEG-TS are not supported.

## Discovery

`getPeripheralType`, `getPos`, `getSpeakerCount`, `getSpeakers`, `getSpeakerPos`.

Discovery/status reads do not intentionally supersede in-flight playback admission.

## Built-in diagnostic instrumentation

The normal release JAR carries four dormant test-instrumentation methods:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

These methods were added after the original playback freeze to replace subjective listening checks with measurable client/OpenAL evidence. They are disabled during normal use unless explicitly enabled by the acceptance runner.

The diagnostic subsystem does **not** add a network payload, change playback ownership/control semantics or create a second audio engine. In the selected singleplayer test scope, client and integrated server share diagnostic state in-process.

## Removed names

Retired and must remain absent:

- `speakOgg`
- `speakAudio`, `speakFile`, `speakPacked` and corresponding All/At variants
- `speakHLS`, `speakHLSAll`, `speakHLSAt`
- `speakTS`, `speakTSAll`, `speakTSAt`
- `speakStopAll`, `speakStopAt`, `speakVolumeAll`
- legacy `setLooping` / `setLoopingAll`

## Movement

All HQ positional paths use `MovingSourcePosition`: Sable Companion first, VS2 second, static block center otherwise. No continuous server position-packet stream is introduced.

## Protocol/package

Protocol v10 has exactly 9 registered payloads. JLayer 1.0.1.4 and Sable Companion 1.6.0 are Jar-in-Jar dependencies. mp3spi/Tritonus are removed.

## Build policy

Build/test/package only on NeoForge 21.1.247. The resulting JAR declares `[21.1,21.2)`. Historical 21.1.248 CI is evidence only; do not create a second current artifact.
