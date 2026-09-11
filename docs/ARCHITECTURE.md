# Architecture

## Design principle

Improve CC:HQ Speakers as a **programmable ComputerCraft speaker peripheral**.

Do not model application roles such as music/effect/notification. Model technical source capabilities.

```text
                          one physical HQ speaker
                                   |
              +--------------------+--------------------+
              |                    |                    |
          raw/feed              finite media         live stream
      playAudio/speakPCM       MP3/OGG/WAV/...      MP3/HLS/TS
              |                    |                    |
      open-ended samples       known timeline       open-ended remote
              |                    |                    |
       backpressure/stop      seek/duration/loop    reconnect/stop/meta
```

Lua owns playlists, priorities, sequencing, alarms, notifications, music-player behavior, and other application policy.

## CC:T base contract

The normal `computercraft:speaker` remains the product surface. Standard CC:T behavior is foundational:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- `speaker_audio_empty`

The current composite peripheral delegates these standard calls to CC:T's original `SpeakerPeripheral`. Keep that approach unless exact target-stack evidence requires otherwise.

See `CC-T-COMPATIBILITY-CONTRACT.md`.

## HQ continuous-output policy

Java does not maintain a playlist.

For HQ continuous sources, a new incompatible playback replaces the previous HQ continuous playback. Repeated chunks belonging to one raw feed remain one feed. Standard CC:T calls retain their own CC:T semantics rather than being rewritten to match HQ replacement policy.

## Finite target model

Finite media is split into three concepts:

```text
MEDIA ASSET
    -> PLAYBACK STATE
        -> PHYSICAL SPEAKER RENDERER
```

A media asset contains the encoded file and finite facts such as size, format, duration, and optional seek metadata. It is reusable and not conceptually owned by one physical speaker.

A playback references an asset and owns generation, state, position, pause/resume, seek, loop, volume, and sync-clock membership.

A physical renderer is client-local and exists only while that speaker is relevant to the listener.

## Server-authoritative playback

The server owns finite semantic truth:

- playing/paused/ended/error state;
- position and duration;
- seek target;
- looping;
- volume;
- generation;
- sync-clock state.

Clients may report transfer/decoder/renderer failures, but a client renderer does not become the canonical clock or EOF authority.

A finite playback continues logically even when no player is nearby. A client which becomes relevant later receives the current generation/state and joins at the current position.

This replaces the M1A prototype's renderer-observation timeout, successful-renderer authority, and STARTED-driven canonical clock.

## Local ComputerCraft file import

The writable ComputerCraft mount is an **import/staging mechanism**.

```text
CC filesystem
    -> fs.copy to HQ writable mount
    -> server validates/prepares reusable media asset
    -> playback references that asset
```

The helper API may expose a simple `hq.playFile(speaker, path)` while lower-level prepare/play/release capabilities allow Lua to preload without Java becoming a playlist manager.

## Large finite transfer

Encoded finite files are transferred reliably, but in bounded client-requested ranges.

```text
client needs asset X
    -> request bounded range
    -> server returns several <= 256 KiB chunks
    -> client writes them to disk
    -> client requests next range
```

The next range request provides natural pacing/acknowledgement. There is no need for a permanent historical listener/recipient list or an unbounded server push queue.

Do not intentionally drop encoded MP3/OGG ranges and pretend the asset is complete. Stale **decoded PCM** may later be dropped at individual renderer taps to preserve real-time synchronization.

Server/client disk and codec work must run on bounded worker resources, not the Minecraft server tick or client render thread.

## Client asset cache

The client keeps reusable encoded assets on disk:

- partial `.part` state;
- validated completed asset;
- reuse across leaving/re-entering range;
- reuse by multiple speakers referencing the same asset;
- bounded cache/LRU cleanup;
- orphan partial cleanup after crashes.

Stopping one renderer does not delete a reusable encoded asset.

## Incremental finite decoding

Large-file memory use must not scale with decoded track duration.

Target:

```text
encoded asset on disk
    -> incremental decoder
    -> bounded PCM production
    -> one or more renderer taps
```

OGG uses file-backed STB Vorbis reads/seeks. MP3 uses incremental decoding with seek work off the render thread and may gain a coarse frame index. WAV/AIFF/AU/other proven JavaSound formats use bounded conversion and format-appropriate seek behavior.

Natural EOF must be distinct from close/cancel/resource reload.

## Dynamic range rendering

Do not retain everyone who once heard a speaker.

The server sends small current-state snapshots to currently relevant/tracking clients. A client which leaves range destroys or parks its local positional renderer. The server playback continues. If the client returns while playback is still active, it joins the current position; if playback stopped while away, it stays silent.

Dimension change, chunk unload, speaker removal, and VS2 movement belong to renderer relevance/lifecycle, not historical recipient ownership.

## Multispeaker and synchronization

The same encoded asset must not be transferred once per physical speaker.

Synchronized playbacks reference a shared sync-clock ID:

```text
asset X
  -> playback A --\
  -> playback B ---- shared sync clock Q
  -> playback C --/
```

There is no expected-global-member/expected-tap barrier. A client renders whichever physical speakers are currently relevant and joins the shared clock at its current frame.

Where practical, playbacks sharing the same asset and sync clock should decode once and fan bounded PCM to several renderer taps.

Each audible physical speaker still gets its own positional Minecraft/OpenAL source. This preserves spatial direction, attenuation, wall/occlusion differences, VS2 movement, and future Sound Physics Remastered behavior.

A speaker can leave the shared clock if Lua later pauses, seeks, stops, or replaces it independently.

## Raw/feed path

Standard `playAudio` remains CC:T's signed 8-bit producer-fed audio with native `speaker_audio_empty` semantics.

HQ `speakPCM` is also open-ended and must have its own truthful bounded feed/backpressure lifecycle. It does not gain duration or arbitrary seek merely because finite media does.

Do not reuse the legacy synthetic `speaker_audio_empty` heartbeat for HQ raw feed.

## Live streams

Internet MP3/HLS/TS remains a later milestone.

Live sources are open-ended. They do not expose finite duration/seek. Pause/resume, when stabilized, means suspend/stop then reconnect to the current live point.

The finite asset/cache architecture must not depend on live-stream implementation details.

## Prototype/legacy boundaries

The repository currently contains overlapping implementations:

- legacy HQ raw/finite/stream code in `HQSpeakerPeripheral` / `HQAudioStream`;
- M1A staged finite prototype in `HQFiniteMediaServer` / `HQFiniteMediaClient`;
- standard CC:T delegation in `HQSpeakerCompositePeripheral`.

The M1A prototype proved writable staging, chunk packets, disk-backed cache, and incremental decoding. Its fixed recipient set, per-speaker media ownership, server push transfer, renderer observation timeout, and client-authoritative state reports are scheduled for replacement. Do not polish those concepts during cleanup.

Legacy byte-taking finite APIs should eventually become compatibility frontends into the same asset/playback engine, after which the whole-file/whole-PCM legacy finite decoder can be removed.

## Lifecycle

Static caches must not retain old integrated-server Levels/worlds.

Required cleanup boundaries:

- peripheral/block removal;
- server Level unload;
- server shutdown;
- computer detach/unmount;
- future asset/transfer/decoder worker shutdown;
- client world/disconnect/resource lifecycle.

Weak keys are not sufficient when cached values strongly reference the same Level.

## VS2

VS2 integration remains reflective and optional. Preserve world-space speaker position updates and keep failures isolated.

## SPR

Sound Physics Remastered remains a later productization milestone. Shared asset/PCM optimization must never collapse several physical speakers into one positional OpenAL source. Preserve the frozen V7.1 acoustic work unless explicitly retuning it.

## Non-goals

Do not add permanent concepts such as:

- music channel;
- effects channel;
- notification channel;
- Java playlist/album database;
- automatic application-level priority rules.

Lua may build those policies using the peripheral capabilities.
