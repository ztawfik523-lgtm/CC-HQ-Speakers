# Testing

Updated: 2026-09-21

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does not prove Minecraft audibility, OpenAL/SoundManager lifecycle, moving-ship behavior, spatial synchronization or realistic performance.

Frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
CI: `35655973164` — NeoForge 21.1.247 + 21.1.248 PASS.

Deterministic coverage includes finite analyzers/decoders/range transport/state machines/recovery/projection, storage/release limits, ordered multi-lock behavior, RAW feed lifetime, strict radio group sealing, URL policy and package checks.

## Frozen runtime targets

### Native CC:T

Verify `playNote`, `playSound`, `playAudio`, `stop`, native `speaker_audio_empty`, plus grouped/indexed native helpers.

### Finite singular

MP3 and common WAV: start, status, pause/resume, seek, volume, mute, loop enable/disable/wrap, exact-duration end, replacement and stop.

### Finite multispeaker

2, 4 and 8+ speakers: shared playback ID/timeline, synchronized start, pause/resume/seek/loop, endpoint volume/mute, All volume/mute snapshot semantics, `audioStopAt` survivor behavior, endpoint remove/replace and concurrent controls.

### Listener/recovery

Outside-at-start, enter, leave, re-enter at current time, dimension/disconnect cleanup, resource reload, renderer loss and sustained starvation.

### Movement

Static regression plus real Sable/Aeronautics and VS2 motion. Test finite, RAW and radio because all now share the movement resolver.

### RAW

Signed-16 audibility, repeated feed continuity, max/rejection behavior, `hqspeaker_audio_empty`, All preflight/common start, At targeting, drain/grace ownership and moving source.

### MP3/ICY radio

Direct, At and All; metadata; strict no-auto-membership; late speaker stays out until rerun; one-client subset behavior; start sync after prebuffer; long-run drift; `audioStopAt`/All stop; bad URL, EOF and network interruption.

Remember: `isStreaming` is server-side ownership/request state, not client-connect proof.

### Bounds/stress

Malformed/extreme MP3/WAV, long media, repeated prepare/release, configured storage limits, range request bounds, worker shutdown/restart, many listeners/speakers, repeated start/stop/replace/seek.

### Sound Physics Remastered

Verify these SoundManager sources are processed normally and profile many simultaneous sources. Add compatibility code only if runtime proves an actual gap.

## Package checks

CI verifies NeoForge metadata, mixins, Jar-in-Jar metadata, JLayer 1.0.1.4, Sable Companion 1.6.0 and bundled `hqspeaker.lua`.

## Current scripts

Current/frozen helpers:

- `scripts/v10_core_acceptance.lua`
- `scripts/v10_radio_acceptance.lua`
- `scripts/p0_cc_speaker_contract.lua`
- `scripts/p0_finite_regression.lua`

M0/M1 milestone scripts are historical unless explicitly promoted by the v10 runtime plan.
