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

Whether the Lua program uses any of those as music, an alarm, speech, a soundboard, etc. is outside the Java architecture.

## CC:T base contract

The Mixin replaces `SpeakerBlockEntity.peripheral()` with `HQSpeakerPeripheral`, and that peripheral still reports type `speaker`.

Therefore the standard CC:T speaker surface is foundational:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- `speaker_audio_empty`

See `CC-T-COMPATIBILITY-CONTRACT.md`.

HQ behavior should extend this device rather than redefine those methods into unrelated behavior.

## Server/client split

Lua executes server-side. Minecraft sound rendering happens client-side.

The server may own **semantic intent/state**, but it cannot truthfully claim that a client rendered something unless a client reports it.

For finite media, M1 uses transition reports and an `observed` concept. Keep that separation.

Do not invent renderer truth when:

- no client was in range;
- a client failed to decode;
- a client missed a packet;
- a renderer was never started.

## Current raw/feed path

```text
Lua playAudio / speakPCM
    -> HQSpeakerPeripheral
    -> server SpeakerChunk queue
    -> one PCM packet
    -> HQSpeakerClientHandler RAW mode
    -> HQAudioStream PCM queue
    -> HQSpeakerSound
    -> Minecraft SoundEngine
```

Current defects:

- the server queue is packet-dispatch state, not true audible backpressure;
- `speaker_audio_empty` is not tied to real raw capacity;
- RAW mode can remain alive by returning silence;
- raw arrival can reset finite client state without matching server semantic changes.

The correct mixed raw/finite submission policy is an explicit P0 decision.

## Current finite path

```text
Lua speakMp3/speakOgg/speakWav/speakAudio
    -> server queue + FiniteServerTrack generation
    -> whole encoded packet (<= 8 MiB)
    -> client finite queue
    -> HQSpeaker-Decoder
    -> retained mono signed-16-bit PCM + exact sample rate
    -> FiniteAudioTrack renderer fork
    -> HQSpeakerSound
    -> Minecraft SoundEngine
```

Finite controls:

- pause/resume uses actual `ChannelHandle`;
- seek stops/re-primes the finite renderer at a retained PCM cursor;
- loop rewinds retained PCM;
- duration is frames/sampleRate;
- logical position is clock-based after STARTED confirmation.

Current renderer boundary policy: one renderer per logical finite item.

This is acceptable as the current implementation. Reusing channels across compatible consecutive items is an optional future optimization, not a P0 requirement.

## Current streaming path

```text
Lua speakStream/speakHLS/speakTS
    -> URL packet
    -> HQSpeakerClientHandler STREAM mode
    -> HQAudioStream
    -> StreamingAudioSource or SharedStreamingGroup
    -> PCM queue
    -> HQSpeakerSound
```

The live path is open-ended and must not be forced into finite duration/seek semantics.

Intended eventual pause/resume semantics:

```text
pause  -> stop/suspend current live source
resume -> reconnect/restart and play the source's current live point
```

Current code does not yet implement that behavior.

## Finite state model

Finite semantic states currently include:

- loading
- playing
- paused
- ended
- error

Generation IDs reject stale finite control/status.

Important remaining issues:

- anchor renderer has no failover;
- generation promotion can skip an earlier active item;
- no observed renderer can leave loading indefinitely;
- loop disable does not rebase wrapped position;
- seek exactly to duration needs a clean terminal path.

## Decoder threading/resources

Finite decode already runs off the Minecraft sound read thread. Preserve that.

Current global single-thread executor has an unbounded task queue. Lifecycle tokens reject stale publication but do not remove stale work.

P0 leaves the bounded-only vs bounded+cancellation implementation family as a user decision.

Large-media work must separately bound:

- encoded Lua/server copies;
- packet/chunk transport;
- queued decoder work;
- decoder temporary/native allocations;
- retained decoded PCM;
- simultaneous speakers/tracks;
- stream buffers.

## Multi-speaker/sync

The repo exposes All/At helpers and sync-group IDs.

Current range-local packet delivery conflicts with global expected group size. Fixing partial group behavior is a P0 design choice.

Do not solve synchronization by introducing music-specific concepts.

## VS2

VS2 integration is reflective and optional.

Moving speaker position is transformed server-side for packet/control range and updated client-side for active sound position.

Keep VS2 optional and failure-tolerant.

## SPR

Sound Physics Remastered integration remains a later productization milestone.

Reuse the frozen V7.1 acoustic work and its lifecycle/resource hardening. Do not retune acoustics casually and do not force SPR as a hard dependency.

## Non-goals

Do not add permanent concepts such as:

- music channel
- effects channel
- notification channel
- album/playlist database
- content-addressed media store

Lua may build all of those using the peripheral API if desired.
