# Lua / ComputerCraft API reference

Updated: 2026-09-19

This is the current user-facing programming reference for CC:HQ Speakers.

The mod upgrades the normal ComputerCraft `speaker`. Lua decides what the audio means; Java exposes technical audio capabilities and playback controls.

M1G, post-M1G hardening, and M1H source work are implemented. M1J modern finite multispeaker is active; its first source/test/CI/package checkpoint is `b557773b9c6f6b8029aec132a1706f0d8da914bd`. Exact current source still wins over this reference if behavior and documentation disagree.

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

### `hq.preparedFormats(speaker) -> table`

Return the truthful modern prepared-format capability set. Current result is equivalent to:

```lua
{ mp3 = true, wav = true }
```

This is deliberately separate from inherited `speakSupportedFiles()`, whose broader result describes legacy compatibility paths and must not be interpreted as the modern prepared engine contract.

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

Post-M1G hardening resolved KI-063: failed/rejected prepared replacement is admitted before destructive ownership transfer, so the previous valid HQ source is preserved on normal admission failure.

### `hq.playPreparedAll(speaker, assetId [, options]) -> boolean`

Start one shared prepared playback on the speakers currently attached to the calling ComputerCraft computer. The speaker set is a start-time snapshot.

All endpoints share one canonical play/pause/seek/loop timeline, but each remains a separate positional source with its own listeners, renderer/recovery, configured volume, and mute state. There is no expected-member barrier.

### `hq.playFileAll(speaker, path [, options]) -> boolean`

Convenience form of prepare -> shared multispeaker play -> release preparation ownership.

### `hq.releasePrepared(speaker, assetId) -> boolean`

Release this computer's preparation reference. Returns false when that computer did not own the preparation. Active playback retains its own reference until stop/end/error.

## Modern finite controls

### `speaker.audioStatus() -> table`

Useful modern prepared fields include:

- `state`: `playing`, `paused`, `ended`, or `error`
- `kind = "finite"`
- `assetId`
- `generation`
- `playbackId`
- `stateRevision`
- `format`
- `position`
- `duration`
- `sampleRate`
- `channels`
- `bitsPerSample`
- `volume` (configured endpoint gain)
- `muted` (endpoint mute flag)
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

Protocol v7 uses an explicit server-authoritative decoder/re-anchor revision. Semantic seek increments that revision, making fresh codec state self-describing; ordinary state snapshots keep the revision and do not restart a healthy decoder. Nonterminal CONTROL projection was removed, so seek correctness does not depend on packet ordering.

### `speaker.audioSetVolume(volume) -> boolean`

Change finite playback volume.

Logical volume range remains `0..3`.

Selected M1G modern-finite policy:

- core delivery/listening radius remains fixed at 32 blocks;
- volume changes loudness/gain, not the core radius;
- distance attenuation still makes sound quieter with distance inside that range;
- future Sound Physics Remastered compatibility owns any intentional extended range/acoustics and matching transport relevance.

The live modern-finite channel explicitly enforces the fixed 32-block attenuation distance.

For **global HQ volume exactly zero**, canonical server time keeps running while clients hibernate decode/render/range work. Server range delivery is suppressed while muted, and non-zero volume rebuilds/rejoins current authoritative time.

A player's personal Minecraft MASTER/BLOCKS slider is client-local and does not change server transport policy.

### `speaker.audioSetLooping(loop) -> boolean`

Change canonical server looping.

Looping means normal “play the same thing again.” At local physical EOF, while authoritative state still says `looping=true`, the client starts a fresh decoder/render iteration and catches up to the current canonical loop position. A normal restart gap is acceptable.

M1G does **not** promise sample-gapless MP3 looping, LAME/Xing encoder-delay/padding trimming, loop-head prefetch, or a permanent OpenAL source across loop iterations.

### Endpoint mute and multispeaker controls

For modern prepared playback, pause/resume/seek/loop operate on the shared playback. Volume and mute operate on the physical endpoint.

- `speaker.audioSetMuted(boolean) -> boolean`
- `speaker.audioSetMutedAll(boolean) -> boolean`
- `speaker.audioSetMutedAt(index, boolean) -> boolean`
- `hq.mute/unmute`, `hq.muteAll/unmuteAll`, and `hq.muteAt/unmuteAt`
- modern `audioPauseAll/audioResumeAll/audioSeekAll/audioSetLoopingAll/audioStopAll` address the shared playback;
- `audioSetVolumeAll` applies volume to all selected endpoints;
- `audioSetVolumeAt` changes only the indexed endpoint;
- indexed pause/resume/seek/loop still address the shared timeline;
- indexed stop detaches only that endpoint.

Muting preserves the endpoint's configured volume. Unmuting rejoins the current shared playback position rather than restarting from where it was muted.

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

Post-M1G hardening also resolves the RAW side of KI-063: the PCM table/volume are validated before replacement stops the previous source.

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
- `speaker.audioPlayPreparedAll(assetId [, volume]) -> boolean`
- `speaker.audioReleasePrepared(assetId) -> boolean`

The writable mount is import plumbing, not a persistent playback library/cache.

Whole-owner staging cleanup now removes arbitrary/interrupted leftovers after unmount/release. The low-level mount is still temporary import plumbing and should not be treated as permanent storage.

## Removed prototype API

### `speaker.audioPlayStaged(...)`

Removed as of M1F. Use `hq.playFile(...)` or prepare -> play -> release.

## Inherited legacy HQ APIs

Older functions such as `speakMp3`, `speakWav`, `speakOgg`, `speakAudio`, `speakFile`, `speakPacked`, `speakStream`, `speakHLS`, `speakTS`, metadata helpers, and older multi-speaker helpers still exist in inherited code.

They are not the model for modern large local finite-file playback. Legacy finite compatibility will be reviewed during engine/API convergence. Direct radio/ICY/HLS/TS is not a core roadmap requirement.

Important inherited caveats:

- legacy capability lists advertise formats broader than modern prepared support and should not be used as the modern contract;
- composite `playNoteAll`/`playSoundAll`/`playAudioAll` and indexed variants are now intercepted and delegated to each real CC:T speaker, so requested instruments, sound IDs, and native DFPWM/backpressure semantics are preserved; obsolete legacy synthesis implementations remain only as compatibility/dead code pending cleanup;
- inherited live HLS has a confirmed refreshed-playlist index progression bug;
- KI-062 is resolved: blocking DNS may still occupy the calling ComputerCraft command, but it no longer holds the ownership monitor needed by server tick/lifecycle cleanup.

## Positional / moving-source note

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry live x/y/z updates.

M1H now updates the modern finite positional source locally from Sable/Aeronautics-style sublevel projection, VS2 transforms, or normal block center. The server uses the same resolved position for listener relevance. Late entry/leave/re-entry membership is also implemented.

Focused moving-source/listener Minecraft checks remain in the runtime backlog.

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

M1G is complete and has a recorded focused audible/core Minecraft PASS on NeoForge 21.1.247. Current source uses protocol v8 for M1J; green CI/package evidence is not a substitute for the still-unrecorded focused Minecraft multispeaker acceptance.

See `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, and `TESTING.md` for the current engineering boundary.

## Modernized legacy-name MP3/WAV calls

`speakMp3`, `speakWav`, `speakMp3All`, `speakWavAll`, `speakMp3At`, and `speakWavAt` now use the modern MediaAsset + progressive finite engine. The names remain for Lua compatibility, but they no longer use the inherited whole-file finite decoder or expected-member multispeaker barrier.

These wrappers are intentionally strict: `speakMp3` expects modern-supported MP3 data and `speakWav` expects modern-supported common WAV data. OGG and generic packed-file aliases remain legacy for now.

