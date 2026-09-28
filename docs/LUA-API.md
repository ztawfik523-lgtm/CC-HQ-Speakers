# Lua API — working v11

Updated: 2026-09-28

Peripheral type remains `speaker`. The v10 freeze remains historical evidence; the current working contract is being updated for protocol v11 volume/range tuning.

## Recommended finite files

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 1.5, range = 48 })
```

Module helpers: `prepareFile`, `preparedInfo`, `preparedFormats`, `playPrepared`, `playPreparedAll`, `playFile`, `playFileAll`, `releasePrepared`, mute helpers.

Modern finite formats: MP3 + supported common WAV.

Compatibility byte names: `speakMp3/speakWav` plus All/At.

## Finite controls

Singular: `audioStatus`, `audioPause`, `audioResume`, `audioSeek`, `audioSetVolume`, `audioSetRange`, `audioSetLooping`, `audioStop`, `audioSetMuted`.

All/At equivalents exist for status, pause/resume, seek, volume, range, looping, stop and mute.

Shared playback operations: pause/resume/seek/loop and ordinary/All stop.

Endpoint operations: volume/range/mute. `audioStopAt(index)` stops/detaches only that endpoint. Calling `audioSetRange()` (or the All/At equivalent without a range value) returns that endpoint to automatic volume-derived range.

`audioStatus` reports logical `volume`, resolved `gain`, resolved `range`, and `rangeMode` (`auto` or `explicit`).

## Native CC:T

Preserved: `playNote`, `playSound`, `playAudio`, `stop`, `speaker_audio_empty`.

Added selected-endpoint helpers: `playNoteAll/At`, `playSoundAll/At`, `playAudioAll/At`.

## RAW

`speakPCM(samples [, volume [, range]])`, `speakPCMAll`, `speakPCMAt`.

Samples are signed 16-bit integers, mono 48 kHz, max 131072 samples/call. Backpressure rejection returns false; a producer that observed rejection may wait for `hqspeaker_audio_empty`.

Helpers: `speakStop`, `speakVolume`, `speakIsPlaying`, `speakQueueSize`, `speakSampleRate`, `speakMaxAudioBytes`, `speakMaxSamples`, `speakSupportedFiles`.

## MP3/ICY radio

`speakStream(url [, volume [, range]])`  
`speakStreamAll(url [, volume [, range]])`  
`speakStreamAt(index, url [, volume [, range]])`

Metadata/status helpers: `isStreaming`, `getStreamUrl`, `getStreamFormats`, `getStreamMeta`, `getStreamTitle`, `getStreamArtist`, `getStreamSong`, `getStreamStation`, `getStreamGenre`, `getStreamMetaSerial`.

Metadata event: `hqspeaker_metadata`.

All radio grouping is strict snapshot/no-auto-membership. A late/new speaker joins only after the script reruns the stream command.

`isStreaming` reports server-owned stream state, not confirmed client connectivity.

Only MP3/ICY HTTP(S) radio is supported. HLS and TS are removed.

## Discovery

`getPeripheralType`, `getPos`, `getSpeakerCount`, `getSpeakers`, `getSpeakerPos`.

## Runtime diagnostic methods

The release JAR also exposes dormant test instrumentation:

```lua
speaker.hqDiagEnable(true)     -- enable/disable collection
speaker.hqDiagReset()          -- begin a fresh measurement epoch
local snapshot = speaker.hqDiagSnapshot()
local caps = speaker.hqDiagCapabilities()
```

These methods exist for the release acceptance runner. They do not start audio, do not change playback ownership and do not add a network payload.

Normal scripts generally do not need them.

## Removed names

`speakOgg`; generic `speakAudio/speakFile/speakPacked` families; HLS/TS families; `speakStopAll`, `speakStopAt`, `speakVolumeAll`; old `setLooping`/All aliases.
