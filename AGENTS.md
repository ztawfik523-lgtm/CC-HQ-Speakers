# CC:HQ Speakers — agent guide

Updated: 2026-09-20

## Current authority

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

Current green source checkpoint:

- source: `47976d92e3ba7113f72377969a1f03578f075d7b`
- CI: `35481364704`
- NeoForge 21.1.247: PASS
- NeoForge 21.1.248: PASS

Current network protocol is **v9** with 9 payloads.

M1E through M1J are complete at source/test/CI/package level. M1G has focused audible/core Minecraft proof on NeoForge 21.1.247. Focused M1H listener/recovery/movement checks and focused M1J multispeaker Minecraft acceptance remain deferred to the runtime backlog; they were not rejected.

Post-M1J finite convergence and modern/core multispeaker concurrency hardening are complete. MP3/WAV compatibility names use the modern finite engine. OGG/generic whole-file aliases and the duplicate finite engine are removed. Dead singular/grouped fake standard/RAW implementation bodies are removed from the legacy peripheral while the composite preserves the supported public API. RAW remains separate. Live MP3/HLS/TS/ICY remains optional legacy/future work.

The inherited standalone `hqspeaker:hq_speaker` Minecraft block is removed. The only block product is the normal `computercraft:speaker` upgraded through the mixin/composite. The internal custom-audio sound event is `hqspeaker:hq_audio_source`.

License is MPL-2.0. The old LGPL metadata mismatch was inherited from upstream and has been corrected.

## Read before source changes

Read in this order:

1. `docs/HANDOFF-2026-09-20-POST-CONVERGENCE.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/ROADMAP.md`
8. `docs/LUA-API.md`
9. exact current source and latest CI

Dated older milestone/handoff files are historical evidence. Their “current” statements describe the time they were written and do not override the files above.

## Product rules

This is a programmable ComputerCraft speaker extension. Lua owns application meaning and policy. Do not add Java music/effect/notification roles or a Java playlist manager.

Preserve standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`.

One physical speaker remains one mono positional source.

Modern finite playback:

- MP3 + supported common WAV only;
- server-authoritative canonical state/time;
- bounded encoded range transport;
- progressive client decode;
- normal Minecraft SoundManager positional rendering;
- fixed 32-block core relevance/delivery radius;
- shared playback authority for multispeaker;
- independent physical endpoints;
- start-time endpoint snapshot;
- shared play/pause/resume/seek/loop/stop;
- endpoint-local volume/mute, with explicit All/At controls.

RAW:

- signed 16-bit PCM at 48 kHz;
- maximum 131072 samples per call;
- bounded queue/backpressure;
- `hqspeaker_audio_empty` only after a producer observed rejection;
- singular/All/At;
- All preflights the complete endpoint snapshot and uses one future start tick without an expected-member barrier.

Optional live streaming:

- `speakStream` / MP3 stream, `speakHLS`, `speakTS` and ICY metadata;
- not a core release requirement;
- grouped live helpers still use legacy sync-group metadata/client code;
- do not delete that sync machinery until grouped live streaming is retired or redesigned.

## Current architecture boundaries

The normal CC:T `SpeakerPeripheral` remains the standard-behavior implementation. `HQSpeakerCompositePeripheral` adds HQ ownership and extensions around it.

Modern finite multispeaker shares one `FinitePlaybackAuthority` and playback asset while each physical speaker keeps its own source UUID, position, listener membership, transport, renderer, recovery, volume and mute.

Protocol v9 is the current wire protocol. v8 introduced `playbackId` / `stateRevision`. v9 removed retired legacy finite payloads; the modern finite semantics remain.

Client shared playback projection must not re-anchor the canonical local clock for every same-revision endpoint packet.

M1H movement uses Sable Companion first, then VS2, then static block center. Do not add continuous position packets without a concrete need.

Important unresolved runtime risk: server relevance still requires `player.level() == level` after moving-source position projection. A Sable sublevel speaker projected into a parent world may therefore be geometrically correct but still fail listener admission. Do not mark this resolved without focused runtime/source work.

## Work style

Do not overcomplicate for hypothetical edge cases. When there are multiple meaningful approaches, present the practical tradeoffs and let the owner choose. Handle small implementation choices yourself.

Green CI is not runtime proof. Keep source/test/package evidence separate from Minecraft/SoundManager/OpenAL evidence.

Before deleting “legacy” code, verify exact current references. The recent cleanup intentionally kept live-stream sync code because it is still reachable.

Do not reintroduce a standalone speaker block, a second finite engine, OGG/generic JavaSound aliases, or shared decode fan-out unless a fresh product/performance case justifies it.
