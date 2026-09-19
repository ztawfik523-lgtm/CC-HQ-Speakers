# Legacy API convergence

Updated: 2026-09-19

This file records the current migration boundary after M1J. It is not a promise to preserve every historical helper forever.

## Principles

- One modern finite engine should own finite MP3/WAV playback.
- Standard CC:T note/sound/audio stays native CC:T.
- HQ RAW PCM remains producer-fed and separate from finite songs.
- Do not pull OGG/AIFF/AU/live-radio requirements into the modern finite core merely to preserve old names.
- Preserve compatibility where the frontend can be mapped truthfully without retaining a parallel engine.
- Do not perform media import/analyze work on the Minecraft server tick thread.

## Method matrix

| API family | Direction | Notes |
| --- | --- | --- |
| `playNote`, `playSound`, `playAudio`, `stop` | Keep native | Standard CC:T contract. |
| Standard `*All` / `*At` | Keep, native dispatch | Composite now dispatches each real CC:T speaker; old fake-sine/HQ-PCM implementations are bypassed. |
| `speakPCM` | Keep separate | Open-ended signed-16 producer-fed RAW source with bounded backpressure. |
| `hq.playFile` / prepared APIs | Keep primary | Modern bounded finite MP3/common-WAV path. |
| `speakMp3` / `speakWav` | Candidate for compatibility bridge | Import exact bytes as a temporary MediaAsset, analyze off-thread, then commit modern playback on the main thread. |
| `speakOgg` | Hold legacy | Modern OGG requires a deliberate progressive/seek/rejoin implementation. |
| `speakAudio` / `speakFile` / `speakPacked` | Product decision later | Generic historical aliases have a broader/ambiguous format promise. |
| Legacy finite `*All` / `*At` | Migrate together with their singular frontend | Do not retain the expected-member barrier for modernized formats. |
| `speakStream` / HLS / TS / ICY | Future optional | Not part of finite convergence. |
| `speakSupportedFiles` | Legacy-only capability | Use `hq.preparedFormats(speaker)` for modern support. |

## Safe MP3/WAV bridge shape

The existing primitives are sufficient; do not create another storage system:

```text
Lua byte string/table
  -> copy and validate on ComputerCraft thread
  -> MediaAssetStore.importAsset(... ReadableByteChannel)
  -> ModernFiniteMediaAnalyzer
  -> prepared modern finite start token
  -> ILuaContext.executeMainThreadTask(...)
       -> short ownership replacement + commit
  -> release temporary import-owner reference
```

The import-owner reference and the playback reference are distinct. Every rejection, supersession, detach, and main-thread commit failure needs an explicit release path.

This bridge should be implemented only for formats already supported by the modern analyzer/decoder. It must not turn `speakOgg` or generic `speakAudio` into misleading aliases.
