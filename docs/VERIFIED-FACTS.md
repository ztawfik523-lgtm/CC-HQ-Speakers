# Verified facts

Updated: 2026-09-20

Checked against current `codex/m1j-multispeaker` source at/through `395a41c1c91a4d1efa41cee9db89ba02fe767785` unless explicitly historical.

## Product/repository

- FACT-001: the only block product is `computercraft:speaker` upgraded by the mixin/composite.
- FACT-002: the inherited standalone `hqspeaker:hq_speaker` block/item/block entity/registry is removed.
- FACT-003: internal custom audio uses `hqspeaker:hq_audio_source` and `sounds/hq_audio_source.ogg`.
- FACT-004: repository and NeoForge metadata license are MPL-2.0.
- FACT-005: fork lineage is `tiktop101 -> jvrcruzGAMES -> ztawfik523-lgtm` for CC-HQ-Speakers.

## Protocol/package

- FACT-010: current network protocol is v9 with 9 registered payloads.
- FACT-011: JLayer 1.0.1.4 and Sable Companion 1.6.0 are embedded.
- FACT-012: mp3spi and tritonus-share are removed.
- FACT-013: `395a41c1c91a4d1efa41cee9db89ba02fe767785` passed CI `35476526511` on both supported NeoForge targets, including packaged-mod verification and artifact upload.

## Standard CC:T

- FACT-020: standard singular `playNote/playSound/playAudio/stop` route through the real CC:T `SpeakerPeripheral`.
- FACT-021: standard grouped/indexed note/sound/audio calls are intercepted by the composite and dispatch to real CC:T speakers.
- FACT-022: obsolete legacy standard grouped/indexed bodies are removed; the composite explicitly exposes those method names and dispatches to real CC:T speakers.

## Modern finite

- FACT-030: modern finite supports MP3 and the documented supported common-WAV subset.
- FACT-031: modern finite uses one server-authoritative `FinitePlaybackAuthority` per playback and bounded client range/decode/render work.
- FACT-032: modern multispeaker uses one shared authority with independent physical endpoints and no expected-global-member barrier.
- FACT-033: group membership is a start-time endpoint snapshot; endpoint removal/replacement does not fail remaining endpoints.
- FACT-034: play/pause/resume/seek/loop/stop are shared playback operations; volume/mute are endpoint-local; All gain/mute operates on the surviving endpoint snapshot.
- FACT-035: protocol v8 introduced `playbackId/stateRevision`; current v9 retains those semantics and removes retired finite payloads.
- FACT-036: client shared playback projection does not re-anchor on every same-revision endpoint packet.
- FACT-037: modern finite uses a fixed 32-block core server relevance radius; finite volume changes gain, not that radius.
- FACT-038: M1G focused audible/core Minecraft acceptance passed on NeoForge 21.1.247 on 2026-09-19.

## Listener/recovery/movement

- FACT-040: M1H listener membership is source/test/CI/package implemented.
- FACT-041: M1H renderer/resource/starvation recovery is source/test/CI/package implemented.
- FACT-042: movement resolution uses Sable Companion first, VS2 second, static block center otherwise; no continuous position packet stream.
- FACT-043: focused M1H runtime acceptance remains deferred.
- FACT-044: current server relevance still requires `player.level() == level` after position projection; this is an unresolved Sable sublevel risk.

## Convergence

- FACT-050: `speakMp3/speakWav` singular/All/At are modern finite compatibility frontends.
- FACT-051: historical `speakOgg` and generic whole-file `speakAudio/speakFile/speakPacked` aliases are removed.
- FACT-052: duplicate legacy finite server/client/payload/sync implementation is removed.

## RAW

- FACT-060: HQ RAW is signed 16-bit PCM at 48 kHz.
- FACT-061: composite RAW max is 131072 samples/call; queue limit is 16.
- FACT-062: RAW backpressure uses boolean rejection plus `hqspeaker_audio_empty` only for a producer that observed rejection.
- FACT-063: `speakPCMAll` preflights the target snapshot and uses a common future start tick without expected-member synchronization.
- FACT-064: obsolete legacy `speakPCMAll/speakPCMAt` duplicates and their now-unreferenced wrappers are removed; public RAW All/At admission is composite-owned.

## Optional live

- FACT-070: optional live code supports MP3 stream/HLS/TS and ICY metadata.
- FACT-071: grouped live helpers still use `SyncDispatch` / `syncGroupId` / expected-count client `SyncGroupState`; that sync code is still reachable.
- FACT-072: inherited HLS refreshed-playlist index progression remains a known concern.

## Evidence boundaries

- FACT-080: green CI does not prove audibility, SoundManager/OpenAL lifecycle, Sable/VS2 movement, or real multispeaker synchronization.
- FACT-081: focused M1H and M1J Minecraft acceptance remains deferred, not rejected.
- FACT-082: shared decode fan-out is not selected; it remains a profiling gate.
