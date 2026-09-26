# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `computercraft:speaker` with higher-quality programmable audio while preserving CC:T's native speaker behavior.

Target: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.x. The release is built once against **NeoForge 21.1.247** and declares `[21.1,21.2)`.

## Current release-candidate status

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
Current CI: `36205257110` — PASS on the single NeoForge 21.1.247 build  
Current artifact: `10892998704`  
Current JAR SHA-256: `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`  
Network protocol: **v10**, exactly 9 payloads.

Historical pre-freeze CI also built 21.1.248. That remains historical compatibility evidence; current CI intentionally produces only the 21.1.247-built artifact.

## Frozen v10 product

The normal CC:T speaker is the only block product. The standalone `hqspeaker:hq_speaker` block is removed. Internal custom audio uses `hqspeaker:hq_audio_source`. License: MPL-2.0.

Supported playback:

- native CC:T `playNote`, `playSound`, `playAudio`, `stop`;
- modern finite MP3 + supported common WAV;
- MP3/WAV compatibility byte methods on the same modern finite engine;
- signed-16 mono RAW PCM at 48 kHz;
- MP3/ICY internet radio: singular, All and At;
- multispeaker finite playback with one shared authority and independent physical endpoints.

Retired: OGG/generic whole-file aliases, HLS, MPEG-TS, the duplicate finite engine, stale compatibility-control aliases, and the standalone HQ block.

## Core semantics

Finite multispeaker membership is a start-time speaker snapshot. Pause/resume/seek/loop and ordinary/All stop operate on shared playback. Volume and mute are endpoint-local. `audioStopAt(index)` stops/detaches only that physical endpoint.

Grouped MP3 radio is also strict-snapshot. Late/new speakers do not auto-join; rerun the stream command to create a new group which includes the new membership.

RAW `speakPCMAll` preflights the target snapshot and uses a common future start tick.

All HQ positional paths use the same resolver: Sable Companion first, VS2 second, static block center otherwise.

## Recommended finite API

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Prepared media is stored server-side as immutable encoded assets. Defaults are 512 MiB per asset and 2048 MiB total; server config can change those limits.

## Built-in runtime diagnostics

The normal release JAR includes dormant diagnostics used by the final v10 acceptance runner. They are off during ordinary play and explicitly enabled by `hqDiagEnable(true)`.

The diagnostic layer observes the actual Minecraft/OpenAL playback path rather than trusting only server/Lua state. It records real client channels, play/pause/stop state, PCM delivery, buffer health, source position, playback clocks/latency where supported, decoder/recovery activity, sound-engine reloads, multispeaker timing, Sable movement and Sound Physics processing.

Diagnostic Lua methods:

- `hqDiagEnable(boolean)`
- `hqDiagReset()`
- `hqDiagSnapshot()`
- `hqDiagCapabilities()`

These do not add a protocol payload or alter normal playback semantics.

## Runtime acceptance

Use one user-facing runner:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url]
```

The current target scope is **singleplayer + Sable/Aeronautics + Sound Physics Remastered**. Dedicated-server/multiplayer and VS2 are intentionally outside this runtime acceptance target.

The operator performs physical actions when prompted—walk out of range, press F3+T, move the Sable contraption, move behind an obstacle, connect speakers—but the diagnostics determine PASS/FAIL.

Runtime acceptance has **not yet been declared complete**. CI proves build/tests/package structure, not real in-game playback.

See `docs/RUNTIME-ACCEPTANCE-V10.md` and `docs/HANDOFF-2026-09-26-DIAGNOSTIC-RUNTIME.md`.
