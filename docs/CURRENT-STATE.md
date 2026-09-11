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

Completed M0.5 preparation reference:

`ad38412a2173f849a0fc8e867030da8a78965c9c`

Current M1A implementation branch:

`codex/m1a-compat-output`

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

## What the frozen local-file prototype proved

The frozen `69e34a5` prototype established that the following are viable on the target stack:

- standard CC:T calls can be delegated to CC:T's original `SpeakerPeripheral` while HQ extensions remain exposed through a composite peripheral;
- a writable ComputerCraft mount can stage local CC files without `readAll()`;
- client-bound finite media can be split into 256 KiB chunks;
- the client can keep encoded media on disk instead of retaining the complete decoded track;
- OGG/JavaSound finite media can be consumed incrementally from a file-backed stream;
- a bundled `hqspeaker.lua` ROM helper can expose a simple `hq.playFile(speaker, path)` interface;
- pure tests cover finite clock and staged path behavior.

The prototype is evidence and implementation material, not the target finite architecture.

## M0.5 result

M0.5 completed the cleanup/redesign preparation layer before M1A:

- prototype branch preserved unchanged;
- deterministic composite cleanup on speaker removal, server Level unload, and server shutdown;
- repository guidance rewritten around accepted architecture rather than obsolete P0 choices;
- packaged JAR verification now checks the bundled `hqspeaker.lua` ROM module;
- exact M0.5 HEAD built successfully on NeoForge 21.1.247 and 21.1.248.

See `docs/M0.5-CLEANUP.md`.

## M1A current source state

M1A is the compatibility/output-ownership milestone. It does not yet implement the new reusable finite-media asset system.

Current source behavior on `codex/m1a-compat-output`:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain delegated to CC:T 1.120.0's real `SpeakerPeripheral`;
- synthetic inherited HQ `speaker_audio_empty` events are suppressed;
- the normal single physical speaker has one explicit HQ continuous owner: RAW, legacy finite, staged finite prototype, or stream intent;
- calls which change that owner run one at a time on a physical speaker, preventing two connected computers from interleaving replacement state;
- a new incompatible HQ start replaces the prior HQ source; Java does not create a playlist;
- repeated `speakPCM` calls continue the current RAW feed;
- native notes remain independent;
- native `playSound` / `playAudio` return `false` while HQ continuous output owns the speaker rather than overlapping it;
- `stop()` ends both native CC sound/audio state and HQ continuous state;
- `audioStop()` truthfully stops the current HQ source, including RAW/stream intent;
- `audioStatus()` is routed by current HQ ownership instead of stale terminal state from another subsystem;
- RAW reports open-ended capabilities instead of fake duration/seek/loop support;
- `speakMaxSamples()` reports the real inherited contiguous table ceiling of `131072`;
- RAW admission is bounded by both the inherited 16-packet server queue and a duration allowance of `135872` outstanding samples: one maximum `speakPCM` call plus 100 ms of headroom;
- rejected valid `speakPCM` writers receive the separate `hqspeaker_audio_empty` event only when their requested chunk can fit both the packet queue and the sample-duration allowance;
- the RAW allowance drains by `2400` samples per server tick at 48 kHz/20 TPS, preventing normal producers from feeding multi-second chunks every Minecraft tick and building a huge client backlog;
- malformed/empty/oversized RAW tables still fail validation instead of being presented as ordinary backpressure;
- RAW drain lifetime is sample-derived in server ticks and eventually releases the otherwise-silent inherited client source.

`hqspeaker_audio_empty` is producer/server admission control, not a promise that every listener has physically played previous samples. The client RAW queue remains bounded and may discard stale PCM under pathological network/client conditions rather than allowing unlimited delay.

Source/CI success is **not** Minecraft runtime proof. The acceptance scripts still need to be run on the target stack before M1A is declared runtime-complete.

See `docs/M1A-OUTPUT-OWNERSHIP.md` and `docs/CC-T-COMPATIBILITY-CONTRACT.md`.

## Multi-speaker boundary during M1A

The inherited `*All` / `*At` helpers directly call old `HQSpeakerPeripheral` instances and bypass the new composite ownership boundary.

M1A deliberately does not retrofit them. They still carry the inherited expected-group-count/sync architecture and are scheduled for replacement by M1J's shared asset/timeline + per-physical-speaker renderer design.

Do not infer single-speaker M1A ownership guarantees for those helpers yet.

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

M1A does not polish these concepts because the accepted finite redesign removes them.

## Accepted finite-media direction

The target model after M1A is:

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

## Known work after M1A

The following remain intentionally outside the current compatibility/output slice:

- reusable server media asset manager;
- replace the 8 MiB local-file architecture with asset/range transfer;
- server media metadata/duration analysis;
- client-pulled range transfer and reusable client cache;
- server-authoritative finite clock/EOF;
- efficient asynchronous MP3 seek/indexing;
- dynamic range-based renderer lifecycle, including clients which walk away before a legacy stop packet is sent;
- multispeaker asset/timeline sharing and `*All` / `*At` replacement;
- migrate legacy finite byte APIs onto the new finite engine;
- richer RAW pause behavior if later justified;
- stream double-volume/HLS/TS/live-state problems;
- sound-category normalization;
- SPR integration;
- dead custom HQ block decision;
- MPL/LGPL metadata mismatch resolution before public release.

## Testing state

Pure Java tests currently cover:

- retained-M1 `FiniteAudioTrack` behavior;
- HLS playlist parsing;
- `FinitePlaybackClock` behavior;
- `FiniteMediaPath` validation;
- RAW outstanding-sample accounting, capacity checks, server-tick drain, idle grace, and invalid-capacity arguments.

Runtime scripts relevant to the current milestone:

- `scripts/p0_cc_speaker_contract.lua` — standard CC:T compatibility;
- `scripts/m1a_output_contract.lua [optional-small-mp3]` — HQ ownership, RAW backpressure/event, replacement, stop, and idle release.

The staged prototype runtime script remains useful as historical/prototype evidence but is not final architecture acceptance.

CI must continue to build NeoForge 21.1.247 and 21.1.248 with Java 21 and verify required packaged resources, including the bundled `data/computercraft/lua/rom/modules/main/hqspeaker.lua` module.
