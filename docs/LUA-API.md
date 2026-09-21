# Lua API — frozen v10

Updated: 2026-09-21

Peripheral type remains `speaker`. See `API-FREEZE-V10.md` for the release contract.

## Recommended finite files

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Module helpers: `prepareFile`, `preparedInfo`, `preparedFormats`, `playPrepared`, `playPreparedAll`, `playFile`, `playFileAll`, `releasePrepared`, mute helpers.

Modern finite formats: MP3 + supported common WAV.

Compatibility byte names: `speakMp3/speakWav` plus All/At.

## Finite controls

Singular: `audioStatus`, `audioPause`, `audioResume`, `audioSeek`, `audioSetVolume`, `audioSetLooping`, `audioStop`, `audioSetMuted`.

All/At equivalents exist for status, pause/resume, seek, volume, looping, stop and mute.

Shared playback operations: pause/resume/seek/loop and ordinary/All stop.

Endpoint operations: volume/mute. `audioStopAt(index)` stops/detaches only that endpoint.

## Native CC:T

Preserved: `playNote`, `playSound`, `playAudio`, `stop`, `speaker_audio_empty`.

Added selected-endpoint helpers: `playNoteAll/At`, `playSoundAll/At`, `playAudioAll/At`.

## RAW

`speakPCM(samples [, volume])`, `speakPCMAll`, `speakPCMAt`.

Samples are signed 16-bit integers, mono 48 kHz, max 131072 samples/call. Backpressure rejection returns false; a producer that observed rejection may wait for `hqspeaker_audio_empty`.

Helpers: `speakStop`, `speakVolume`, `speakIsPlaying`, `speakQueueSize`, `speakSampleRate`, `speakMaxAudioBytes`, `speakMaxSamples`, `speakSupportedFiles`.

## MP3/ICY radio

`speakStream(url [, volume])`  
`speakStreamAll(url [, volume])`  
`speakStreamAt(index, url [, volume])`

Metadata/status helpers: `isStreaming`, `getStreamUrl`, `getStreamFormats`, `getStreamMeta`, `getStreamTitle`, `getStreamArtist`, `getStreamSong`, `getStreamStation`, `getStreamGenre`, `getStreamMetaSerial`.

Metadata event: `hqspeaker_metadata`.

All radio grouping is strict snapshot/no-auto-membership. A late/new speaker joins only after the script reruns the stream command.

`isStreaming` reports server-owned stream state, not confirmed client connectivity.

Only MP3/ICY HTTP(S) radio is supported. HLS and TS are removed.

## Discovery

`getPeripheralType`, `getPos`, `getSpeakerCount`, `getSpeakers`, `getSpeakerPos`.

## Removed names

`speakOgg`; generic `speakAudio/speakFile/speakPacked` families; HLS/TS families; `speakStopAll`, `speakStopAt`, `speakVolumeAll`; old `setLooping`/All aliases.
