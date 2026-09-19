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

### Post-M1J finite convergence

Complete: MP3/WAV compatibility names moved to modern engine, OGG/generic whole-file aliases retired, duplicate finite engine/payloads removed, protocol v9, mp3spi/Tritonus removed.

### RAW convergence

Complete for the current admission model: singular/All/At share bounded admission, group preflight, common future start tick, no expected-member barrier.

### Product/metadata cleanup

Complete: standalone HQ block removed, normal CC:T speaker is sole block surface, MPL-2.0 metadata corrected, internal sound ID renamed to `hqspeaker:hq_audio_source`.

## Active non-runtime phase — release/API cleanup

Priorities:

1. re-verify and remove dead `HQSpeakerPeripheral` standard All/At bodies/helpers;
2. finish RAW/public contract cleanup;
3. contain optional live code without letting it drive core architecture;
4. exact dead-code/dependency/package cleanup;
5. docs/API freeze;
6. CI/default-branch/repository hygiene where useful.

Do not delete live sync-group code while grouped `speakStreamAll` / `speakHLSAll` / `speakTSAll` still use it.

## Runtime/integration phase

When the owner is ready, batch:

- M1H range entry/leave/re-entry;
- reload/renderer-loss/starvation recovery;
- Sable/Aeronautics + VS2;
- Sable sublevel parent-world relevance;
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
