# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `computercraft:speaker` with higher-quality programmable audio while preserving CC:T's native speaker behavior.

Target: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.x. The release is built once against **NeoForge 21.1.247** and declares `[21.1,21.2)`.

## Current release-candidate status

Runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Runtime-tested candidate CI: `36238699768` — PASS  
Runtime-tested artifact: `10905071824`  
Runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Network protocol: **v10**, 9 payloads.

A1-A19 and R1-R9 have passed in real Minecraft. C3 MP3/ICY radio + strict membership also passed.

Release acceptance is still blocked by **runtime recheck evidence**, not by an unresolved architecture redesign:

- C1's false-negative assertion is fixed but needs a clean rerun;
- direct SPR `processSound` diagnostics are implemented, and C2 now tests a normal-world wall plus long-running refresh vs restart;
- the 8-speaker finite admission stampede has a source fix which shares the server's per-player request budget fairly, but C4 must prove it in Minecraft;
- C4 now runs RAW independently, so the missing 8+ RAW evidence can finally be collected.

SPR remains optional/client-side. The server and protocol behave the same whether clients use SPR or not.

See `docs/RUNTIME-INVESTIGATION-2026-09-27.md` for the forensic reconstruction.

## Frozen v10 product

The normal CC:T speaker is the only block product. Supported playback:

- native CC:T `playNote`, `playSound`, `playAudio`, `stop`;
- modern finite MP3 + supported common WAV;
- prepared finite media on the same modern engine;
- signed-16 mono RAW PCM at 48 kHz;
- MP3/ICY internet radio: singular, All and At;
- multispeaker finite playback with one shared authority and independent endpoints.

Finite multispeaker membership is a start-time snapshot. Shared playback controls stay shared; volume/mute are endpoint-local; `audioStopAt(index)` detaches only that endpoint.

Grouped radio is also strict-snapshot: late speakers join only after rerunning the stream command.

All HQ positional paths resolve Sable Companion -> VS2 -> static block center.

## Runtime diagnostics

The release JAR contains dormant diagnostics for real Minecraft/OpenAL channels, PCM, recovery, sync, movement and SPR integration.

The current SPR diagnostics are being strengthened so acceptance records actual `SoundPhysics.processSound` execution rather than only environment writes.

## Runtime acceptance

Use:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

The selected target is singleplayer + Sable/Aeronautics + Sound Physics Remastered. Dedicated-server/multiplayer and VS2 are outside this release acceptance target.

Do not declare release-ready until the current target blockers are fixed and rerun.
