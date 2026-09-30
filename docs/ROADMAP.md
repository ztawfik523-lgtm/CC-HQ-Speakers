# Roadmap

Updated: 2026-10-01

Audited candidate checkpoint before documentation-only updates: `cd9a3f67ac3449df975c00240d83ee8e33239fc9` (last production-code commit `11a22e9f122ebf511744ac0aa1b17512fd69b6eb`).

This roadmap supersedes the older “v10 is frozen / no HQ refresh planned” wording. Runtime work proved that long-lived HQ sources need their own efficient SPR reevaluation path, and the product is now intentionally adding the agreed volume/range model plus the previously accepted acoustic tuning work.

Build/test/package only NeoForge **21.1.247**. Keep metadata compatibility `[21.1,21.2)`; do not add a separate 21.1.248 artifact.

## Already established

- A1-A19 deterministic matrix and R1-R9 real-client core matrix passed on the selected target.
- C1 Sable tracking passed.
- C3 grouped MP3/ICY radio + metadata + strict membership/rerun passed.
- C4 RAW passed at 8 speakers.
- C4 finite admission stall was fixed; corrected catch-up alignment is millisecond-scale.
- A8/A9/A18 focused rejection recheck passed.
- Focused v11 C2 PASS: one continuously-playing HQ source reprocessed and became measurably occluded as the listener moved behind a normal-world wall with SPR Update Moving Sounds OFF.
- The HQ-only movement-gated SPR scheduler is established at runtime: ~0.15-block accumulated movement threshold, ~100 ms moving cadence, settle refresh, fixed ~1 s safety refresh, no catch-up bursts, and at most one expensive SPR refresh task globally at a time.
- The existing `HQSpeakerServerConfig` SERVER config has been extended with the selected audio tuning profile.

## Locked volume/range product model

Logical HQ volume remains a continuous numeric input from **0 to 3**.

- `1.5` is the normal reference point.
- `3.0` is maximum.
- lower values are quieter and shorter-range.
- HQ APIs reject non-finite or out-of-server-limit volume/range requests with a Lua error; do not silently clamp them.
- fixed input anchor positions: `0, 0.5, 1, 1.5, 2, 2.5, 3`.
- gain and default range use separate output anchor tables with linear interpolation between anchors.
- range is an actual distance in blocks.
- explicit range overrides only automatic range; it does not change loudness.
- while range is automatic, changing logical volume also changes automatic range.
- while range is explicitly overridden, changing logical volume changes gain but leaves range alone.

Accepted starting automatic-range anchors:

```text
volume: 0    0.5   1.0   1.5   2.0   2.5   3.0
range:  0     12    29    48    70    96    132 blocks
```

Selected starting gain anchors are `0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0`. This is the requested near-linear progression with slight upward rounding. Minecraft clamps normal SoundInstance gain to 1.0, so the default server gain anchors remain within 0..1.

## Server config

The existing NeoForge SERVER config now contains the `audio` section.

Implemented controls:

```text
defaultVolume = 1.5
maxVolume = 3.0
allowRangeOverride = true
maxRange = 256.0

gain anchors at 0 / 0.5 / 1 / 1.5 / 2 / 2.5 / 3
range anchors at 0 / 0.5 / 1 / 1.5 / 2 / 2.5 / 3
```

The existing media-storage limits stay world-restart settings. The new audio tuning settings do **not** require restart.

Config reload rule is deliberately simple:

```text
active source keeps the tuning profile it started with
new finite / RAW / radio source snapshots the current config
```

No per-computer config snapshot, no script-restart rule, and no retroactive mutation of active sources.

For a continuous RAW source, the profile snapshot begins when RAW ownership starts and survives subsequent `speakPCM` chunks until that RAW source naturally closes or is stopped.

## Implementation status

Source work through the acoustic integration is implemented and CI-covered. Runtime evidence is now the blocker, not missing core implementation.

Completed:

1. immutable server-owned audio tuning profiles and Lua-error validation;
2. protocol v11 with the same 9 payload types and server-resolved gain/range;
3. removal of the fixed 32-block HQ delivery/relevance limit;
4. resolved client gain plus explicit linear-clamped attenuation range;
5. optional Lua range plus endpoint-local finite range controls/status;
6. the accepted Beta3/Beta5 progressive direct model, smoothing and reflection stabilization;
7. HQ-only movement-gated SPR reevaluation with global SPR "Update Moving Sounds" allowed to remain OFF;
8. per-source private EFX filters for HQ sources while retaining SPR's native room/reverb targets and auxiliary effect slots.

The private-EFX decision is no longer conditional. Reinspection of the exact SPR 1.21.1-1.5.1 release source confirmed that stock `setEnvironment` mutates one shared set of direct/send low-pass filters for all sources. Historical runtime evidence already showed that this causes multispeaker contamination. The current implementation therefore uses separate HQ filters per OpenAL source, reattached on every environment application, with native SPR fallback if that path fails.

A later explicit cleanup audit also removed or fixed:

- duplicate stale speaker-group discovery state;
- dead native RAW-ready event/proxy machinery;
- retired OGG/AIFF/AU finite analyzer/model code;
- obsolete modern-vs-historical analyzer layering;
- dead stream/test-only accessors;
- false `.mp2` radio capability advertising;
- 1.20.1-era VS2 transform method scanning, replaced with the concrete 1.21.1 API shape;
- the ignored/deprecated NeoForge event-bus selector;
- deprecated GitHub Actions majors.

The packaged candidate was dependency-scanned: all 85 top-level production classes have ordinary incoming references except the expected two NeoForge event subscribers and five mixins.

The full recheck also fixed:

- scheduler movement decisions accidentally observing the reflection-stabilized render position instead of the physical speaker position;
- native/strict SPR reflected positions being snapped back by Minecraft's next TickableSoundInstance update;
- RAW `All` continuation across endpoints with different config-snapshot lifetimes;
- progressive path samples being capped before the approved weighted blend;
- a legacy packet-constructor path which could synthesize gain/range client-side instead of requiring server-resolved v11 values.

## Remaining work

1. **Two integrated runtime runs on the audited candidate**
   - Test 1: exactly two normal-world speakers — tuning/range/rejection, endpoint controls, live config reload, simultaneous SPR private-filter isolation, F3+T recovery and 70-80 block SPR behavior.
   - Test 2: exactly eight Sable/Aeronautics speakers — finite scale/catch-up, Sable movement, RAW continuation/backpressure and grouped MP3/ICY radio + metadata.
   - Use one JAR and one Minecraft launch. Focused C2 is already PASS and is not repeated.

2. **Release freeze**
   - Keep the candidate metadata and runtime results synchronized in the authority docs.
   - Mark release acceptance complete only after both integrated runs PASS.
   - Keep release diagnostics in the normal JAR.

## Important implementation facts found in the recheck

- Minecraft 1.21.1's linear attenuation path uses the supplied attenuation distance as the OpenAL linear-clamped max distance; with HQ's setup, range is naturally the fade-to-zero distance.
- Minecraft clamps normal SoundInstance gain to 1.0, while volume >1 normally affects attenuation distance separately. The new HQ model therefore needs to resolve gain and range separately.
- The old finite 32-block attenuation override and server-side 32-block delivery/relevance cap have been replaced by each source's server-resolved range.
- SPR's default maximum processing distance is large, but its safe cloned-world neighborhood is much smaller. Long audible range is valid; far-distance acoustic quality must be runtime-checked rather than treated as a hard 60-block speaker limit.

## Release/deferred scope

Still outside the selected release target unless deliberately reopened later:

- dedicated-server/multiplayer acceptance;
- VS2 runtime acceptance;
- separate NeoForge 21.1.248 build;
- OGG/FLAC/HLS/MPEG-TS/provider/playlists/standalone HQ block;
- >8 streamed-source guarantee beyond the already selected 8-speaker acceptance target.

