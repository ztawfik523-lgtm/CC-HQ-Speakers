# Roadmap

Updated: 2026-09-20

## Product rule

Build a better programmable ComputerCraft speaker peripheral.

The normal `computercraft:speaker` is the only block product. Lua owns application meaning and policy. Java owns truthful playback mechanics, lifecycle and safety.

## Completed

### M1E-M1G — modern finite engine

Complete: server-authoritative finite state/time, immutable MediaAssets, bounded range transport, progressive MP3/common-WAV decode, codec-aware seek/rejoin, SoundManager rendering, fixed 32-block core radius, and focused M1G audible/core Minecraft PASS on NeoForge 21.1.247.

### Post-M1G hardening

Complete: KI-062 DNS/server-lock coupling, KI-063 admit-before-replace, KI-054 shutdown/root-lock retry, KI-064 import no-progress/rename fallback.

### M1H — listeners, recovery, movement

Source/test/CI/package complete. Focused runtime acceptance remains deferred.

### M1J — modern finite multispeaker

Source/test/CI/package complete: one shared playback authority, independent physical endpoints, start-time snapshot, no expected-global-member barrier, shared timeline/control, endpoint-local volume/mute, client shared playback projection.

Focused runtime acceptance remains deferred.

### Finite/multispeaker concurrency hardening

Complete in source through `68314efe2e7ccbaa73e273044389ea43fea70530` / CI `35478810268`.

Finite endpoint fanout no longer nests endpoint monitors. Modern/core multi-endpoint starts and controls use stable ordered target reservations; finite/RAW All replacement keeps the complete snapshot reserved through commit; controls invalidate delayed older stream starts on every affected modern endpoint; shared playback identity is revalidated under reservation. Deterministic lock-order/overlap tests are included.

Real-Minecraft concurrent-control stress remains part of runtime acceptance.

### Post-M1J finite convergence

Complete: MP3/WAV compatibility names moved to modern engine, OGG/generic whole-file aliases retired, duplicate finite engine/payloads removed, protocol v9, mp3spi/Tritonus removed.

### RAW convergence

Complete for the current admission model: singular/All/At share bounded admission, group preflight, common future start tick, no expected-member barrier.

### Product/metadata cleanup

Complete: standalone HQ block removed, normal CC:T speaker is sole block surface, MPL-2.0 metadata corrected, internal sound ID renamed to `hqspeaker:hq_audio_source`.

### Dead grouped/indexed implementation cleanup

Complete: obsolete legacy standard All/At bodies were removed while the composite explicitly retained the public method names; obsolete legacy RAW `speakPCMAll/speakPCMAt` duplicates and their now-unreferenced wrappers were also removed. Optional live sync machinery remains because grouped live helpers still use it.

Dead singular legacy fake standard playback and shadowed legacy `speakPCM` were also removed at `2377aae3bde94d3393f21525697198fb8645f354`, while the composite explicitly retains the supported public names.

Unreachable post-routing fallbacks and the now-obsolete generic 8-bit table conversion path were removed at `47976d92e3ba7113f72377969a1f03578f075d7b`.

## Active non-runtime phase — release/API cleanup

Priorities:

1. decide whether stale inherited `speakStopAll/At`, `speakVolumeAll`, and `setLoopingAll` aliases should be removed or made ownership-aware;
2. contain optional live code without letting it drive core architecture;
3. continue exact dead-code/dependency/package cleanup;
4. docs/API freeze;
5. CI/default-branch/repository hygiene where useful.

Do not delete live sync-group code while grouped `speakStreamAll` / `speakHLSAll` / `speakTSAll` still use it.

## Runtime/integration phase

When the owner is ready, batch:

- M1H range entry/leave/re-entry;
- reload/renderer-loss/starvation recovery;
- Sable/Aeronautics + VS2;
- 2/4/8+ modern multispeaker;
- endpoint remove/replace;
- pause/resume/seek/loop;
- per-endpoint mute/volume;
- malformed/extreme media;
- queue/memory/network/worker bounds;
- Sound Physics Remastered;
- final NeoForge 21.1.247 and 21.1.248 acceptance.

## Performance gate

Do not pre-build shared decode fan-out. Current server range work is bounded per player; the likely scaling cost is repeated client codec/PCM/render work. Only optimize if realistic profiling proves it materially significant.

## Optional feature bucket

Not core blockers:

- modern OGG;
- FLAC;
- AAC/other codecs;
- internet radio/ICY/HLS/TS repairs;
- Spotify/YouTube/provider integration;
- native ordinary Create contraption lifecycle;
- gapless playback.

Evaluate each independently against actual value and maintenance cost.
