# Lua API

Updated: 2026-09-20

The peripheral type remains `speaker`. Standard CC:T speaker methods continue to exist; HQ methods are extensions.

## Recommended modern finite module

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")
```

### `hq.prepareFile(speaker, path) -> assetId`

Copy a ComputerCraft-local file into immutable server media storage and analyze it.

Modern supported formats:

- MP3
- supported common WAV

### `hq.preparedInfo(speaker, assetId) -> table`

Returns server-derived facts such as format, duration, sample rate, channels, bits per sample, size and source name.

### `hq.preparedFormats(speaker) -> table`

Current result:

```lua
{ mp3 = true, wav = true }
```

`speaker.speakSupportedFiles()` also reports the current finite set `{"mp3","wav"}` for compatibility.

### `hq.playPrepared(speaker, assetId, options?) -> boolean`

Start one prepared asset on one speaker. `options.volume` is optional.

### `hq.playPreparedAll(speaker, assetId, options?) -> boolean`

Start one shared prepared playback on the speakers attached to the calling computer at invocation time. The endpoint set is a snapshot.

### `hq.releasePrepared(speaker, assetId) -> boolean`

Release the caller's preparation reference. Active playback owns its own retained reference.

### `hq.playFile(speaker, path, options?) -> boolean`

Prepare, start, then release the temporary preparation reference.

### `hq.playFileAll(speaker, path, options?) -> boolean`

Prepare once and start one shared playback across the current speaker snapshot.

## Finite compatibility names

These old names now use the modern finite engine:

- `speakMp3`
- `speakWav`
- `speakMp3All`
- `speakWavAll`
- `speakMp3At`
- `speakWavAt`

They are strict format-specific wrappers.

Removed:

- `speakOgg`;
- generic whole-file `speakAudio` / `speakFile` / `speakPacked`;
- corresponding All/At forms.

## Finite controls

Singular:

- `audioStatus()`
- `audioPause()`
- `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`
- `audioSetMuted(muted)`

Grouped/indexed equivalents exist for the modern finite surface, including status, pause/resume, seek, loop, stop, volume and mute All/At variants.

Shared playback controls affect the shared authority.

Volume/mute are endpoint-local. All-volume/all-mute targets the surviving playback endpoint snapshot.

`audioStatus()` for active finite playback includes state, playbackId, stateRevision, generation, format, position, duration, sampleRate, channels, bitsPerSample, volume, muted, looping, assetId, capability flags, and error when present.

## Standard CC:T methods

Preserved:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

Grouped/indexed standard helpers dispatch to real CC:T speakers:

- `playNoteAll` / `playNoteAt`
- `playSoundAll` / `playSoundAt`
- `playAudioAll` / `playAudioAt`

## HQ RAW PCM

### `speakPCM(samples [, volume]) -> boolean`

Signed 16-bit PCM at 48 kHz.

Current maximum: 131072 samples per call.

Returns false when bounded HQ RAW capacity cannot accept the chunk.

If a computer observes false, it may wait for `hqspeaker_audio_empty` and retry.

### `speakPCMAll(samples [, volume]) -> boolean`

Preflights the full endpoint snapshot. If any endpoint cannot admit the chunk, the call rejects before intentionally replacing/advancing part of the group.

Accepted endpoints share a future start tick but no expected-member barrier.

### `speakPCMAt(index, samples [, volume]) -> boolean`

Target one current physical speaker index.

Related helpers include:

- `speakQueueSize()`
- `speakSampleRate()`
- `speakMaxSamples()`
- `speakStop()`
- `speakVolume(...)`
- `speakIsPlaying()`

RAW has no real seek/duration/loop model.

## Speaker discovery

Available grouped/indexed discovery includes:

- `getSpeakerCount()`
- `getSpeakers()`
- `getSpeakerPos(index)`

## Optional live streaming

Still exposed but not core release functionality:

- `speakStream(url [, volume])`
- `speakHLS(url [, volume])`
- `speakTS(url [, volume])`
- All/At variants
- ICY metadata getters/events.

Grouped live helpers still use legacy expected-count synchronization. HLS long-running refresh behavior has a known concern.

Do not treat these optional live APIs as the architecture contract for modern finite/RAW.

## Events

`speaker_audio_empty` is native CC:T `playAudio` backpressure.

`hqspeaker_audio_empty` is HQ RAW retry notification after observed rejection.

`hqspeaker_audio_state` is modern finite state projection to attached computers.

## Range/volume behavior

Modern finite core server relevance is fixed at 32 blocks.

Finite volume changes gain, not this radius.

## Removed standalone block

There is no `hqspeaker:hq_speaker` peripheral block. Use the normal CC:T speaker.

The internal SoundManager event `hqspeaker:hq_audio_source` is not a Lua API or block.
