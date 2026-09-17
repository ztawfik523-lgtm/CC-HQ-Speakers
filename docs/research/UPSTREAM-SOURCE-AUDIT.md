# Inherited CC:HQ source audit

Updated project-context note: 2026-09-17

> **Historical bootstrap audit.** This file records the inherited baseline and problems which motivated the later architecture. It is not a description of the current M1F/M1G implementation.
>
> Current authority is `../CURRENT-STATE.md`, `../M1G-SCOPE-DECISIONS-2026-09-14.md`, `../KNOWN-ISSUES.md`, `../TESTING.md`, `../VERIFIED-FACTS.md`, and exact current source.

Baseline:
`d1a592351c866f9a28ceef00b59e591ee773f3d5`

This was a bootstrap source audit, not a complete code review.

## Finite-media limit at the inherited baseline

`HQSpeakerPeripheral`:
- `SPEAKER_MAX_AUDIO = 8 * 1024 * 1024`
- `speakWav` rejects larger data
- generic file helpers use the same finite-media path

`HQSpeakerAudioPacket`:
- `MAX_BYTES = 8 * 1024 * 1024`
- finite media is serialized as one `byte[]`

Historical conclusion:
The 8 MiB problem was duplicated across the Lua/peripheral and network boundary.

**Current status:** modern prepared playback no longer uses this whole-payload transport. M1F uses server MediaAssets and bounded client-requested ranges. Inherited byte-taking APIs still exist as legacy surfaces.

## Looping at the inherited baseline

`HQSpeakerPeripheral`:
- has `volatile boolean looping`
- `setLooping(boolean)` changes it

`HQSpeakerAudioPacket`:
- no loop field

`HQSpeakerClientHandler.HQSpeakerSound`:
- sets `this.looping = false`

Historical conclusion:
Inherited looping was not an end-to-end playback feature.

**Current status:** modern server timeline owns looping. M1G loop behavior is now selected as ordinary local replay after physical EOF while authoritative looping remains enabled; a restart gap is acceptable. That replay source work is still pending.

## Playback status at the inherited baseline

`HQSpeakerPeripheral.speakIsPlaying()`:
- returns queue-nonempty OR `streamActive`

`HQSpeakerClientHandler`:
- separately tracks actual client `SpeakerState`
- can query Minecraft `SoundManager.isActive(sound)`

Historical conclusion:
Server/Lua playing state and actual finite renderer state were disconnected.

**Current status:** modern M1E server state is canonical and client failures are diagnostics. Green source/CI still does not prove a particular client is audibly rendering.

## Finite decode at the inherited baseline

`HQAudioStream`:
- single worker thread `HQSpeaker-Decoder`
- `MAX_DECODED_PCM_BYTES = 64 MiB`
- OGG: `stb_vorbis_decode_memory`
- JavaSound path: decode/conversion then `readAllBytes()`

Historical conclusion:
The inherited mod kept primary decode off the sound read callback, but whole-track decoded PCM remained the finite-memory model.

**Current status:** the modern prepared path progressively decodes MP3/common WAV through bounded encoded and PCM windows. The old complete-file bridge remains legacy only.

## Read/EOF behavior at the inherited baseline

`HQAudioStream.read()`:
- returns queued PCM when available
- can return silence while data is not yet ready
- returns null only when its internal drained/closed conditions are met

Historical conclusion:
Reliable player state/repeat work needs explicit distinction between buffering, underrun, natural EOF, cancellation and decoder failure.

**Current status:** modern M1F/M1G explicitly distinguishes NEED_DATA, true asset EOF, cancellation/stale state, PCM starvation and queue EOF. General long-underrun rejoin still remains M1H.

## Historical next audit targets

The original bootstrap list was:
- full `HQSpeakerPeripheral` method/queue semantics
- group peripheral wrappers
- stop packets
- streaming source lifecycle
- sync-group replacement behavior
- network registration/payload size behavior on NeoForge 21.1.247/.248
- how finite decode marks closed/drained
- exact current repeat behavior in runtime

Those targets were superseded by subsequent M1A-M1G work and later full-repository audits. Do not use this list as the current implementation plan.

Current open work is tracked in `../KNOWN-ISSUES.md`. The main current clusters are explicit decoder/reanchor revision (KI-053/056/057), fixed-range renderer/start behavior, ordinary replay, evidence/staging gaps, shared-monitor/DNS safety, replacement admission, and storage/shutdown hardening.
