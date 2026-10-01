# Current state

Updated: 2026-10-01

## Working v11 candidate — source/CI green, runtime acceptance pending

Active branch: `codex/m1j-multispeaker`

Current candidate checkpoint: `cd9a3f67ac3449df975c00240d83ee8e33239fc9`  
Candidate CI: `36783159001` — PASS  
Candidate artifact: `11128792552` — `hqspeaker-neoforge-21.1.247`  
Candidate JAR SHA-256: `ce91a67e8c20d82bb7e1bcc82bee94a025edfec426cecede2035fdfda4337d76`  
Runtime script checkpoint: `d90b0b536dfbb77239deb3739e1079069aa80ba9`  
Runtime-script CI: `36933446358` — PASS  
Protocol: **v11**, exactly 9 payloads.  
Build baseline: **NeoForge 21.1.247 only**.

Commits after the production candidate may be documentation-only or runtime-script-only. The runtime-script checkpoint above changes no production Java/resources/build logic; keep the candidate JAR identity separate from script revisions.

## Product state

Only the normal `computercraft:speaker` is upgraded. Native CC:T `SpeakerPeripheral` remains the owner of native note/sound/DFPWM behavior.

Supported HQ playback:

- finite MP3 + supported common WAV;
- prepared immutable finite media;
- multispeaker finite playback with shared canonical authority and endpoint-local renderer/gain/range/mute;
- signed-16 mono 48 kHz RAW;
- MP3/ICY radio, singular/All/At, with strict start-time membership.

Removed/unsupported scope remains removed: standalone HQ block, OGG/generic whole-file aliases, HLS, MPEG-TS and duplicate playback engines.

All HQ positional paths resolve Sable Companion -> VS2 -> static block center.

## v11 logical volume and range

Logical HQ volume is continuous `0..3`.

- `1.5` = normal reference.
- `3.0` = maximum.
- invalid/non-finite/out-of-server-limit requests throw Lua errors; they are not silently clamped.
- fixed input anchors: `0, 0.5, 1, 1.5, 2, 2.5, 3`.
- selected gain outputs: `0, .17, .34, .50, .67, .84, 1`.
- selected automatic ranges: `0, 12, 29, 48, 70, 96, 132` blocks.
- values between anchors are linearly interpolated.
- explicit `range` is measured in blocks and overrides only automatic range.
- default explicit-range server ceiling: 256 blocks.

The server resolves logical volume into concrete gain/range and sends those values to clients. The same resolved range is used for server delivery/relevance and client linear-clamped attenuation.

Finite `audioStatus` reports logical `volume`, resolved `gain`, resolved `range` and `rangeMode`.

## Server config reload rule

The existing NeoForge SERVER config contains the audio tuning profile.

Audio settings do not require a world restart.

A source snapshots the current profile when it begins:

```text
source starts under config A
config reloads to B
existing source keeps A
next finite / RAW / radio source uses B
```

Continuous RAW keeps one profile across its chunks until that RAW source ends/stops. There is no per-computer or per-script snapshot.

## Sound Physics Remastered integration

Target SPR: 1.21.1-1.5.1.

The current client path keeps Minecraft sound/channel ownership and adds HQ-specific acoustic reevaluation only for `hqspeaker:hq_audio_source`.

Refresh policy:

- accumulated listener/source displacement threshold: ~0.15 blocks;
- ordinary moving refresh minimum: ~100 ms;
- >=1 block displacement is urgent;
- one settle refresh after movement stops;
- fixed ~1 second hard-stale safety refresh;
- at most one queued/running expensive HQ SPR refresh globally;
- starts/resumes are urgent;
- scheduler decisions use the **physical speaker position**, not the reflected render position.

Each due refresh invokes normal SPR `processSound`; room/reverb/reflection calculation remains SPR-owned.

The HQ layer then applies the accepted direct-acoustic behavior:

- 17-path full progressive direct check: center + 8 inner at 0.20 + 8 outer at 0.49;
- adaptive 9-path partial refreshes after a full cache;
- center weight 4, inner 1, outer 0.5;
- center-path gate `0.20 + 0.80 * smoothstep(center)`;
- cutoff occlusion scale 0.35;
- gain occlusion scale 0.50;
- muffling alpha 0.30;
- log-space clear alphas 0.18 cutoff / 0.16 gain;
- room/reverb smoothing alpha 0.22;
- reflected-position stabilization using the accepted threshold/blend/offset/redirect values.

SPR's stock `setEnvironment` uses shared mutable filters, so HQ sources now use **private per-source direct/send EFX filters** while retaining SPR's native room targets and aux effect slots. Filters are created only after the source is PLAYING/PAUSED and are reattached on every environment application. Native SPR application remains the fallback if the HQ private path cannot be applied.

## Source/CI verification already done

The recheck before runtime testing fixed and CI-covered:

- fixed 32-block HQ relevance/delivery replaced by resolved range;
- protocol v11 packet fields and exactly 9 payloads;
- server-authoritative tuning resolution;
- RAW `All` profile lifetime across endpoints;
- physical-position scheduler input vs reflected render position;
- native/strict SPR reflected-position persistence;
- progressive-path weighting/clamping;
- private per-source EFX isolation and lifecycle safety;
- diagnostics for progressive probes, reflection stabilization and private EFX.

A full legacy/dead-code audit after the first v11 runtime pass also:

- removed the duplicate legacy computer-to-speaker registry which could retain stale members;
- routed discovery and playback through the same composite registry;
- removed dead native `speaker_audio_empty` readiness/proxy machinery; HQ RAW uses `hqspeaker_audio_empty`;
- reduced finite format/analyzer state to the shipped MP3 + common WAV contract;
- removed dead stream observers/test-only accessors and the false `.mp2` capability claim;
- aligned optional VS2 reflection with the current 1.21.1 `getShipManagingPos` / ship-to-world API instead of scanning historical method aliases;
- removed the ignored/deprecated NeoForge event-bus selector;
- updated GitHub Actions majors;
- verified the packaged JAR has 85 top-level production classes; the only seven with no ordinary Java incoming edge are the expected two event subscribers and five mixins.

## Runtime evidence

V11 focused SPR C2 is also established: the same continuous finite source reprocessed with SPR Update Moving Sounds OFF, progressive full/partial probes advanced, private EFX stayed on one source-owned filter with zero fallback, and wall occlusion produced measurable direct gain/HF reduction. This evidence remains valid because the later cleanup did not change the SPR scheduler/progressive/private-EFX implementation.

Historical v10 runtime evidence remains valid regression history:

- A1-A19 PASS;
- R1-R9 PASS;
- C1 Sable tracking PASS;
- C3 grouped radio + metadata + strict membership PASS;
- C4 RAW PASS at eight speakers;
- corrected C4 finite catch-up alignment is millisecond-scale and the old ~2.2-second admission-stall fingerprint is gone;
- focused A8/A9/A18 rejection recheck PASS.

Do **not** promote those results into a v11 release verdict. v11 changed gain/range transport and the active SPR/acoustic path.

## Remaining release-target work

Use this exact candidate JAR in one Minecraft launch and run the two tracked integrated scripts in order:

1. `scripts/v11_runtime_1.lua` on exactly two normal-world speakers:
   - representative/interpolated tuning + explicit/auto range;
   - invalid/non-finite rejection at the Lua boundary;
   - endpoint-local volume/range/mute;
   - live config reload old-source/new-source behavior;
   - simultaneous clear/occluded SPR private-filter isolation;
   - F3+T finite recovery;
   - representative 70-80 block playback/occlusion.
2. `scripts/v11_runtime_2.lua` on exactly eight Sable/Aeronautics speakers:
   - eight-speaker finite admission/catch-up with v11 tuning fields;
   - Sable movement tracking;
   - eight-speaker multi-chunk RAW continuation/backpressure;
   - grouped MP3/ICY radio + metadata.

Do not rerun C2. CI now compiles both integrated scripts with CC:T's Cobalt parser.

The script recheck hardened Test 1 against config-abort leakage, slow F3+T recovery and unrealistic long-range walk timing, and hardened Test 2 so RAW drain requires all eight endpoints plus finite looping begins before baseline measurement.

Only after both integrated runs pass, freeze the v11 runtime verdict.

Dedicated multiplayer, VS2 runtime, a separate NeoForge 21.1.248 build and >8 streamed-source guarantee remain outside the selected release target.
