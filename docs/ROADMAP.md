# Roadmap

Updated: 2026-09-28

Branch baseline: `codex/m1j-multispeaker` at `74a49440dfd7a2aa1ddf83307600a9729ed3f0c5`.

This roadmap supersedes the older “v10 is frozen / no HQ refresh planned” wording. Runtime work proved that long-lived HQ sources need their own efficient SPR reevaluation path, and the product is now intentionally adding the agreed volume/range model plus the previously accepted acoustic tuning work.

Build/test/package only NeoForge **21.1.247**. Keep metadata compatibility `[21.1,21.2)`; do not add a separate 21.1.248 artifact.

## Already established

- A1-A19 deterministic matrix and R1-R9 real-client core matrix passed on the selected target.
- C1 Sable tracking passed.
- C3 grouped MP3/ICY radio + metadata + strict membership/rerun passed.
- C4 RAW passed at 8 speakers.
- C4 finite admission stall was fixed; corrected catch-up alignment is millisecond-scale.
- A8/A9/A18 focused rejection recheck passed.
- The C2 root cause is established: SPR processes an HQ source at start but does not reevaluate a long-lived source as the listener/environment changes unless moving-sound updates are enabled.
- Enabling SPR “Update Moving Sounds” makes HQ occlusion update, confirming that missing reevaluation is the issue.
- Current branch contains the HQ-only movement-gated SPR scheduler candidate: ~0.15-block accumulated movement threshold, ~100 ms moving cadence, settle refresh, fixed ~1 s safety refresh, no catch-up bursts, and at most one expensive SPR refresh task globally at a time.
- Current server config infrastructure already exists in `HQSpeakerServerConfig`; it should be extended, not replaced.

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

Starting gain anchors may use a simple monotonic 0..1 curve, but gain tuning is not acoustically frozen until runtime listening. Minecraft clamps normal SoundInstance gain to 1.0, so the default server gain anchors must remain within 0..1 unless we deliberately replace Minecraft's gain ownership later.

## Server config

Extend the existing NeoForge SERVER config with an `audio` section.

Planned controls:

```text
defaultVolume = 1.5
maxVolume = 3.0
allowRangeOverride = true
maxRange = 512.0

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

## Implementation order

1. **Audio tuning core**
   - Add one immutable server-side tuning-profile/snapshot type.
   - Implement anchor interpolation once and reuse it everywhere.
   - Resolve logical volume -> source gain + automatic range.
   - Validate server limits and explicit range overrides with Lua errors.

2. **Protocol v11, still 9 payloads**
   - Packet shape must change because clients need the server-resolved gain/range.
   - Keep exactly the existing 9 payload types; do not add a separate config/tuning payload.
   - Finite BEGIN/STATE carry resolved gain/range.
   - RAW/radio audio packets carry resolved gain/range.
   - Server remains authoritative; clients do not independently reinterpret the server config.

3. **Replace the hard 32-block HQ reach**
   - Current finite listener relevance and legacy RAW/radio delivery still use a fixed 32-block server radius; this must follow each source's resolved range.
   - Finite listener membership uses the endpoint's current effective range.
   - RAW delivery uses the current resolved range and preserves enough previous reach to deliver a shrink/update/stop correctly.
   - Radio start/metadata/stop use the radio source's snapshotted range.
   - Do not confuse media range-read transport with audible speaker range.

4. **Client gain + attenuation**
   - Replace finite's hardcoded `linearAttenuation(32)`.
   - Explicitly apply the resolved range to finite, RAW and radio channels.
   - Keep Minecraft/OpenAL linear-clamped distance rolloff for now: the configured range is the fade-to-zero distance.
   - Apply the server-resolved gain as the sound source gain.
   - Range changes on an active finite source update the existing OpenAL channel; no restart.

5. **Lua range control**
   - Add optional per-start `range` alongside `volume` for modern finite helpers and HQ byte/RAW/radio starts.
   - Add finite endpoint-local `audioSetRange`, `audioSetRangeAll`, and `audioSetRangeAt`.
   - Calling the range control without a value returns that endpoint to automatic volume-derived range.
   - Extend `audioStatus` with logical volume, resolved gain, resolved range, and auto/explicit range mode.
   - Keep existing shared-vs-endpoint semantics: range, like volume/mute, is endpoint-local.

6. **Acoustic tuning port**
   - Keep the current Minecraft-owned HQ playback engine and the new HQ-only SPR refresh scheduler.
   - Port the user-approved Beta3/Beta5 direct acoustic behavior, not the old playback engine:
     - progressive 17-probe occlusion geometry;
     - exact center/inner/outer weighting and center-path gate;
     - adaptive 9/17 probe cache;
     - accepted direct cutoff/gain tuning;
     - accepted muffling/clearing smoothing;
     - reflected-position stabilization.
   - Keep native SPR room/reverb/reflection evaluation authoritative.
   - Do not import Beta7 sentinel/snapshot experiments, the old scheduler, or the old raw-OpenAL playback architecture.
   - Do not restore private EFX ownership unless exact runtime evidence shows the native SPR application path cannot reproduce the accepted direct tuning.

7. **Deterministic tests**
   - Anchor interpolation at anchors and between anchors.
   - volume/range Lua error boundaries.
   - explicit-range override and return-to-auto.
   - config snapshot/new-playback reload behavior.
   - continuous RAW keeps one tuning snapshot across chunks.
   - protocol-v11 codec round trips.
   - finite listener relevance follows effective range.
   - RAW range shrink/grow/stop delivery behavior.
   - client attenuation update behavior.
   - progressive acoustic math/cache/smoothing/stabilizer tests.

8. **Runtime validation**
   - C2: same continuously-playing HQ source must respond to open/wall movement with global SPR “Update Moving Sounds” OFF.
   - Verify automatic range at representative anchors and interpolation points.
   - Verify explicit range overrides volume-derived range.
   - Reload audio config during playback: current source remains unchanged; the next source uses new settings.
   - Recheck finite/RAW/radio gain + range, movement, pause/resume/seek, grouped playback and endpoint-local controls.
   - Recheck 8-speaker finite + RAW scale with the new packet fields.
   - Check long-range SPR behavior beyond the default cloned-world neighborhood; do not add a special far-range fallback unless runtime evidence shows one is needed.

9. **Docs/freeze**
   - Update `CURRENT-STATE.md`, `SERVER-CONFIG.md`, `LUA-API.md`, architecture/known-issues/runtime docs and the API freeze only after the implementation is stable.
   - Freeze the resulting contract as protocol v11.
   - Keep release diagnostics in the normal JAR.

## Important implementation facts found in the recheck

- Minecraft 1.21.1's linear attenuation path uses the supplied attenuation distance as the OpenAL linear-clamped max distance; with HQ's setup, range is naturally the fade-to-zero distance.
- Minecraft clamps normal SoundInstance gain to 1.0, while volume >1 normally affects attenuation distance separately. The new HQ model therefore needs to resolve gain and range separately.
- Current finite playback explicitly overwrites attenuation with 32 blocks.
- Current RAW/radio rely on Minecraft's normal attenuation setup, but server packet delivery is still capped at 32 blocks.
- The server-side 32-block radius is therefore a real blocker for any longer-range design; changing only the client attenuation would not work.
- SPR's default maximum processing distance is large, but its safe cloned-world neighborhood is much smaller. Long audible range is valid; far-distance acoustic quality must be runtime-checked rather than treated as a hard 60-block speaker limit.

## Release/deferred scope

Still outside the selected release target unless deliberately reopened later:

- dedicated-server/multiplayer acceptance;
- VS2 runtime acceptance;
- separate NeoForge 21.1.248 build;
- OGG/FLAC/HLS/MPEG-TS/provider/playlists/standalone HQ block;
- >8 streamed-source guarantee beyond the already selected 8-speaker acceptance target.

No unrelated cleanup while the volume/range + SPR/acoustic work is active.
