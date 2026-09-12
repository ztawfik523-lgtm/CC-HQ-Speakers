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
      playAudio/speakPCM       MP3 / WAV core       MP3/HLS/TS
              |               + FLAC if proven           |
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
MEDIA ASSET ON SERVER
    -> SERVER PLAYBACK STATE
        -> CLIENT STREAM/DECODER/RENDERER
```

A media asset contains the encoded server file and finite facts such as size, format, duration, and seek metadata. It is reusable and is not conceptually owned by one physical speaker.

A playback references an asset and owns generation, state, position, pause/resume, seek, loop, volume, and later sync-clock membership.

A physical renderer is client-local and exists only while that speaker is relevant to the listener.

## Supported finite product scope

The supported product target is deliberately narrow:

- MP3 / MPEG Layer III;
- normal WAV with common, easy-to-decode sample representations;
- normal native FLAC only if its decoder/analyzer/seek/package path is proven in the gated FLAC milestone.

OGG Vorbis, Ogg-FLAC, AIFF/AIF, and AU/SND are not product targets going forward. Historical M1D support for analyzing some of those formats remains historical evidence and should not force the final streaming architecture to retain them.

Finite render output is **mono positional audio**. Mono input stays mono. Stereo input is downmixed to mono. More-than-stereo input is rejected rather than pretending one physical Minecraft speaker is a surround array.

For WAV, support the common/easy cases only: unsigned 8-bit PCM, signed 16/24/32-bit PCM, and 32-bit IEEE float, mono/stereo only. Do not keep every unusual telephony codec or arbitrary professional bit width which can technically appear inside a WAV container.

## Server-authoritative playback

The server owns finite semantic truth:

- playing/paused/ended/error state;
- position and duration;
- seek target;
- looping;
- volume;
- generation;
- later sync-clock state.

A successful finite `play` starts the canonical server timeline immediately. It does not wait for a nearby client, transfer completion, decoder readiness, or audible renderer.

Clients may report transfer/decoder/renderer failures for diagnostics, but a client renderer does not become the canonical clock or EOF authority.

A finite playback continues logically even when no player is nearby. A client which becomes relevant later receives current state and streams from the current server position.

M1E has already implemented this semantic server-authority model. M1F+ replaces the remaining transitional transport/render path.

## Local ComputerCraft file import

The writable ComputerCraft mount is an **import/staging mechanism**.

```text
CC filesystem
    -> fs.copy to HQ writable mount
    -> server validates/prepares reusable media asset
    -> playback references that asset
```

The helper API may expose a simple `hq.playFile(speaker, path)` while lower-level prepare/play/release capabilities allow Lua to preload without Java becoming a playlist manager.

## Finite transfer is streaming, not client caching

The server keeps the complete encoded finite asset. Relevant clients request bounded encoded ranges while playback is active.

```text
server MediaAsset
    -> client asks for encoded bytes near current playback position
    -> server reads a bounded range off-thread
    -> server sends bounded packets
    -> client keeps a small temporary RAM window
    -> decoder produces bounded PCM
    -> mono positional renderer
```

The client does **not** maintain a persistent encoded music cache on disk. There is no client `.part` library, LRU asset database, persistent partial-download resume, or cross-restart song cache in the target architecture.

The only client retention required for finite playback is bounded temporary memory:

- encoded bytes currently needed by the decoder;
- small seek/pre-roll context where required by the codec;
- bounded decoded PCM queued for the sound renderer.

Old encoded windows are discarded once they are no longer useful. Stopping playback, leaving the server, or destroying the renderer may discard them completely.

The server remains the source of truth and source of encoded bytes.

## Range request behavior

Transfer is client-pulled for pacing, cancellation, seeking, and late entry. A request identifies the current source/generation/asset plus offset and bounded length. The server validates current playback, player relevance, bounds, and resource limits before reading.

Response packets remain bounded. The existing 256 KiB packet size is a valid starting point, not a permanently frozen optimum; packet compression/CPU behavior should be benchmarked before final tuning.

The next request naturally provides flow control. There is no need for a permanent historical listener list or for the server to enqueue an entire large song to a client.

All server asset reads and client decode work must run away from Minecraft game/audio threads. The server re-checks generation/relevance before sending an asynchronously read range because playback or player relevance may have changed while IO was in flight.

## Progressive finite decoding

Large-file playback must begin after a bounded prebuffer rather than after full file transfer. RAM usage must not scale with track duration.

Target:

```text
bounded encoded window in RAM
    -> codec decoder/converter worker
    -> bounded mono PCM queue
    -> Minecraft/OpenAL positional renderer
```

### MP3

Use the exact JLayer path only with an input abstraction which distinguishes **temporary missing bytes** from true asset EOF. Temporary stream starvation must wait/refill on a decoder worker; it must never be exposed to JLayer as permanent EOF.

Random seek/rejoin uses server-derived MP3 frame offsets and codec pre-roll. MPEG Layer III's bit reservoir means the decoder should begin from earlier encoded frames and silently decode forward before audible output at the requested/current server position.

### WAV

For supported normal WAV shapes, server analysis provides enough layout information for direct frame-range conversion. Common mono/stereo source samples are converted/downmixed to signed mono PCM without requiring the full WAV locally.

Do not retain support for every WAV encoding merely because JavaSound can theoretically open it. Narrow analysis/advertisement to the common sample formats the progressive converter actually supports.

### FLAC

Native `.flac` is wanted because it is finite and frame-based, but FLAC is a separate gated extension after the MP3/WAV engine. It is not considered implemented or advertised until the project proves an exact decoder path, metadata/seek behavior, malformed-input handling, target-stack packaging, and Minecraft runtime playback.

Do not add Ogg-FLAC.

## Seeking while streaming

Seeking remains a finite-media feature even without a client cache.

```text
Lua seek(T)
    -> server canonical position becomes T immediately
    -> client discards irrelevant buffered encoded/PCM data
    -> client requests encoded data needed around current server position
    -> decoder pre-rolls/buffers as required
    -> renderer rejoins the server timeline
```

The client does not have to retain old song data simply to support seek. It can fetch another bounded encoded window from the server.

If fetching/decoding takes time, the server clock continues. When the client is ready it joins the **then-current** server position rather than pretending playback waited for it.

## Underrun behavior

A slow client never pauses canonical finite playback.

If its encoded or decoded buffer runs dry, that listener may become temporarily silent. It refills from the server and rejoins the current position. The exact renderer tactic (stop/recreate versus bounded silence) should be settled with audible runtime tests; it does not change server semantics.

## Dynamic range rendering

Do not retain everyone who once heard a speaker.

The server sends current-state snapshots to currently relevant/tracking clients. A client which leaves range destroys or parks its local renderer and stops requesting encoded ranges. The server playback continues. If the client returns while playback is active, it streams from the current position; if playback stopped while away, it stays silent.

Dimension change, chunk unload, speaker removal, and VS2 movement belong to renderer relevance/lifecycle, not historical recipient ownership.

## Multispeaker and synchronization

The same server asset may back several physical speaker playbacks without duplicating the server file.

Synchronized playbacks later reference a shared sync-clock ID:

```text
asset X
  -> playback A --\
  -> playback B ---- shared sync clock Q
  -> playback C --/
```

There is no expected-global-member/expected-tap barrier. A client renders whichever physical speakers are currently relevant and joins the shared clock at its current position.

Where practical, clients hearing several synchronized speakers may share encoded transfer/decode work **in memory while those renderers are active**, but persistent client caching is not required.

Each audible physical speaker still gets its own positional Minecraft/OpenAL source. The encoded stereo source, if any, has already been downmixed to mono before spatial rendering. This preserves physical source direction, attenuation, wall/occlusion differences, VS2 movement, and future Sound Physics Remastered behavior.

A speaker can leave the shared clock if Lua later pauses, seeks, stops, or replaces it independently.

## Raw/feed path

Standard `playAudio` remains CC:T's signed 8-bit producer-fed audio with native `speaker_audio_empty` semantics.

HQ `speakPCM` is also open-ended and must have its own truthful bounded feed/backpressure lifecycle. It does not gain duration or arbitrary seek merely because finite media does.

Do not reuse the legacy synthetic `speaker_audio_empty` heartbeat for HQ raw feed.

## Live streams

Internet MP3/HLS/TS remains a later milestone.

Live sources are open-ended. They do not expose finite duration/seek. Pause/resume, when stabilized, means suspend/stop then reconnect to the current live point.

Finite-file streaming and live-network streaming may share low-level buffering ideas, but they have different semantics: a finite server asset has known size/duration and arbitrary server-readable byte ranges; a live stream does not.

## Prototype/legacy boundaries

The repository currently contains overlapping implementations:

- legacy HQ raw/finite/stream code in `HQSpeakerPeripheral` / `HQAudioStream`;
- staged/prepared finite path in `HQFiniteMediaServer` / `HQFiniteMediaClient`;
- standard CC:T delegation in `HQSpeakerCompositePeripheral`.

M1E has already replaced the renderer-authoritative semantic model, but the finite client/server transfer is still transitional: fixed recipients, server push-whole-file behavior, client disk-file bridge, and complete-file decoder remain until M1F/M1G. Do not polish those concepts into the final design.

Legacy byte-taking finite APIs should eventually become compatibility frontends into the same asset/playback engine, after which the old whole-file/whole-PCM finite decoder can be removed. Legacy OGG-specific compatibility APIs may be removed/deprecated with the narrowed finite format scope rather than forcing Vorbis into the final architecture.

## Lifecycle

Static caches must not retain old integrated-server Levels/worlds.

Required cleanup boundaries:

- peripheral/block removal;
- server Level unload;
- server shutdown;
- computer detach/unmount;
- asset/transfer/decoder worker shutdown;
- client world/disconnect/resource lifecycle;
- bounded temporary encoded/PCM buffers.

Weak keys are not sufficient when cached values strongly reference the same Level.

## VS2

VS2 integration remains reflective and optional. Preserve world-space speaker position updates and keep failures isolated.

## SPR

Sound Physics Remastered remains a later productization milestone. Shared in-memory media/PCM optimization must never collapse several physical speakers into one positional OpenAL source. Preserve the frozen V7.1 acoustic work unless explicitly retuning it.

## Non-goals

Do not add permanent concepts such as:

- music channel;
- effects channel;
- notification channel;
- Java playlist/album database;
- persistent client song/music cache;
- surround rendering from one physical speaker block;
- automatic application-level priority rules.

Lua may build application policy using the peripheral capabilities.
