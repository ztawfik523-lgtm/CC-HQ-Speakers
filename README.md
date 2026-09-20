# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `speaker` peripheral with higher-quality programmable audio while preserving the standard CC:T speaker contract.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline / 21.1.248 compatibility

## Fork and license

Repository lineage:

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

The inherited repository license is Mozilla Public License 2.0. This fork remains MPL-2.0. Upstream also shipped an inconsistent LGPL-3.0 mod-metadata label; this fork corrected NeoForge metadata to `MPL-2.0`.

## Product direction

The normal `computercraft:speaker` is the **only** speaker block product.

The inherited standalone `hqspeaker:hq_speaker` block/item/block entity/registry path was removed. The internal custom SoundManager anchor is now named `hqspeaker:hq_audio_source` so it cannot be confused with a block.

Lua decides whether audio is music, speech, alarms, notifications, ambience, soundboards, or something else. Java exposes playback mechanics and truthful capabilities.

One physical speaker remains one mono positional source.

## Current status

Current green source checkpoint:

- `fe880002b387f329d39a7372af521f1eacce559a`
- CI `35529480491`
- NeoForge 21.1.247: PASS
- NeoForge 21.1.248: PASS

Current protocol: **v9**, 9 payloads.

Completed at source/test/CI/package level:

- modern bounded finite engine;
- post-M1G lifecycle/storage hardening;
- dynamic listener admission/leave/rejoin;
- renderer/reload/starvation recovery;
- Sable/Aeronautics + VS2 moving-source support;
- modern multispeaker shared playback;
- MP3/WAV compatibility-name migration to the modern engine;
- removal of OGG/generic whole-file aliases and duplicate finite engine;
- RAW multispeaker admission without the old expected-member barrier;
- obsolete JavaSound MP3 SPI dependency removal;
- standalone speaker block removal;
- MPL metadata correction;
- internal sound-resource rename;
- finite/multispeaker concurrency hardening;
- dead legacy singular standard/RAW implementation cleanup while preserving the public composite API.

Focused M1H/M1J Minecraft checks remain in the runtime backlog.

## Finite playback

Recommended Lua path:

```lua
local speaker = peripheral.find("speaker")
local hq = require("hqspeaker")

hq.playFile(speaker, "/music/song.mp3", { volume = 0.6 })
```

Supported modern finite formats:

- MP3
- supported common WAV

Current finite pipeline:

```text
ComputerCraft file or compatibility byte payload
-> immutable server MediaAsset
-> server analysis / codec descriptor
-> one server-authoritative playback authority
-> protocol v9 bounded range transport
-> progressive client decode
-> one shared client playback projection
-> independent positional SoundManager source per physical speaker
```

Multispeaker starts snapshot the speakers attached to the calling computer. There is no expected-member barrier. Removing/replacing one endpoint does not kill the other endpoints.

Shared controls: play/pause/resume/seek/loop/stop.  
Endpoint-local controls: volume/mute.  
Explicit `*All` and `*At` variants apply endpoint controls as requested.

Legacy names `speakMp3` / `speakWav` and their All/At variants remain as compatibility frontends but now use the same modern finite engine.

OGG and generic whole-file aliases are retired.

## Standard CC:T behavior

The composite delegates normal `playNote`, `playSound` and `playAudio` to CC:T's real `SpeakerPeripheral`. Grouped/indexed standard helpers are intercepted by the composite and also dispatch to the real CC:T speakers, preserving requested instruments, sound IDs and native DFPWM behavior.

Native `speaker_audio_empty` remains CC:T-owned.

## HQ RAW

`speakPCM` is a separate producer-fed signed-16 PCM path:

- 48 kHz;
- maximum 131072 samples per call;
- bounded queue/backpressure;
- `hqspeaker_audio_empty` for producers which actually observed rejection;
- singular, All and At forms;
- All preflights every target endpoint before replacement and uses a common future start tick without a global expected-member barrier.

RAW is intentionally not represented as a fake finite song with seek/duration/loop semantics.

## Optional live streaming

MP3 stream / ICY, HLS and TS helpers remain optional legacy/future features. They are not core release blockers.

Known inherited HLS progression concerns remain. Grouped live-stream helpers still retain legacy sync-group metadata, so that code is not dead yet.

## Moving speakers

M1H-3 uses local movement resolution:

1. Sable Companion / Aeronautics-style sublevels;
2. VS2 transform fallback;
3. normal static block center.

No continuous position packets are sent.

Focused Sable/Aeronautics and VS2 runtime acceptance remains pending. The earlier Sable parent-world Level-identity concern was closed by source-model verification; runtime still needs to prove actual moving-source behavior.

## What is next

Core finite architecture is no longer the main task.

Near-term non-runtime work:

1. keep live-stream sync machinery while grouped live helpers still use it and decide whether optional grouped live is worth keeping/fixing;
2. continue exact dead-code/API/dependency cleanup;
3. packaging/dependency/docs/CI/default-branch release hygiene.

Then run the deferred integrated Minecraft acceptance/stress pass covering listener lifecycle, recovery, movement, multispeaker behavior, endpoint replacement, malformed media, bounds, Sound Physics Remastered, and realistic 2/4/8+ speaker cost.

Shared decode fan-out stays deferred unless profiling shows duplicated client decode is materially expensive.

## Documentation

Start with:

1. `docs/HANDOFF-2026-09-20-POST-CONVERGENCE.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/ROADMAP.md`
8. `docs/LUA-API.md`

For a fresh chat, use `docs/HANDOFF-PROMPT-2026-09-20.md` or `docs/NEXT-CHAT-PROMPT.md`.

Historical dated milestone documents remain evidence for their checkpoints but do not override current docs.
