# Architecture

## Status and authority

This file describes the current architecture and selected near-term direction. Exact current source still wins over documentation. For implementation status and open defects, read `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, and `VERIFIED-FACTS.md` first.

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`, CI `35297026277`. Post-M1G hardening source checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`, latest full verification CI `35406595856`.

The source implements modern finite protocol **v7** with an explicit server-authoritative decoder/re-anchor revision.

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
-> protocol v7 descriptor + STATE decodeRevision + codec-aware anchor
-> bounded client-requested encoded ranges
-> fixed-size sliding encoded window
-> progressive decoder worker
-> bounded mono S16 PCM
-> Minecraft AudioStream / positional BLOCKS renderer
```

A MediaAsset owns encoded bytes and server-derived facts. Current protocol-v7 source still has one finite server session per physical speaker. M1J is refactoring the canonical playback facts into one shared authority so one-speaker and multispeaker playback use the same model.

The selected M1J boundary is:

```text
shared playback authority
  media/playback identity
  canonical clock + play/pause/seek/loop
  shared state/decode revisions
  playback asset lifetime
        |
        +--> physical speaker endpoint A -> listeners/transport/renderer/recovery
        +--> physical speaker endpoint B -> listeners/transport/renderer/recovery
        +--> physical speaker endpoint C -> listeners/transport/renderer/recovery
```

A renderer remains client-local and never becomes canonical timeline authority. Endpoint loss/removal is not a shared playback failure.

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

The server owns canonical playback identity, PLAYING/PAUSED/ENDED/shared-ERROR, position/duration, seek, loop, and codec re-anchor state. M1J separates endpoint gain and endpoint-local failure from the shared canonical timeline.

Successful play starts canonical time immediately. It does not wait for transfer, decoder readiness, or audibility. Client failures are local diagnostics, not canonical clock/EOF authority.

Temporary range starvation never becomes canonical EOF.

## Local import and asset ownership

The writable CC mount is temporary import plumbing:

```text
CC file -> temporary staging copy -> reusable server MediaAsset -> playback
```

Recommended helpers are `hq.playFile`, `prepareFile`, `preparedInfo`, `playPrepared`, and `releasePrepared`.

`audioPlayStaged()` was a project prototype and was removed in M1F. Do not restore a second direct-staged playback route.

Known lifecycle debt after M1G:

- KI-064 was closed by post-M1G hardening: import has bounded no-progress handling and unsupported-`ATOMIC_MOVE` fallback;
- KI-054 was closed by post-M1G hardening: shutdown starts range-worker cancellation early, retains failed cleanup for retry, and preserves the media-store root lock until cleanup truly succeeds.

KI-061 was resolved: whole-owner staging cleanup removes persistent staging leftovers after unmount/release.

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

Protocol v7 separates timeline snapshots from codec restart intent.

- new media => new generation;
- semantic seek => `decodeRevision` increments;
- ordinary STATE/pause/resume/volume/loop snapshots keep the same revision and preserve a healthy decoder/window;
- local recovery may rebuild a missing decoder without changing server revision;
- stale lower-revision STATE is ignored;
- local worker identity is invalidated before cancellation can wake/report;
- STATE is the sole nonterminal transition authority;
- explicit STOP remains separate because stop removes the server session.

This closes KI-053/KI-056/KI-057.

## Volume, attenuation, and range

M1G deliberately uses a **fixed core listening/delivery radius** rather than native CC:T-style volume-dependent range.

Current server relevance already uses 32 blocks. The selected target contract is:

- fixed 32-block M1G core radius unless the owner explicitly changes the number;
- normal positional attenuation inside that radius;
- HQ `volume` changes gain/loudness, not the core radius;
- volume above 1 must not silently enlarge the modern finite attenuation distance;
- future Sound Physics Remastered compatibility owns any intentional range extension/acoustic behavior and matching transport relevance.

The active Minecraft channel is explicitly assigned the selected fixed 32-block attenuation distance. KI-058 is resolved.

## Volume zero

Global HQ volume zero does not pause canonical server time.

Current behavior:

- keep the server playback/session clock running;
- cancel local decoder and renderer work;
- stop client encoded range demand;
- reject/drop server range delivery while globally muted;
- retain client session metadata;
- on unmute, rebuild from the current authoritative position/anchor.

A player's own MASTER/BLOCKS slider is client-local and does not affect server transport. `FiniteSpeakerSound.canStartSilent()` supports that local-muted case.

## Looping

Looping is deliberately ordinary replay, not gapless playback.

At local physical EOF, if authoritative state still says `looping=true`, the client starts a fresh local decoder/render iteration and catches up to the current canonical loop position. A normal restart gap is acceptable.

M1G does **not** include sample-gapless boundaries, LAME/Xing delay/padding trimming, loop-head prefetch solely to hide the boundary, a permanent source across iterations, or SPR-specific loop continuity.

Seek/replacement/stop supersede stale local replay through generation/revision checks.

## Renderer startup

`FiniteSpeakerSound` uses Minecraft `SoundManager`, `SoundSource.BLOCKS`, linear positional attenuation, and `canStartSilent()`.

The client latches renderer start only after `SoundManager.play(...)` returns, observes later activation/physical EOF, and requests authoritative READY/STATE rejoin when the renderer fails to become active or is unexpectedly lost. KI-060 is resolved at source/component level.

## M1H listener and recovery lifecycle

M1H-1 now tracks which players actually own the active modern finite client session. The server checks the fixed 32-block relevance set every tick, sends BEGIN + current STATE when a player enters, sends targeted STOP when they leave, and prunes disconnect/dimension changes. READY and range traffic require current membership.

M1H-2 keeps local playback recoverable without changing protocol v7. Unexpected renderer/SoundEngine close or renderer loss requests a fresh authoritative STATE. READY is retried until STATE arrives. Five seconds of continuous renderer starvation also discards the stale local decoder and rejoins current server time. Ordinary STATE snapshots do not reset that starvation timer.

Focused Minecraft listener/reload/starvation checks are deferred to the runtime backlog.

### Moving-source handling

M1H-3 uses local position resolution rather than streaming coordinates over the network.

For each active modern finite speaker:

1. Sable Companion projects Sable/Aeronautics-style sublevel block coordinates into current world space when applicable;
2. otherwise the existing VS2 ship transform is used when applicable;
3. otherwise the block center is already the world position.

The client updates the existing positional sound from that result. The server uses the same resolved position for fixed-radius listener membership. BEGIN's existing block coordinates are sufficient, so protocol v7 needs no x/y/z update packet.

This is intentionally not a universal movement abstraction. Native ordinary Create contraption assembly/disassembly has different lifecycle semantics and is outside M1H-3 unless a later concrete requirement justifies that work.

Focused moving-source Minecraft testing remains in the runtime backlog.

## Cross-cutting ownership/threading safety

KI-062 is resolved. Dynamic legacy stream calls may still block their calling ComputerCraft thread during DNS, but blocking URL validation now runs outside both the ownership monitor used by `tickOwnership()`/cleanup and the separate command-order lock. This also matters because `audioPlayPrepared` is a CC:T main-thread method: it cannot be forced to wait behind DNS through that lock.

After validation, the normal single-speaker stream path briefly reacquires command ordering and ownership locking for the actual commit. A mutation revision rejects a normal stream start superseded by a newer playback/control command while DNS was pending. A lifecycle epoch separately rejects a result which returns after detach/cleanup, preventing a stale DNS completion from reviving a removed speaker.

KI-063 is resolved for the known RAW/prepared paths: replacements are validated/admitted before destructive ownership transfer.

## Multispeaker — selected M1J model

M1J uses one shared playback authority plus independent physical speaker endpoints.

A multispeaker start snapshots the speakers selected by the calling ComputerCraft computer. It does not maintain an expected-global-member count and does not automatically add speakers attached later.

The shared authority owns only facts that must be identical: media/playback identity, canonical time, play/pause/seek/loop, shared revisions, natural EOF/shared failure, and playback asset lifetime. Each endpoint owns its physical source identity, position/movement, listener membership, endpoint gain, range transport, renderer, and recovery.

Removing/replacing one endpoint detaches it from the authority without stopping the remaining endpoints. An authority with no endpoints releases its playback ownership.

Single-speaker prepared playback is the same architecture with one endpoint; do not maintain a second semantic engine for the one-speaker case.

Server-side authority alone is not sufficient for tight client synchronization. A later M1J protocol slice will add shared playback identity/state revision so multiple endpoint renderers on one client project the same local authoritative timeline while retaining separate positional SoundManager sources.

Encoded-range/decode sharing is deliberately not part of the correctness model. Measure duplicate work after M1J and add fan-out only if profiling justifies it.

Inherited `*All` / `*At` helpers are legacy and are not the target architecture.

## Raw and optional external/live sources

Standard `playAudio` remains CC:T signed-8 producer-fed audio. HQ `speakPCM` remains open-ended signed-16 producer-fed audio with bounded backpressure; neither is a finite song.

Direct internet-radio/ICY/HLS/TS support is not a core roadmap requirement. Existing inherited live code remains legacy until retained, replaced, or removed during convergence.

Spotify/YouTube/provider-backed playback is future product research and is not equivalent to generic direct-HTTP audio streaming.

## Legacy boundary

Inherited byte-taking finite/live APIs and whole-track classes remain for later M1L/M3 migration/removal. They are compatibility/legacy code, not the modern prepared architecture.

Legacy advertised format lists and legacy multispeaker note/sound helpers are not authoritative descriptions of modern prepared capabilities.

## Lifecycle / storage

Cleanup boundaries include peripheral removal, Level unload, server shutdown, computer detach/unmount, range/decoder worker shutdown, client disconnect/resource lifecycle, and bounded temporary encoded/PCM buffers.

`HQSpeakerPeripheralProvider` intentionally uses a Level-keyed weak map only as a fallback: cached values themselves reference their Level, so deterministic lifecycle eviction is the real design. Do not describe that as an accidental WeakHashMap cycle; verify actual unload/removal hooks when evaluating residual cache lifetime.

## VS2 / SPR / non-goals

VS2 remains reflective and optional. Sound Physics Remastered remains later work. Preserve one physical positional source per speaker.

Do not add permanent Java music/effects/notification channels, Java playlists, persistent client song caches, surround rendering from one speaker block, gapless-loop machinery, or automatic application-priority rules.
