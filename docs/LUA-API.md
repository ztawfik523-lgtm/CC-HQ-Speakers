# Lua / ComputerCraft API reference

Updated: 2026-09-17

This is the current user-facing programming reference for CC:HQ Speakers.

The mod upgrades the normal ComputerCraft `speaker`. Lua decides what the audio means; Java exposes technical audio capabilities and playback controls.

Where this file distinguishes **current source** from **selected M1G target behavior**, current source wins until the selected source work is implemented and tested.

## Recommended starting point

For normal Minecraft sounds and standard DFPWM audio, use the normal CC:T speaker API.

For a local finite MP3 or supported common WAV file:

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

An accepted active playback has its own server asset reference, so releasing the preparation reference after a successful play does not stop that playback.

## Standard CC:T functions

These keep normal CC:T semantics:

### `speaker.playNote(instrument [, volume [, pitch]]) -> boolean`

Play a Minecraft note-block instrument.

### `speaker.playSound(name [, volume [, pitch]]) -> boolean`

Play a Minecraft/modded sound event.

### `speaker.playAudio(audio [, volume]) -> boolean`

Play standard CC:T signed 8-bit 48 kHz audio. Use native `speaker_audio_empty` for pacing.

### `speaker.stop()`

Stop normal CC:T sound/audio and the currently owned HQ continuous output according to the composite speaker contract.

## `hqspeaker` finite helpers

Load with:

```lua
local hq = require("hqspeaker")
```

### `hq.playFile(speaker, path [, options]) -> boolean`

Recommended one-call path for a ComputerCraft-local finite file.

Current modern prepared formats are:

- MP3 / MPEG Layer III;
- supported common WAV: mono/stereo U8, S16, S24, S32, or F32, including the selected narrow PCM/float WAVEX subset.

Historical inherited OGG/AIFF/AU helpers do not define the modern prepared-file support surface.

Current option:

- `volume = number`

Behind the scenes:

```text
CC file
-> temporary HQ import copy
-> reusable immutable server MediaAsset
-> modern prepared playback
-> release temporary preparation ownership
```

The client requests bounded encoded ranges and progressively decodes them; it does not download a complete song file first.

### `hq.prepareFile(speaker, path) -> assetId`

Import/analyze a local file without starting playback. Returns a server media asset ID and gives the calling ComputerCraft computer one preparation-owner reference.

### `hq.preparedInfo(speaker, assetId) -> table`

Return server-derived media information. Current fields include:

- `format`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `sizeBytes`
- `sourceName`

The server metadata is authoritative for finite duration.

### `hq.playPrepared(speaker, assetId [, options]) -> boolean`

Start a previously prepared server asset. A successful play creates a server-owned finite generation and separate playback asset reference.

**Current implementation caveat:** KI-063 means a failed/rejected prepared replacement can currently stop the previous valid HQ source before the replacement is known to be accepted. That is a source bug, not intended API semantics.

### `hq.releasePrepared(speaker, assetId) -> boolean`

Release this computer's preparation reference. Returns false when that computer did not own the preparation. Active playback retains its own reference until stop/end/error.

## Modern finite controls

### `speaker.audioStatus() -> table`

Useful modern prepared fields include:

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

`transferredBytes` is not a modern finite status field; M1F replaced whole-file transfer with bounded demand-driven ranges.

With no HQ continuous owner, the composite reports an idle shape with `kind = "none"` and all finite capability flags false.

### `speaker.audioPause() -> boolean`

Pause canonical finite playback. Server position freezes immediately.

### `speaker.audioResume() -> boolean`

Resume paused finite playback.

### `speaker.audioSeek(seconds) -> boolean`

Change canonical server position. Non-looping exact-duration seek ends playback. Looping exact-duration seek wraps the server timeline to the start.

Current v6 client code discards/restarts local decoder state using CONTROL/STATE coordination and codec-aware anchors. KI-053/KI-056/KI-057 document correctness problems in that coordination.

Selected target behavior is an explicit server-authoritative decoder/re-anchor revision so semantic seek is self-describing and ordinary state snapshots do not restart healthy decode state. This is not implemented yet.

### `speaker.audioSetVolume(volume) -> boolean`

Change finite playback volume.

Logical volume range remains `0..3`.

Selected M1G modern-finite policy:

- core delivery/listening radius remains fixed at 32 blocks;
- volume changes loudness/gain, not the core radius;
- distance attenuation still makes sound quieter with distance inside that range;
- future Sound Physics Remastered compatibility owns any intentional extended range/acoustics and matching transport relevance.

Current source still needs KI-058 to explicitly enforce that fixed attenuation distance on the live channel.

For **global HQ volume exactly zero**, selected target behavior is to keep canonical server time running while the client hibernates decode/render/range work, then rebuild/rejoin current authoritative time on unmute. That source work is not implemented yet.

A player's personal Minecraft MASTER/BLOCKS slider is client-local and does not change server transport policy.

### `speaker.audioSetLooping(loop) -> boolean`

Change canonical server looping.

The loop policy is settled: looping means normal “play the same thing again.” At local physical EOF, while authoritative state still says `looping=true`, the client should start a fresh decoder/render iteration from the beginning. A normal restart gap is acceptable.

**Current M1G caveat:** the server timeline already wraps, but ordinary local replay is not implemented yet. Do not interpret wrapped `audioStatus().position` as proof that the Minecraft client audibly restarted.

M1G does **not** promise sample-gapless MP3 looping, LAME/Xing encoder-delay/padding trimming, loop-head prefetch, or a permanent OpenAL source across loop iterations.

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

## HQ raw/feed audio

### `speaker.speakPCM(samples [, volume]) -> boolean`

Open-ended HQ signed 16-bit PCM producer feed. It is not a finite song and does not expose truthful duration/arbitrary seek/natural EOF.

Current composite per-call limit: `131072` contiguous samples.

**Current implementation caveat:** KI-063 also affects failed RAW replacement admission: a rejected replacement can currently stop a previous valid HQ source before capacity failure is returned. That is not intended final behavior.

### `speaker.speakMaxSamples() -> number`

Returns `131072`.

### `hqspeaker_audio_empty`

HQ RAW capacity event. Do not confuse it with native `speaker_audio_empty`, which belongs to standard CC:T `playAudio`.

## Low-level prepared/import functions

Most programs should use the `hqspeaker` module. These remain available because the module itself uses them.

- `speaker.audioMountPath() -> string`
- `speaker.audioMaxStagedBytes() -> number`
- `speaker.audioPrepareStaged(path [, consume]) -> assetId`
- `speaker.audioPreparedInfo(assetId) -> table`
- `speaker.audioPlayPrepared(assetId [, volume]) -> boolean`
- `speaker.audioReleasePrepared(assetId) -> boolean`

The writable mount is import plumbing, not a persistent playback library/cache.

KI-061: arbitrary/interrupted files left in the per-speaker staging mount can currently become unreachable after whole staging-owner cleanup/recreation. High-level successful `hq.playFile()` normally consumes/deletes its temporary staged copy, but the low-level mount should not be treated as permanent storage.

## Removed prototype API

### `speaker.audioPlayStaged(...)`

Removed as of M1F. Use `hq.playFile(...)` or prepare -> play -> release.

## Inherited legacy HQ APIs

Older functions such as `speakMp3`, `speakWav`, `speakOgg`, `speakAudio`, `speakFile`, `speakPacked`, `speakStream`, `speakHLS`, `speakTS`, metadata helpers, and older multi-speaker helpers still exist in inherited code.

They are not the model for modern large local finite-file playback. Legacy finite compatibility is reviewed later in M1L; live MP3/HLS/TS is later M3 work.

Important inherited caveats:

- legacy capability lists advertise formats broader than modern prepared support and should not be used as the modern contract;
- legacy `playNoteAll`/`playSoundAll` do not preserve requested normal note/sound semantics;
- inherited live HLS has a confirmed refreshed-playlist index progression bug;
- KI-062 affects dynamic legacy stream starts: blocking DNS can occur while a shared composite monitor is held, potentially stalling server tick/lifecycle cleanup waiting on that composite.

## Positional / VS2 note

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry live x/y/z updates.

The current modern finite client does not update `FiniteSpeakerSound` position after renderer creation, so moving-source/VS2 lifecycle remains M1H.

A new network position packet is not automatically required: the inherited client already recalculates VS2 world position from block coordinates each tick. M1H may mirror that path for modern finite playback or choose explicit position updates later.

## Current implementation/evidence caveat

The modern prepared path **does** have progressive decoding and positional rendering integrated in source:

```text
server MediaAsset
-> server-authoritative timeline
-> bounded encoded ranges
-> bounded sliding client encoded RAM
-> progressive MP3/common-WAV decode
-> bounded mono S16 PCM
-> Minecraft AudioStream / positional BLOCKS renderer
```

However, focused audible Minecraft M1G acceptance is not recorded, real-MP3 progressive integration coverage is incomplete, ordinary replay is not implemented, and KI-053/KI-056/KI-057 remain open in the current v6 coordination model.

See `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, and `TESTING.md` for the current engineering boundary.
