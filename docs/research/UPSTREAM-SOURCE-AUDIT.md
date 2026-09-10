# Inherited CC:HQ source audit

Baseline:
`d1a592351c866f9a28ceef00b59e591ee773f3d5`

This is a bootstrap source audit, not a complete code review.

## Finite-media limit

`HQSpeakerPeripheral`:
- `SPEAKER_MAX_AUDIO = 8 * 1024 * 1024`
- `speakWav` rejects larger data
- generic file helpers use the same finite-media path

`HQSpeakerAudioPacket`:
- `MAX_BYTES = 8 * 1024 * 1024`
- finite media is serialized as one `byte[]`

Conclusion:
The 8 MiB problem is duplicated across the Lua/peripheral and network boundary.

## Looping

`HQSpeakerPeripheral`:
- has `volatile boolean looping`
- `setLooping(boolean)` changes it

`HQSpeakerAudioPacket`:
- no loop field

`HQSpeakerClientHandler.HQSpeakerSound`:
- sets `this.looping = false`

Conclusion:
Inherited looping is not an end-to-end playback feature.

## Playback status

`HQSpeakerPeripheral.speakIsPlaying()`:
- returns queue-nonempty OR `streamActive`

`HQSpeakerClientHandler`:
- separately tracks actual client `SpeakerState`
- can query Minecraft `SoundManager.isActive(sound)`

Conclusion:
Server/Lua playing state and actual finite renderer state are disconnected.

## Finite decode

`HQAudioStream`:
- single worker thread `HQSpeaker-Decoder`
- `MAX_DECODED_PCM_BYTES = 64 MiB`
- OGG: `stb_vorbis_decode_memory`
- JavaSound path: decode/conversion then `readAllBytes()`

Conclusion:
The inherited mod already keeps primary decode off the sound read callback, but whole-track decoded PCM remains the finite-memory model.

## Read/EOF behavior

`HQAudioStream.read()`:
- returns queued PCM when available
- can return silence while data is not yet ready
- returns null only when its internal drained/closed conditions are met

Conclusion:
Reliable player state/repeat work needs explicit distinction between buffering, underrun, natural EOF, cancellation and decoder failure.

## Next audit targets

Before implementing M1/M2:
- full `HQSpeakerPeripheral` method/queue semantics
- group peripheral wrappers
- stop packets
- streaming source lifecycle
- sync-group replacement behavior
- network registration/payload size behavior on NeoForge 21.1.247/.248
- how finite decode marks closed/drained
- exact current repeat behavior in runtime
