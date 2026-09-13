# Lua / ComputerCraft API reference

This is the user-facing programming reference for CC:HQ Speakers.

The mod upgrades the normal ComputerCraft `speaker` peripheral. Lua decides what the audio means — music, alarms, speech, ambience, notifications, soundboards, or anything else. The Java side exposes audio capabilities; it does not provide a playlist/music-player policy layer.

## What to use in new programs

For normal ComputerCraft sounds and DFPWM audio, keep using the standard CC:T speaker functions.

For large local finite files such as MP3/WAV, prefer the bundled `hqspeaker` Lua module:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

For preload/reuse or manual control, use `prepareFile` + `playPrepared` together with the `audio*` finite controls.

Do **not** build new code around `audioPlayStaged`. It was introduced by this project's old staged-file prototype, is not an original HQ Speakers API, and is scheduled for removal when M1F replaces the prototype transfer path.

---

## Standard CC:T speaker functions

These remain the normal ComputerCraft speaker contract and are delegated to CC:T's real speaker implementation.

### `speaker.playNote(instrument [, volume [, pitch]]) -> boolean`

Play a Minecraft note-block instrument using normal CC:T behavior.

### `speaker.playSound(name [, volume [, pitch]]) -> boolean`

Play a Minecraft/modded sound event using normal CC:T behavior.

### `speaker.playAudio(audio [, volume]) -> boolean`

Play normal CC:T signed 8-bit 48 kHz audio. Keep using the normal `speaker_audio_empty` event for CC:T backpressure.

### `speaker.stop()`

Stop the active standard/HQ continuous output according to the composite speaker's ownership rules.

### `speaker_audio_empty`

This is the **native CC:T event** for standard `playAudio` pacing. HQ Speakers deliberately does not replace it with its own fake event.

---

# Recommended finite-file API: `hqspeaker` module

Load the bundled ROM module with:

```lua
local hq = require("hqspeaker")
```

The helper accepts a wrapped HQ-capable normal `speaker` peripheral.

## `hq.playFile(speaker, path [, options]) -> boolean`

The simplest way to play a ComputerCraft-visible finite file.

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

assert(hq.playFile(speaker, "/music/song.mp3", {
    volume = 0.6,
}))
```

What it does behind the scenes:

```text
ComputerCraft file
    -> temporary HQ staging copy
    -> immutable server MediaAsset
    -> start playback from that asset
    -> release the temporary preparation reference
```

The active playback owns its own reference, so releasing the temporary preparation reference does not delete the file while it is playing.

Arguments:

- `speaker`: wrapped normal ComputerCraft speaker with HQ support.
- `path`: ComputerCraft filesystem path.
- `options.volume`: optional numeric volume.

Returns `true` when the prepared playback was accepted, otherwise `false`.

The current server clamps finite volume into its supported range. New Lua programs should still pass sensible non-negative values rather than relying on clamping.

## `hq.prepareFile(speaker, path) -> assetId`

Import and analyze a ComputerCraft-visible file without starting playback.

```lua
local asset = hq.prepareFile(speaker, "/music/song.mp3")
```

Returns a server media asset ID as a string.

Use this when you want to prepare once and decide later when or where to play the asset.

A prepared asset owns a preparation reference. Release it with `hq.releasePrepared` when the Lua program no longer needs that prepared ownership.

## `hq.preparedInfo(speaker, assetId) -> table`

Return server-derived facts for a prepared asset.

```lua
local info = hq.preparedInfo(speaker, asset)
print(info.format)
print(info.duration)
```

Current fields:

- `format`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `sizeBytes`
- `sourceName`

These describe the server asset. Client decoder guesses are not authoritative for finite duration.

## `hq.playPrepared(speaker, assetId [, options]) -> boolean`

Start a previously prepared server asset.

```lua
assert(hq.playPrepared(speaker, asset, {
    volume = 0.6,
}))
```

The playback takes its own asset reference before returning success. This means the preparation reference may be released after playback starts without stopping the active playback.

## `hq.releasePrepared(speaker, assetId) -> boolean`

Release this ComputerCraft computer's prepared ownership of the asset.

```lua
assert(hq.releasePrepared(speaker, asset))
```

Returns `true` when this computer owned and released that preparation reference. It does **not** stop an active playback that already owns a separate playback reference.

A random computer cannot release another computer's prepared ownership merely by knowing the asset ID.

## Preload/reuse example

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

local asset = hq.prepareFile(speaker, "/music/song.mp3")
local info = hq.preparedInfo(speaker, asset)

print(("Prepared %s, %.2f seconds"):format(info.format, info.duration))

assert(hq.playPrepared(speaker, asset, { volume = 0.5 }))

-- The current playback has its own reference now.
assert(hq.releasePrepared(speaker, asset))
```

---

# Finite playback controls

These functions operate on the HQ continuous source currently owned by the speaker. For the modern prepared finite path, the server owns the canonical timeline.

## `speaker.audioStatus() -> table`

Return current HQ audio status.

For an active or terminal modern prepared finite playback, useful fields include:

- `state`: `playing`, `paused`, `ended`, or `error`.
- `kind`: `finite`.
- `assetId`: present for prepared-asset playback.
- `generation`: playback generation.
- `format`
- `position`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `volume`
- `looping`
- `canPause`
- `canSeek`
- `canLoop`
- `error`: present when the server playback itself is in error.

The current M1E bridge also exposes transfer-oriented fields such as `transferredBytes` and `totalBytes`. `transferredBytes` is **transitional**, not a stable long-term API promise: M1F replaces the whole-file transfer model with demand-driven ranges.

When no HQ continuous source owns the speaker, `audioStatus()` reports an idle status with:

- `state = "idle"`
- `kind = "none"`
- `observed = false`
- `canPause = false`
- `canSeek = false`
- `canLoop = false`

For HQ RAW ownership, the status uses `kind = "raw"` and does not claim finite pause/seek/loop capabilities.

Legacy finite/live ownership may still return the inherited legacy status shape until those paths are migrated in their later milestones.

## `speaker.audioPause() -> boolean`

Pause the current finite playback if supported.

The canonical server position freezes immediately. A client renderer is not allowed to become the playback clock.

## `speaker.audioResume() -> boolean`

Resume a paused finite playback if supported.

## `speaker.audioSeek(seconds) -> boolean`

Seek the current finite playback.

The server changes the canonical finite position immediately. In the final streamed design the client will discard irrelevant buffered data and request new encoded data around the new server position.

For a non-looping finite playback, seeking exactly to duration ends it. With looping enabled, exact-duration seek wraps to the beginning.

## `speaker.audioSetVolume(volume) -> boolean`

Change the current finite playback volume.

## `speaker.audioSetLooping(loop) -> boolean`

Enable or disable looping for the current finite playback.

## `speaker.audioStop()`

Stop the currently owned HQ continuous source.

The standard `speaker.stop()` is still available and also requests CC:T's native speaker stop behavior.

---

# HQ raw/feed audio

## `speaker.speakPCM(samples [, volume]) -> boolean`

Feed signed 16-bit PCM samples to the HQ raw path.

This is an **open-ended producer feed**, not a finite song API. It does not truthfully have finite duration, arbitrary seek, or natural EOF.

The modern composite limits one accepted call to 131072 contiguous samples and applies bounded server-side backpressure.

When a valid call is rejected because HQ RAW capacity is full, retry after `hqspeaker_audio_empty`.

## `speaker.speakMaxSamples() -> number`

Returns the current modern composite per-call HQ RAW sample maximum: `131072`.

## `hqspeaker_audio_empty`

HQ RAW backpressure event. This is separate from CC:T's native `speaker_audio_empty` event.

Use:

- `speaker_audio_empty` for standard CC:T `playAudio`;
- `hqspeaker_audio_empty` for HQ `speakPCM` retry pacing.

---

# HQ finite state event

## `hqspeaker_audio_state`

The modern finite server queues this event to attached computers when authoritative finite state changes.

Typical use:

```lua
while true do
    local _, state = os.pullEvent("hqspeaker_audio_state")
    print(state.state, state.position or 0)
end
```

During active/terminal modern finite playback, the event carries the same server-owned finite status fields described above.

One current implementation detail is worth documenting precisely: when a finite playback is explicitly stopped, the finite server queues an idle event with `kind = "finite"`; after composite ownership is cleared, a later `speaker.audioStatus()` call reports the general no-owner idle shape with `kind = "none"`.

Do not infer client audibility from this event. It represents canonical server playback state, not whether one particular Minecraft client has decoded or rendered sound successfully.

---

# Low-level prepared-media peripheral functions

Most programs should use the `hqspeaker` module instead. These functions are documented because the module itself uses them and advanced programs may deliberately work at this level.

## `speaker.audioMountPath() -> string`

Return this computer's temporary writable HQ staging mount path.

The staging mount is only an **import mechanism**. It is not the final media library and should not be treated as persistent playback storage.

## `speaker.audioMaxStagedBytes() -> number`

Return the current maximum size accepted by the temporary staging/import path for one asset.

## `speaker.audioPrepareStaged(path [, consume]) -> assetId`

Import and analyze one file already copied into the HQ staging mount.

- `path` is relative to the HQ staging mount.
- `consume` defaults to `true` through the Lua helper and means the temporary staged copy should be removed after successful import.

Returns a reusable server asset ID.

Prefer `hq.prepareFile()` unless you specifically need to manage the staging copy yourself.

## `speaker.audioPreparedInfo(assetId) -> table`

Low-level equivalent used by `hq.preparedInfo()`.

## `speaker.audioPlayPrepared(assetId [, volume]) -> boolean`

Low-level equivalent used by `hq.playPrepared()`.

## `speaker.audioReleasePrepared(assetId) -> boolean`

Low-level equivalent used by `hq.releasePrepared()`.

---

# Prototype API scheduled for removal

## `speaker.audioPlayStaged(...)`

**Do not use in new programs.**

This command did not come from the original HQ Speakers mod. This project introduced it during the old staged/local-file prototype so a temporary uploaded file could be played directly.

That model has since been superseded by:

```text
stage/import -> server MediaAsset -> playPrepared
```

Project decision as of 2026-09-13: when M1F implementation begins, remove `audioPlayStaged()` rather than carrying this prototype path into the new range-streaming transport.

Use `hq.playFile()` for the normal one-call experience, or `hq.prepareFile()` + `hq.playPrepared()` for preload/reuse.

---

# Inherited legacy HQ APIs

The repository still contains older HQ Speakers finite/live functions such as `speakMp3`, `speakWav`, `speakOgg`, `speakAudio`, `speakFile`, `speakPacked`, `speakStream`, `speakHLS`, `speakTS`, older multispeaker helpers, metadata helpers, and related size/status functions.

They are **not the model for the new large local finite-file engine**.

Important project direction:

- useful legacy compatibility frontends are reviewed/migrated later in M1L;
- large local files should use the prepared/server-asset path;
- OGG/AIFF/AU are not final finite-core requirements;
- live MP3/HLS/TS is later M3 work;
- do not infer the final supported-format promise from old inherited helper names.

This reference intentionally does not present those inherited surfaces as the recommended modern API while their migration/final status is unresolved.

---

# Current milestone caveat

As of the pre-M1F documentation checkpoint:

- M1E server-authority source/tests/CI are implemented;
- the final manual M1E Minecraft PASS was **not performed** because the project owner chose to skip that manual test and move forward later;
- therefore M1E must not be described as runtime-verified;
- M1F implementation has **not started**;
- the current prepared client still contains the temporary whole-file transfer/decoder bridge;
- M1F will replace transport;
- M1G will provide the final progressive MP3/common-WAV decoder and audible renderer.

The public Lua workflow (`playFile`, prepare/play/release, finite controls) is the intended programming shape even though the internal finite transport underneath it is still being replaced.