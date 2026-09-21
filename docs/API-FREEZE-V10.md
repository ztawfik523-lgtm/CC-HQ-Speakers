# API freeze — protocol v10

Frozen: 2026-09-21  
Source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
CI: `35655973164`  
Target: MC 1.21.1 / Java 21 / CC:T 1.120.0 / NeoForge 21.1.247 + 21.1.248

This is the release-candidate public contract. Runtime testing may justify bug fixes, but method additions/removals and semantic redesign are frozen unless a release-blocking defect requires them.

## Native CC:T surface

Preserved: `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`.

Added grouped/indexed native dispatch: `playNoteAll/At`, `playSoundAll/At`, `playAudioAll/At`. These invoke real CC:T speaker peripherals.

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

Grouped radio is strict snapshot/no-auto-membership. Late/new speakers wait for a rerun. Each client uses one shared decoder for its accepted local group and releases all endpoints together after prebuffering.

Radio volume is applied once as Minecraft sound-source gain; decoded PCM is not pre-scaled.

`isStreaming()` is server-side requested/owned stream state. It does not prove that every client successfully connected to or is currently hearing the remote URL.

URL policy: HTTP/HTTPS only, no userinfo, bounded length, restricted ports, local/private/reserved targets rejected, redirects are not followed.

HLS and MPEG-TS are not supported.

## Discovery

`getPeripheralType`, `getPos`, `getSpeakerCount`, `getSpeakers`, `getSpeakerPos`.

Discovery/status reads do not intentionally supersede an in-flight radio admission.

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

Protocol v10 has 9 registered payloads. JLayer 1.0.1.4 and Sable Companion 1.6.0 are Jar-in-Jar dependencies. mp3spi/Tritonus are removed.
