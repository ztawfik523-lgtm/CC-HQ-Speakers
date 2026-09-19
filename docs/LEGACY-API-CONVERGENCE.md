# Legacy API convergence

Updated: 2026-09-20

Finite API convergence is complete.

## Result

There is one supported finite engine.

| API family | Current state |
| --- | --- |
| `playNote` / `playSound` / `playAudio` / `stop` | Native CC:T path |
| Standard All/At note/sound/audio | Composite dispatches to real CC:T speakers |
| `speakPCM` / All / At | Separate bounded RAW path |
| `hq.playFile` / prepared APIs | Primary modern finite path |
| `speakMp3` / `speakWav` / All / At | Compatibility frontends routed to modern finite |
| `speakOgg` / All / At | Removed |
| generic `speakAudio` / `speakFile` / `speakPacked` / All / At | Removed |
| live MP3/HLS/TS/ICY | Separate optional legacy/future path |
| `speakSupportedFiles` | Reports current finite set: mp3, wav |
| `hq.preparedFormats(...)` | Structured modern finite capability: mp3 + wav |

## MP3/WAV compatibility bridge

```text
Lua byte payload
-> copy/validate on ComputerCraft thread
-> temporary immutable MediaAsset
-> ModernFiniteMediaAnalyzer
-> prepared finite admission
-> short main-thread ownership/commit task
-> release temporary importer reference
```

The playback keeps its own retained asset reference.

## Removed duplicate finite engine

Removed:

- inherited whole-file finite server timeline;
- whole-file finite client decoder;
- `FiniteAudioTrack`;
- old finite control/status packets;
- old finite expected-member synchronization;
- JavaSound MP3 SPI dependencies.

Current protocol is v9.

## Standard All/At cleanup note

The composite already provides correct supported behavior for standard grouped/indexed note/sound/audio calls.

Old fake implementations remain in `HQSpeakerPeripheral` as dead-code candidates. Do not confuse those bodies with the exposed contract.

## Live-path boundary

Grouped optional live streaming still uses legacy sync-group metadata. That code is separate from completed finite convergence and should be retained until live grouped behavior is redesigned or retired.
