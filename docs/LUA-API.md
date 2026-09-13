# Lua / ComputerCraft API reference

This is the current user-facing programming reference for CC:HQ Speakers.

The mod upgrades the normal ComputerCraft `speaker`. Lua decides what the audio means; Java exposes audio capabilities and playback controls.

## Recommended starting point

For normal Minecraft sounds and standard DFPWM audio, use the normal CC:T speaker API.

For a local finite MP3/WAV file:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

assert(hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 }))
```

For preload/reuse:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

local asset = hq.prepareFile(speaker, "/music/song.mp3")
local info = hq.preparedInfo(speaker, asset)
print(info.format, info.duration)

assert(hq.playPrepared(speaker, asset, { volume = 0.6 }))
assert(hq.releasePrepared(speaker, asset))
```

The active playback has its own server asset reference, so releasing the preparation reference after a successful play does not stop that playback.

## Standard CC:T functions

These keep normal CC:T semantics:

### `speaker.playNote(instrument [, volume [, pitch]]) -> boolean`

Play a Minecraft note-block instrument.

### `speaker.playSound(name [, volume [, pitch]]) -> boolean`

Play a Minecraft/modded sound event.

### `speaker.playAudio(audio [, volume]) -> boolean`

Play standard CC:T signed 8-bit 48 kHz audio.

Use native `speaker_audio_empty` for standard `playAudio` pacing.

### `speaker.stop()`

Stop normal CC:T sound/audio and the currently owned HQ continuous output according to the composite speaker contract.

## `hqspeaker` module finite helpers

Load with:

```lua
local hq = require("hqspeaker")
```

### `hq.playFile(speaker, path [, options]) -> boolean`

Recommended one-call path for a ComputerCraft-local finite file.

Current options:

- `volume = number`

Behind the scenes:

```text
CC file
    -> temporary HQ import copy
    -> reusable immutable server MediaAsset
    -> start playback
    -> release temporary preparation ownership
```

M1F changes how that server asset reaches Minecraft clients internally; the Lua call remains the same.

### `hq.prepareFile(speaker, path) -> assetId`

Import/analyze a local file without starting playback. Returns a server media asset ID string and gives the calling ComputerCraft computer one preparation-owner reference.

### `hq.preparedInfo(speaker, assetId) -> table`

Return server-derived media information. Current fields include:

- `format`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `sizeBytes`
- `sourceName`

The server metadata is authoritative for finite duration; client decoder guesses are not.

### `hq.playPrepared(speaker, assetId [, options]) -> boolean`

Start a previously prepared server asset.

### `hq.releasePrepared(speaker, assetId) -> boolean`

Release this computer's preparation reference. Returns false when that computer did not own the preparation.

A currently playing speaker keeps its separate playback reference until stop/end/error.

## Modern finite controls

### `speaker.audioStatus() -> table`

For active/terminal modern prepared finite playback, useful fields include:

- `state`: `playing`, `paused`, `ended`, or `error`
- `kind = "finite"`
- `assetId`
- `generation`
- `format`
- `position`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `volume`
- `looping`
- `totalBytes`
- `canPause`
- `canSeek`
- `canLoop`
- `error` when server playback itself failed

M1F removed the old whole-file transfer, so `transferredBytes` is no longer a modern finite status field.

With no HQ continuous owner, the composite reports the general idle shape:

```lua
{
  state = "idle",
  kind = "none",
  observed = false,
  canPause = false,
  canSeek = false,
  canLoop = false,
}
```

### `speaker.audioPause() -> boolean`

Pause finite playback. The server timeline freezes immediately.

### `speaker.audioResume() -> boolean`

Resume paused finite playback.

### `speaker.audioSeek(seconds) -> boolean`

Change canonical server position.

Non-looping exact-duration seek ends playback. Looping exact-duration seek wraps to the start.

Under M1F the client is given the current server-selected encoded anchor and requests bounded data from there rather than downloading everything before the target.

### `speaker.audioSetVolume(volume) -> boolean`

Change finite playback volume.

### `speaker.audioSetLooping(loop) -> boolean`

Change finite looping.

### `speaker.audioStop()`

Stop the currently owned HQ continuous source.

## HQ finite state event

### `hqspeaker_audio_state`

Authoritative finite server state changes are queued to attached computers:

```lua
while true do
  local _, state = os.pullEvent("hqspeaker_audio_state")
  print(state.state, state.position or 0)
end
```

This is server semantic state, not proof that a particular Minecraft client currently hears sound.

When finite playback is explicitly stopped, the finite server's immediate event uses an idle finite state; after composite ownership clears, a later `audioStatus()` uses the general `kind = "none"` idle shape.

## HQ raw/feed audio

### `speaker.speakPCM(samples [, volume]) -> boolean`

Open-ended HQ signed 16-bit PCM producer feed.

It is not a finite song and does not expose truthful duration/arbitrary seek/natural EOF.

Current modern composite per-call limit: `131072` contiguous samples.

### `speaker.speakMaxSamples() -> number`

Returns `131072` for the current modern composite.

### `hqspeaker_audio_empty`

HQ RAW capacity event. Use this for `speakPCM` retry pacing.

Do not confuse it with native `speaker_audio_empty`, which belongs to standard CC:T `playAudio`.

## Low-level prepared/import functions

Most programs should use the `hqspeaker` module. These remain available because the module itself uses them.

### `speaker.audioMountPath() -> string`

Return this computer's temporary HQ writable import mount.

### `speaker.audioMaxStagedBytes() -> number`

Return the current per-asset staging/import size limit.

### `speaker.audioPrepareStaged(path [, consume]) -> assetId`

Import/analyze a file already copied into that temporary mount and return a reusable server asset ID.

The mount is import plumbing, not a playback library/cache.

### `speaker.audioPreparedInfo(assetId) -> table`

Low-level equivalent of `hq.preparedInfo`.

### `speaker.audioPlayPrepared(assetId [, volume]) -> boolean`

Low-level equivalent of `hq.playPrepared`.

### `speaker.audioReleasePrepared(assetId) -> boolean`

Low-level equivalent of `hq.releasePrepared`.

## Removed prototype API

### `speaker.audioPlayStaged(...)`

This API is **removed as of M1F**.

It was introduced by this project's earlier staged-file prototype and was not part of the inherited HQ Speakers API.

Use:

```text
hq.playFile(...)
```

or:

```text
prepareFile -> playPrepared -> releasePrepared
```

There is no compatibility reason to preserve a second direct-staged playback transport.

## Inherited legacy HQ APIs

Older inherited functions such as `speakMp3`, `speakWav`, `speakOgg`, `speakAudio`, `speakFile`, `speakPacked`, `speakStream`, `speakHLS`, `speakTS`, metadata helpers, and older multi-speaker helpers still exist in parts of the inherited codebase.

They are not the model for the new large local finite-file path.

Project direction:

- large local files use server MediaAssets through the helpers above;
- useful old finite compatibility entrypoints are reviewed later in M1L;
- OGG/AIFF/AU are not final finite-core requirements;
- live MP3/HLS/TS is later M3 work.

## Current implementation caveat

M1F transport is source/test/CI complete, but M1G has not started.

So the modern prepared-file path currently has:

```text
server MediaAsset
-> server timeline
-> client-requested encoded ranges
-> bounded client encoded RAM
```

but it does **not yet** have the final progressive decoder/PCM renderer attached to that RAM window.

Do not expect M1F alone to make prepared MP3/WAV audible. M1G owns that work.
