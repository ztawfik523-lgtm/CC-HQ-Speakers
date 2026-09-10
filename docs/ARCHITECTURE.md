# Architecture

## Principle

Improve the inherited CC:HQ implementation rather than replacing it for architectural cleanliness.

This document should track the architecture the fork actually has and the smallest changes needed for player-visible goals.

## Inherited finite-media path

```text
Lua `speakOgg` / `speakMp3` / `speakWav` / `speakAudio`
    ->
HQSpeakerPeripheral
    ->
whole encoded file copied into byte[]
    ->
HQSpeakerAudioPacket
    ->
one finite-media client packet (currently max 8 MiB)
    ->
HQSpeakerClientHandler
    ->
HQAudioStream
    ->
HQSpeaker-Decoder executor
    ->
whole finite track decoded to PCM
    ->
queued direct PCM ByteBuffers
    ->
Minecraft AudioStream / SoundManager
```

Important consequences:

- current large-file limitation is partly architectural, not just a constant;
- finite decode is already moved to a decoder worker, so do not throw that away;
- decoded finite content still materializes in full;
- the client handler owns actual renderer-side finite playback state.

## Inherited streaming path

```text
Lua stream/HLS/TS call
    ->
URL packet
    ->
HQSpeakerClientHandler
    ->
HQAudioStream
    ->
StreamingAudioSource / SharedStreamingGroup
    ->
prebuffered PCM
    ->
Minecraft AudioStream
```

This path already contains reusable long-running streaming machinery. Before inventing a new ring-buffer system, audit whether parts of it can be generalized safely for large finite MP3/OGG.

## Current state split

Server-side `HQSpeakerPeripheral` currently knows:
- its queue;
- default volume;
- stream-active/url state;
- attached computers;
- source UUID.

Client-side `HQSpeakerClientHandler` knows:
- actual `SpeakerState`;
- `HQAudioStream`;
- `HQSpeakerSound`;
- whether Minecraft still considers the sound active;
- finite/streaming playback lifecycle.

The current `speakIsPlaying()` therefore does not represent actual finite renderer truth.

A proper player API needs an explicit state/control design connecting server/Lua queries with client playback state.

Do not silently choose the final authority model before source/runtime investigation.

## Lifecycle baseline

- server detach/block cleanup clears peripheral state and broadcasts a stop;
- client stop closes the sound and its finite/streaming source;
- client tick removes drained finite states;
- no explicit disconnect or resource-reload cleanup hook exists in the baseline.

The last item is a smoke-test target, not proof of a runtime leak.

## Looping

Current end-to-end loop semantics are missing:

- server has `looping`;
- audio packet does not carry it;
- client sound sets `looping = false`.

M1 must implement loop as real playback behavior, not just keep the existing boolean API.

## Player-control direction

Desired conceptual surface:

```text
play(...)
pause(...)
resume(...)
seek(...)
stop(...)
setVolume(...)
setLoop(...)
getStatus(...)
getPosition(...)
getDuration(...)
```

Exact names and whether playback IDs are required should be decided after auditing how one speaker currently replaces/queues simultaneous finite and streaming playback.

Preserve old methods as compatibility wrappers where practical.

## Large-media direction

Do not just raise `8 MiB`.

Audit and separately bound:

- Lua argument/file loading;
- server heap copy;
- network payload encoding;
- client encoded copy;
- decoder input;
- decoded PCM;
- concurrent decoder work;
- active playback;
- stream queues.

Potential directions with meaningful tradeoffs:

A. chunked finite transport + existing full decode
Simpler; fixes encoded-file cap first, but decoded-memory scaling remains.

B. chunked transport + incremental finite OGG/MP3 decode
More scalable and directly solves long media, but requires more lifecycle/backpressure work.

Do not choose A vs B silently if both remain reasonable after investigation.

## Threading

HighAudio exact-stack investigation observed Minecraft invoking `AudioStream.read()` on the `Sound engine` thread.

CC:HQ already uses `HQSpeaker-Decoder` for finite decoding.

Preserve that advantage. Long-media improvements should not move expensive decode/network work into `AudioStream.read()`.

## PCM correctness

Incremental producers must:

- preserve byte/sample order;
- never discard unread tails;
- expose complete audio frames;
- carry incomplete frame remainders where relevant;
- distinguish natural EOF, underrun/waiting, cancellation, and failure.

## SPR integration

The frozen V7.1 acoustic model is already prior work.

Future integration must:
- remain optional when SPR is absent;
- preserve frozen acoustics unless explicitly retuned;
- connect player pause/resume/seek/stop/reload lifecycle correctly;
- retain compat decode/cache/OpenAL hardening.

Companion JAR vs integrated optional module remains a later user choice.
