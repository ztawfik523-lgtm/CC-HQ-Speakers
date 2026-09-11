# Current state

## Active references

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Untouched inherited fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

Reviewed M1 reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

M1A local-file prototype reference:

`69e34a5346f6ce47580f49ed867c9951bfd338bc`

Current cleanup/redesign preparation branch:

`codex/m0.5-cleanup-prep`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

The mod upgrades the normal CC:Tweaked speaker into a **programmable ComputerCraft audio peripheral**. Java exposes capabilities; Lua owns application policy such as playlists, priorities, alarms, notifications, music-player behavior, and sequencing.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite files with a truthful timeline;
- live/open-ended network streams later.

## What the M1A prototype proved

The frozen `69e34a5` prototype established that the following are viable on the target stack:

- standard CC:T calls can be delegated to CC:T's original `SpeakerPeripheral` while HQ extensions remain exposed through a composite peripheral;
- a writable ComputerCraft mount can stage local CC files without `readAll()`;
- client-bound finite media can be split into 256 KiB chunks;
- the client can keep encoded media on disk instead of retaining the complete decoded track;
- OGG/JavaSound finite media can be consumed incrementally from a file-backed stream;
- a bundled `hqspeaker.lua` ROM helper can expose a simple `hq.playFile(speaker, path)` interface;
- pure tests cover finite clock and staged path behavior.

The prototype built successfully on NeoForge 21.1.247 and 21.1.248 before M0.5 started.

## Prototype concepts scheduled for replacement

Do **not** treat these as the target architecture:

- fixed player recipient set captured when playback starts;
- server-push transfer for the entire encoded file;
- media identity/lifetime owned by one physical speaker;
- client READY/STARTED/ENDED reports acting as canonical playback authority;
- renderer-observation timeout;
- successful-renderer/anchor-style state;
- delete-on-initial-transfer behavior;
- expected-group-size/expected-tap synchronization.

M0.5 deliberately does not polish these concepts because the accepted redesign removes them.

## Accepted finite-media direction

The target model is:

```text
ComputerCraft file
    -> staging/import
    -> reusable server media asset
    -> server-authoritative playback state
    -> small state snapshots
    -> bounded client-pulled asset ranges
    -> reusable encoded client cache
    -> incremental decoder/shared PCM timeline
    -> one positional renderer per audible physical speaker
```

Important consequences:

- no Java playlist;
- new incompatible HQ continuous playback replaces the prior HQ continuous playback;
- no permanent historical listener list;
- finite media asset is separate from playback and physical speaker;
- server owns finite position/pause/loop/EOF;
- clients dynamically join/leave rendering according to current range/tracking state;
- multispeaker playback shares asset/timeline work without collapsing physical source positions;
- large local files are a core requirement, not a later optional extension.

See `docs/ROADMAP.md` for implementation order.

## Standard CC:T compatibility

The composite peripheral currently delegates standard methods to CC:T's actual speaker implementation:

- `playNote`
- `playSound`
- `playAudio`
- `stop`

The legacy HQ synthetic `speaker_audio_empty` event is filtered at the composite boundary so native `speaker_audio_empty` remains CC:T's backpressure event.

Known follow-up work remains around the interaction between standard CC sources and HQ continuous playback, but standard behavior itself should continue to be delegated rather than reimplemented.

## M0.5 work

M0.5 is cleanup/preparation, not the finite-engine redesign itself.

Current goals:

- freeze the prototype reference and work on a separate redesign branch;
- deterministic provider cache cleanup on server Level unload and server shutdown;
- stop relying on a weak-key cache whose values strongly reference the same Level;
- update agent/design/roadmap guidance so obsolete P0 choices are not revived;
- verify the bundled ComputerCraft Lua module in packaged JARs;
- preserve current unit tests and dual-NeoForge CI;
- avoid repairing prototype recipient/renderer-timeout/client-authority concepts which are scheduled for deletion.

## Known work intentionally deferred past M0.5

The following remain real issues but are not prerequisites for the cleanup milestone:

- migrate legacy finite byte APIs onto the new future asset engine;
- replace the 8 MiB whole-byte finite path for local-file use;
- server media metadata/duration analysis;
- client-pulled range transfer;
- reusable client asset cache;
- efficient asynchronous MP3 seek/indexing;
- dynamic range-based renderer lifecycle;
- multispeaker asset/timeline sharing;
- HQ raw feed backpressure/lifecycle cleanup;
- stream double-volume/HLS/TS/live-state problems;
- SPR integration;
- dead custom HQ block decision;
- MPL/LGPL metadata mismatch resolution before public release.

## Testing state

Pure Java tests currently cover:

- retained-M1 `FiniteAudioTrack` behavior;
- HLS playlist parsing;
- `FinitePlaybackClock` behavior;
- `FiniteMediaPath` validation.

Runtime scripts include standard CC:T speaker-contract acceptance and the M1A staged finite prototype test. Do not run the staged prototype test as final architecture acceptance after the asset redesign begins; it remains useful only as historical/prototype evidence until rewritten.

CI must continue to build NeoForge 21.1.247 and 21.1.248 with Java 21 and verify required packaged resources, including the bundled `data/computercraft/lua/rom/modules/main/hqspeaker.lua` module.
