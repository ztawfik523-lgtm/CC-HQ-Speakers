# Architecture

## Product model

CC:HQ Speakers is a programmable ComputerCraft speaker peripheral, not a built-in music player. Lua owns application policy such as music/effects/notifications/playlists; Java models technical audio capabilities only.

One physical speaker remains one mono positional source.

The normal `computercraft:speaker` is the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain CC:T behavior. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Finite architecture

```text
ComputerCraft file
-> immutable server MediaAsset
-> server-authoritative playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client-requested encoded ranges
-> fixed-size sliding encoded window
-> progressive decoder worker
-> bounded mono S16 PCM
-> Minecraft AudioStream / positional BLOCKS renderer
```

A MediaAsset owns encoded bytes and server-derived facts. A playback owns generation/state/time/seek/loop/volume. A renderer is client-local and never becomes canonical timeline authority.

## Modern finite support

Current modern prepared/local playback is deliberately narrow:

- MP3 / MPEG Layer III;
- common WAV U8/S16/S24/S32/F32, mono/stereo;
- classic RIFF/WAVE plus the selected narrow PCM/float WAVEX subset;
- native FLAC only if later M1I proof succeeds.

Historical OGG/AIFF/AU analysis or inherited APIs do not define the modern prepared support surface.

Finite output is mono signed 16-bit PCM at source sample rate. Stereo is downmixed; more-than-stereo input is rejected.

## Server authority

The server owns PLAYING/PAUSED/ENDED/ERROR, position/duration, seek, loop, volume, generation, and later sync state.

Successful play starts canonical time immediately. It does not wait for transfer, decoder readiness, or audibility. Client failures are local diagnostics, not canonical clock/EOF authority.

## Local import and asset ownership

The writable CC mount is temporary import plumbing:

```text
CC file -> temporary staging copy -> reusable server MediaAsset -> playback
```

Recommended helpers are `hq.playFile`, `prepareFile`, `preparedInfo`, `playPrepared`, and `releasePrepared`.

`audioPlayStaged()` was a project prototype and was removed in M1F. Do not restore a second direct-staged playback route.

## Modern transport

The server keeps the complete encoded asset. Relevant clients request bounded encoded ranges.

Current tuning is 128 KiB max range response and 512 KiB active client encoded window, with bounded outstanding work. These are tuning values, not public API guarantees.

Modern prepared playback has no complete client song `.part/.media` file and no modern CHUNK/END whole-file transfer. Server reads and client codec work stay off Minecraft game/audio threads.

## Integrated M1G decoder/render path

Current source wires:

```text
FiniteRangeWindow
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder / ProgressiveMp3Decoder
-> FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

MP3 uses packaged JLayer progressively. Temporary missing range data waits only on a decoder worker and never becomes normal EOF. E1 seek starts from an earlier analyzed frame and discards pre-target PCM.

WAV layout is normalized server-side and progressively converted to mono S16 without whole-track PCM retention.

`FinitePcmAudioStream` performs no network/disk/codec work. Temporary PCM starvation yields bounded silence rather than terminal EOF. `FiniteSpeakerSound` uses normal Minecraft positional attenuation. Pause/resume/volume use the channel-control path; seek/replacement/stop invalidate stale local decoder/PCM/renderer state.

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Seeking

Semantic seek changes canonical server position, invalidates old local decode/render state, receives a codec-aware anchor, then re-fetches/re-decodes from bounded data. It must recreate codec state even when the selected coarse encoded anchor byte is unchanged.

The 2026-09-14 audit found KI-053: an ordinary same-anchor STATE can currently reset a slid encoded window without restarting the existing decoder epoch. That can rewind useful range state behind the decoder cursor. It is documented but not fixed.

## Looping

The server already owns canonical loop time. Audible client wrap rejoin is not finalized. Remaining choices are L1 client EOF refresh, L2 proactive server wrap STATE, or L3 client local modulo/restart. See `CURRENT-STATE.md`; do not silently choose one.

## Underrun and listener lifecycle

A slow client never pauses canonical playback. M1G currently represents temporary PCM starvation as local silence.

Full late-entry discovery, proactive leave cleanup, return/rejoin, dimension/resource-reload recovery, robust general underrun rejoin, and final VS2 lifecycle remain M1H.

## Multispeaker

Later synchronization uses shared server clocks while each physical speaker retains its own positional renderer. Optional active-session sharing may share encoded/decode work only if it does not collapse physical sources.

## Raw and live sources

Standard `playAudio` remains CC:T signed-8 producer-fed audio. HQ `speakPCM` remains open-ended signed-16 producer-fed audio with bounded backpressure; neither is a finite song.

Internet MP3/HLS/TS remains later M3 work and must keep truthful live/open-ended semantics.

## Legacy boundary

Inherited byte-taking finite/live APIs and whole-track classes remain for later M1L/M3 migration/removal. They are compatibility/legacy code, not the modern prepared architecture.

## Lifecycle / storage

Cleanup boundaries include peripheral removal, Level unload, server shutdown, computer detach/unmount, range/decoder worker shutdown, client disconnect/resource lifecycle, and bounded temporary encoded/PCM buffers.

The 2026-09-14 audit found KI-054: `MediaAssetStore.close()` does not retain failed completed-file shutdown deletions for another `close()` retry. Next-start orphan pruning normally recovers those managed files. This is documented but not fixed.

## VS2 / SPR / non-goals

VS2 remains reflective and optional. Sound Physics Remastered remains later work. Preserve one physical positional source per speaker.

Do not add permanent Java music/effects/notification channels, Java playlists, persistent client song caches, surround rendering from one speaker block, or automatic application-priority rules.
