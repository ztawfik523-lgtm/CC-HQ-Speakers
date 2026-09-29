# Architecture

Updated: 2026-09-29

## Product boundary

The normal CC:T `computercraft:speaker` is the product surface. `HQSpeakerCompositePeripheral` adds HQ behavior while the real CC:T `SpeakerPeripheral` remains the owner of native `playNote`, `playSound`, `playAudio`, `stop` and native `speaker_audio_empty`.

One physical speaker remains one mono positional source.

## Ownership

Each endpoint has one HQ continuous owner: NONE, RAW, STAGED_FINITE or STREAM.

Finite multispeaker playback shares canonical media/playback authority while endpoints keep their own listener membership, transport, decoder, renderer, gain, range and mute state.

RAW and radio are endpoint sources. Grouped starts use strict membership snapshots rather than automatic later membership.

## Server-authoritative audio tuning

Protocol v11 separates logical volume from resolved source gain and audible range.

For each new source, the server snapshots the current `HQAudioTuningProfile` and resolves:

```text
logical volume -> gain anchor interpolation
logical volume -> automatic range anchor interpolation
explicit range -> replaces automatic range only
```

Clients receive the concrete resolved gain/range; they do not reinterpret the server config.

The source snapshot is immutable for its lifetime. A live server-config reload affects later starts only. Continuous RAW retains one source profile across chunks.

## Audible range

The historical fixed 32-block HQ radius is gone.

The resolved range is used consistently for:

- finite listener relevance;
- RAW/radio packet delivery;
- radio metadata relevance;
- stop/update delivery safety;
- Minecraft/OpenAL linear attenuation on the client.

With the current linear-clamped OpenAL setup, range is the fade-to-zero distance in blocks.

## Finite transport

Prepared encoded media is stored server-side as immutable assets and fetched progressively in bounded ranges.

Transport bounds remain separate from audible range:

- 128 KiB maximum encoded range response;
- 512 KiB client encoded sliding window;
- 4 outstanding range requests / 512 KiB outstanding bytes per player;
- 2 server range IO workers;
- server range IO queue 64.

All local finite endpoints share the same per-player admission budget so eight endpoints cannot independently stampede the four-request server cap.

Finite catch-up prefers joining the correct media time over forcing simultaneous channel creation.

## RAW

HQ RAW is producer-fed signed-16 mono 48 kHz PCM with bounded queue/backpressure.

A continuous RAW lifetime snapshots its audio tuning profile when it starts. Later chunks reuse that profile. `speakPCMAll` preserves each endpoint's existing RAW profile if endpoints began under different config snapshots.

Locally exhausted channels are repumped when later PCM arrives rather than padded with fake silence.

## MP3/ICY radio

Radio supports direct MP3/ICY HTTP(S) streams only.

Grouped radio uses strict start-time membership and one client-local shared decoder/prebuffer feeding endpoint taps. Late/new speakers join only after a rerun.

`isStreaming()` is server stream ownership/request state, not proof that every client connected or is audible.

## Movement

All HQ positional paths use `MovingSourcePosition`:

1. Sable Companion;
2. VS2;
3. static block center.

The physical speaker position is kept separate from any acoustically redirected/reflected render position.

## Sound Physics Remastered

SPR is optional and client-side. The HQ server, playback authority and network protocol do not depend on SPR being installed.

HQ finite/RAW/radio sources use Minecraft streaming channels under `SoundSource.BLOCKS` with sound id `hqspeaker:hq_audio_source`.

### Long-lived refresh scheduler

SPR normally evaluates a sound when it starts. Its global Update Moving Sounds option can periodically reevaluate active sounds, but that option is intentionally allowed to remain OFF.

HQ therefore tracks only its own active sources and schedules full normal SPR `processSound` reevaluations when needed:

- ~0.15 block accumulated listener/source displacement;
- >=100 ms between ordinary movement refreshes;
- >=1 block urgent displacement;
- settle refresh after movement stops;
- fixed ~1 second hard-stale refresh;
- starts/resumes urgent;
- no catch-up bursts;
- at most one expensive HQ SPR task globally queued/running at once.

The scheduler uses physical source position. Reflection-stabilized render position never feeds back into movement scheduling.

### Progressive direct occlusion

SPR remains authoritative for room/reverb/reflection calculations. HQ replaces only the direct/dry target for bound HQ sources with the runtime-approved compat behavior.

Full refresh: center + 8 inner probes at 0.20 + 8 outer probes at 0.49.

After a valid full cache, alternating partial refreshes evaluate center+inner or center+outer, for 9 fresh paths.

Weights and gate:

```text
centerWeight = 4
innerWeight  = 1
outerWeight  = 0.5
ringScale = 0.20 + 0.80 * smoothstep(centerOcclusion)
denominator = 16
```

Direct targets use the accepted scales:

```text
cutoffOcc = min(SPR maxOcclusion, raw * 0.35)
gainOcc   = min(SPR maxOcclusion, raw * 0.50)
cutoff    = exp(-cutoffOcc * blockAbsorption * 3.0)
gain      = exp(-gainOcc   * blockAbsorption * 0.3)
```

SPR strict-occlusion mode falls back to native direct behavior.

### Smoothing and reflection

Accepted smoothing:

- direct muffling alpha 0.30;
- log-space direct clearing alpha 0.18 cutoff / 0.16 gain;
- room/reverb target smoothing alpha 0.22.

Reflected positions are bounded and smoothed before becoming the persistent render position. Native/strict fallback preserves SPR's own reflected position rather than snapping back to the physical block on the next Minecraft tick.

### Private EFX isolation

SPR 1.21.1-1.5.1 owns one shared set of mutable direct/send filters. Sharing those across simultaneously active HQ sources can contaminate one source with another source's environment.

HQ therefore creates per-source private low-pass filters after the source is PLAYING/PAUSED, writes SPR-derived room targets plus the HQ direct pair into those filters, and reattaches them on every environment application.

SPR's native aux effect slots/reverb effects remain authoritative. If private EFX cannot be applied safely, the mixin allows stock SPR `setEnvironment` to run as fallback.

## Diagnostics

Release JARs carry dormant diagnostics used by runtime acceptance. They observe real Minecraft/OpenAL channels, stream/buffer state, movement, synchronization, SPR process calls, progressive probes, reflection stabilization and private EFX.

Diagnostics do not add another payload; protocol v11 remains exactly 9 payloads.

## Build policy

Build/test/package only NeoForge 21.1.247. Metadata remains `[21.1,21.2)`; do not create a second 21.1.248 artifact.
