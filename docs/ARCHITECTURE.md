# Architecture

## Status and authority

This file describes the current architecture and selected near-term direction. Exact current source still wins over documentation. For implementation status and open defects, read `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, and `VERIFIED-FACTS.md` first.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

The source currently implements modern finite protocol **v6**. The selected next protocol direction is an explicit server-authoritative decoder/re-anchor revision, likely protocol v7; that revision is not implemented yet.

## Product model

CC:HQ Speakers is a programmable ComputerCraft speaker peripheral, not a built-in music player. Lua owns application policy such as music/effects/notifications/playlists; Java models technical audio capabilities only.

One physical speaker remains one mono positional source.

The normal `computercraft:speaker` is the product surface. Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain CC:T behavior. HQ RAW uses separate `hqspeaker_audio_empty` pacing.

## Modern finite architecture

```text
ComputerCraft file
-> temporary per-speaker staging mount
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

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

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

Temporary range starvation never becomes canonical EOF.

## Local import and asset ownership

The writable CC mount is temporary import plumbing:

```text
CC file -> temporary staging copy -> reusable server MediaAsset -> playback
```

Recommended helpers are `hq.playFile`, `prepareFile`, `preparedInfo`, `playPrepared`, and `releasePrepared`.

`audioPlayStaged()` was a project prototype and was removed in M1F. Do not restore a second direct-staged playback route.

Known lifecycle debt:

- KI-061: per-speaker staging mounts can leave arbitrary leftover files unreachable after the staging owner is destroyed;
- KI-064: `MediaAssetStore` import can spin indefinitely on repeated zero-byte reads and lacks a non-atomic rename fallback when `ATOMIC_MOVE` is unsupported;
- KI-054: shutdown failure can lose deletion retry state or prevent store close entirely, leaving the media-store root lock alive in the JVM.

## Modern transport

The server keeps the complete encoded asset. Relevant clients request bounded encoded ranges.

Current tuning is 128 KiB max range response and 512 KiB active client encoded window, with bounded outstanding work. These are tuning values, not public API guarantees.

Modern prepared playback has no complete client song `.part/.media` file and no modern CHUNK/END whole-file transfer. Server range reads and client codec work stay off Minecraft game/audio threads.

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

MP3 uses packaged JLayer progressively. Temporary missing range data waits only on a decoder worker and never becomes normal EOF. E1 seek starts from an earlier analyzed seek point and discards pre-target PCM.

`FiniteDecodeAnchorSelector.Anchor` contains exactly two facts: encoded byte offset and anchor time in seconds. There are no hidden frame-index/skip fields being computed and discarded.

WAV layout is normalized server-side and progressively converted to mono S16 without whole-track PCM retention.

`FinitePcmAudioStream` performs no network/disk/codec work. Temporary PCM starvation yields bounded silence rather than terminal EOF.

## Decoder/re-anchor semantics

Current v6 client behavior still has KI-053/KI-056/KI-057:

- ordinary STATE can rewind/reset a slid encoded window while preserving the live decoder;
- expected SEEK cancellation can report as a fatal decoder failure before the worker identity is invalidated;
- STATE conflates a timeline snapshot with decoder-restart intent because restart is inferred from time-derived anchor movement.

The selected replacement model is an explicit server-authoritative decoder/re-anchor revision:

- new media => new generation;
- semantic seek => revision increments;
- ordinary STATE/pause/resume/volume/loop snapshots do not restart a healthy decoder;
- local recovery may rebuild a missing decoder without forcing a server revision change;
- stale worker identity must be invalidated before cancellation can wake/report.

One implementation-shape choice remains: keep PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional latency hints, or remove that duplicate path and let authoritative STATE carry those transitions. Correctness must not depend on CONTROL/STATE ordering either way.

## Volume, attenuation, and range

M1G deliberately uses a **fixed core listening/delivery radius** rather than native CC:T-style volume-dependent range.

Current server relevance already uses 32 blocks. The selected target contract is:

- fixed 32-block M1G core radius unless the owner explicitly changes the number;
- normal positional attenuation inside that radius;
- HQ `volume` changes gain/loudness, not the core radius;
- volume above 1 must not silently enlarge the modern finite attenuation distance;
- future Sound Physics Remastered compatibility owns any intentional range extension/acoustic behavior and matching transport relevance.

KI-058 remains because the active Minecraft channel does not yet explicitly enforce that selected fixed attenuation distance on live volume updates.

## Volume zero

Global HQ volume zero does not pause canonical server time.

Selected behavior:

- keep the server playback/session clock running;
- stop/cancel local decode and renderer work;
- stop requesting encoded ranges while globally muted;
- retain enough client session metadata to accept later STATE;
- on unmute, rebuild from the then-current authoritative position/anchor.

A player's own MASTER/BLOCKS slider is client-local and must not affect server transport. `canStartSilent()` may still be useful for that case and for renderer-start robustness.

## Looping

Looping is deliberately simple in M1G.

At local physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration. A normal restart gap is acceptable.

M1G does **not** include:

- sample-gapless loop boundaries;
- LAME/Xing encoder-delay/padding trimming;
- loop-head prefetch solely to hide the boundary;
- a permanent Minecraft/OpenAL source across iterations;
- SPR-specific loop continuity engineering.

Seek/replacement/stop still supersede stale local replay through generation/revision checks.

## Renderer startup

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, and linear positional attenuation.

KI-060 remains: the client currently latches `rendererStarted=true` before `SoundManager.play(sound)` proves the sound became active. A failed/deferred start must not permanently strand the session silent.

## Underrun and listener lifecycle

A slow client never pauses canonical playback. M1G currently represents temporary PCM starvation as local silence.

Full late-entry discovery, proactive leave cleanup, return/rejoin, dimension/resource-reload recovery, robust general underrun rejoin, and final moving-source lifecycle remain M1H.

### VS2 moving-source note

Modern BEGIN carries initial world position and block coordinates. Modern STATE does **not** carry x/y/z updates. `FiniteSpeakerSound.updatePosition(...)` exists but currently has no modern M1G call site after renderer creation.

M1H therefore needs moving-source work, but a new wire position packet is **not automatically required**. The inherited client already resolves VS2 movement locally from block coordinates each tick. Two valid later approaches remain:

1. mirror that client-side transform logic for modern finite playback using BEGIN's block coordinates;
2. add an explicit authoritative position-update mechanism if later lifecycle/network requirements justify it.

Do not silently choose between them before M1H design work.

## Cross-cutting ownership/threading safety

KI-062 is separate from the decoder protocol but high impact. Dynamic legacy stream calls enter synchronized composite dispatch and may perform blocking DNS while holding the same monitor used by `tickOwnership()`.

The same monitor is also acquired by synchronized `cleanup()`, which provider `forget`, `forgetLevel`, and `clearAll` paths can call during removal/unload/shutdown. A DNS-parked computer thread can therefore block both server tick ownership work and lifecycle cleanup waiting on that composite.

The fix should preserve ownership ordering while moving blocking DNS/I/O outside the shared monitor or otherwise removing server-thread dependence on that monitor.

KI-063 is the other cross-source ownership defect: RAW/prepared replacement currently stops/transfers ownership before all rejection/failure conditions for the replacement are known.

## Multispeaker

Later synchronization uses shared server clocks while each physical speaker retains its own positional renderer. Optional active-session sharing may share encoded/decode work only if it does not collapse physical sources.

Inherited `*All` / `*At` helpers are legacy and are not a model for the later architecture.

## Raw and live sources

Standard `playAudio` remains CC:T signed-8 producer-fed audio. HQ `speakPCM` remains open-ended signed-16 producer-fed audio with bounded backpressure; neither is a finite song.

Internet MP3/HLS/TS remains later M3 work and must keep truthful live/open-ended semantics. The current inherited HLS implementation has a confirmed refreshed-playlist index progression bug; do not mix its repair into M1G.

## Legacy boundary

Inherited byte-taking finite/live APIs and whole-track classes remain for later M1L/M3 migration/removal. They are compatibility/legacy code, not the modern prepared architecture.

Legacy advertised format lists and legacy multispeaker note/sound helpers are not authoritative descriptions of modern prepared capabilities.

## Lifecycle / storage

Cleanup boundaries include peripheral removal, Level unload, server shutdown, computer detach/unmount, range/decoder worker shutdown, client disconnect/resource lifecycle, and bounded temporary encoded/PCM buffers.

`HQSpeakerPeripheralProvider` intentionally uses a Level-keyed weak map only as a fallback: cached values themselves reference their Level, so deterministic lifecycle eviction is the real design. Do not describe that as an accidental WeakHashMap cycle; verify actual unload/removal hooks when evaluating residual cache lifetime.

## VS2 / SPR / non-goals

VS2 remains reflective and optional. Sound Physics Remastered remains later work. Preserve one physical positional source per speaker.

Do not add permanent Java music/effects/notification channels, Java playlists, persistent client song caches, surround rendering from one speaker block, gapless-loop machinery, or automatic application-priority rules.
