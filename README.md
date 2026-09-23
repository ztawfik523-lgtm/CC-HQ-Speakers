# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `computercraft:speaker` with higher-quality programmable audio while preserving CC:T's native speaker behavior.

Target: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.x. The release is built once against 21.1.247; the mod metadata accepts the 21.1.x line (`[21.1,21.2)`).

## Frozen v10 release surface

Source freeze checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
Source-freeze CI: `35655973164` — both NeoForge targets passed build, deterministic tests, package verification and artifact upload.  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`.  
Runtime-prep head: `a234ba02b80532daf32f6849061b76f23c0eb4d3` / CI `35657182389` — PASS on both targets.  
Network protocol: **v10**, 9 payloads.

The normal CC:T speaker is the only block product. The inherited standalone `hqspeaker:hq_speaker` block is removed. Internal custom audio uses `hqspeaker:hq_audio_source`. License: MPL-2.0.

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

Grouped MP3 radio is also strict-snapshot: the command defines the participating server endpoints, each client collects only the speakers it actually receives before the seal deadline, one shared decoder/prebuffer is used for that local group, and late/new speakers do not auto-join. Rerun the command to create a new group.

RAW `speakPCMAll` preflights the whole target snapshot and uses a common future start tick without an expected-member barrier.

All HQ positional paths now use the same movement resolver: Sable Companion first, VS2 second, static block center otherwise.

## Recommended finite API

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")
hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Prepared media is stored server-side as immutable encoded assets. Defaults are 512 MiB per asset and 2048 MiB total; server config can change those limits.

## Built-in runtime diagnostics

The normal release JAR now carries a dormant diagnostic subsystem used by the v10 acceptance runner. It is off during ordinary use and is enabled explicitly by the test through `hqDiagEnable(true)`.

While enabled it observes the actual client audio channels and records OpenAL play/pause/stop state, queued/processed buffers, source position, playback offset/latency when supported, decoded/RAW PCM delivery, renderer restarts/recovery, multispeaker start skew, sound-engine reloads, Sable source movement and Sound Physics direct-filter application. In singleplayer the integrated server and client share this diagnostic state in-process, so protocol v10 remains at exactly 9 payloads.

Diagnostic Lua surface: `hqDiagEnable`, `hqDiagReset`, `hqDiagSnapshot`, `hqDiagCapabilities`. These methods are test instrumentation and do not change normal playback ownership or command ordering.

## Evidence boundary

Current CI builds/tests/packages only against NeoForge 21.1.247. That single artifact is the release artifact for the supported NeoForge 21.1.x metadata range; older dual-build CI remains historical evidence, not a continuing build requirement. The built-in diagnostic runner adds real-client/OpenAL evidence, but runtime acceptance still requires launching Minecraft and performing physical actions which cannot be simulated in CI, such as moving a Sable contraption, leaving listener range and pressing F3+T.

See `docs/API-FREEZE-V10.md`, `docs/RUNTIME-ACCEPTANCE-V10.md`, then `docs/HANDOFF-2026-09-22-RUNTIME.md`.
