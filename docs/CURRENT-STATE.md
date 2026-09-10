# Current state

## Repository and baseline

- Repository: `ztawfik523-lgtm/CC-HQ-Speakers`
- Immediate parent: `jvrcruzGAMES/CC-HQ-Speakers`
- Original lineage: `tiktop101/CC-HQ-Speakers`
- Untouched fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`

The inherited NeoForge port targeted Minecraft 1.21.1 and Java 21, but the untouched baseline used:

- NeoForge `21.1.211`
- CC:Tweaked `1.113.1`

Project targets:

- Minecraft `1.21.1`
- Java `21`
- CC:Tweaked `1.120.0`
- NeoForge `21.1.247` baseline
- NeoForge `21.1.248` compatibility
- Sound Physics Remastered `1.21.1-1.5.1`

M0 now builds with CC:T `1.120.0` and defaults to NeoForge `21.1.247`.
`-PneoForgeVersion=21.1.248` selects the compatibility target. Clean builds and
dedicated-server startup through the `Done` marker succeeded on both versions.
The consolidated client smoke run on NeoForge `21.1.247` confirmed mod loading,
the HQ mixin and Lua surface, both PCM conventions, volume changes, finite MP3
playback, and clean world shutdown. The run used the exact target stack in a
small test instance rather than the complete ATM10 modpack.

## What the inherited mod already has

Existing code includes:

- `HQSpeakerPeripheral`
- `HQSpeakerGroupPeripheral`
- `HQSpeakerClientHandler`
- `HQAudioStream`
- `StreamingAudioSource`
- `SharedStreamingGroup`
- `HLSPlaylistParser`
- `TSDemuxer`
- networking, mixins, and VS2 integration

Existing product features include:

- PCM
- WAV / generic audio files
- OGG Vorbis
- MP3
- MP3 streams
- HLS
- MPEG-TS
- ICY metadata
- volume
- stop
- multi-speaker/group methods
- a nominal looping toggle
- some status/query methods

## Source-audited product problems

### 1. Finite media is hard-capped to 8 MiB in multiple places

`HQSpeakerPeripheral` defines:

`SPEAKER_MAX_AUDIO = 8 * 1024 * 1024`

and rejects larger `speakWav` / file payloads.

`HQSpeakerAudioPacket` independently defines:

`MAX_BYTES = 8 * 1024 * 1024`

and finite packets carry the entire encoded file as one `byte[]`.

This is not just a UI constant. The current finite-media transport is one-shot, whole-file packet delivery, so large-media work must address both API limits and transport/resource behavior.

### 2. Current looping is not a real playback feature

`HQSpeakerPeripheral.setLooping(boolean)` only changes a server-side `looping` field.

The current `HQSpeakerAudioPacket` has no looping field.

`HQSpeakerClientHandler.HQSpeakerSound` explicitly sets:

`this.looping = false`

Therefore the inherited `setLooping` surface is not a complete end-to-end loop implementation.

This is a concrete correctness gap, not merely poor API naming.

### 3. `speakIsPlaying()` is not renderer playback truth

The inherited method returns:

`!speakerQueue.isEmpty() || streamActive.get()`

For finite file playback, the server queue may become empty after the packet is dispatched while the client is still decoding/playing.

So this method cannot be treated as an accurate player-state primitive for finite media.

### 4. No real pause/resume/seek player path exists

The inherited peripheral exposes stop/volume/loop-related methods, but no coherent finite-media pause/resume/seek/position/duration control path exists.

M1 must introduce truthful playback state instead of bolting more boolean flags onto the current queue.

### 5. Finite decode is already off the Minecraft sound read thread, but still whole-file

`HQAudioStream` owns a single-thread decoder executor named `HQSpeaker-Decoder`.

Finite OGG decoding uses `STBVorbis.stb_vorbis_decode_memory`, producing the complete decoded PCM allocation.

Other JavaSound-supported finite formats use an `AudioInputStream` conversion followed by `readAllBytes()`.

Decoded finite PCM is limited to `64 MiB`.

This means the fork already avoids doing the main finite decode synchronously inside `AudioStream.read()`, which is good, but large finite media still materializes whole decoded tracks.

### 6. Current stream read can synthesize silence while waiting

`HQAudioStream.read()` can return a small direct silence buffer when data is not ready yet.

This behavior should be audited carefully because the distinction between:
- prebuffering,
- underrun,
- natural EOF,
- decoder failure,
- and permanent drain

matters for reliable player state and repeat behavior.

## M0 bootstrap status

Completed automatically:

- exact dependency update and dual NeoForge build validation;
- `.gitignore` plus removal of generated/local state from the Git index;
- packaged JAR/resource/jar-in-jar checks;
- dedicated-server classloading/startup on both target versions;
- CI matrix for both target versions;
- source-surface preservation audit;
- one guided Lua smoke-test package.

The single M0 client run is complete. OGG, URL streaming, multi-speaker/sync,
and resource-reload cleanup were skipped; stop, WAV, generic finite playback,
and inherited looping were inconclusive because the initial helper allowed the
long MP3 to remain active. These are recorded coverage gaps, not runtime passes.

## Product priorities

1. proper player controls;
2. reliable loop/repeat;
3. larger media with bounded resource behavior;
4. productized SPR integration;
5. compatibility with existing CC:HQ Lua methods where practical.

## M1 player-core candidate

The M1 branch now implements finite-track generations, retained decoded mono
PCM, real EOF, renderer-confirmed state, pause/resume, live volume, cursor-based
looping, decoded duration, a monotonic playback timeline, and seek by
renderer re-prime. The precise Lua surface is:

- `audioStatus()`
- `audioPause()` / `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

`speakVolume` remains the default volume setter. `setLooping` now updates both
the default finite setting and the active finite track. Raw `playAudio` and
`speakPCM` remain feed APIs; live streams retain their inherited semantics.

Clean builds and five cursor/seek/loop unit tests pass on NeoForge 21.1.247 and
21.1.248. A 21.1.247 dedicated development server loaded CC:T 1.120.0, loaded
HQ Speakers, registered all five payloads, and reached the `Done` marker without
client-class leakage. Real-client acceptance is still pending the single
consolidated procedure in `M1-RUNTIME-TEST.md`.

## SPR status

SPR work is not starting from zero.

`ztawfik523-lgtm/cchq-soundphysics-compat` already contains a frozen V7.1 acoustic baseline and later lifecycle/performance/release-hardening work.

Treat that as existing project evidence and reusable implementation work, not a research task to restart.

## What this project is not

This is not HighAudio 2.

Do not transplant HighAudio's custom ContentId, upload, transfer, server-session, or GenericSource architecture unless a concrete defect in the CC:HQ fork makes one of those ideas directly useful.
