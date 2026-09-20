# Current state

Updated: 2026-09-20

## Checkpoint

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

Current source checkpoint:

`8349d0883c2521506bbfdaac4546981c1e31af3e`

CI `35477934035` passed:

- NeoForge 21.1.247
- NeoForge 21.1.248
- deterministic tests
- packaged-mod verification
- artifact upload

Current protocol is **v9** with 9 registered payloads.

## Product state

The only block product is the normal `computercraft:speaker` upgraded through `ComputerCraftSpeakerBlockEntityMixin` and `HQSpeakerCompositePeripheral`.

The inherited standalone `hqspeaker:hq_speaker` block/item/block entity/registry was removed at `ce12a8bca2d68e7a6ebfaf106c4206508c26bb98`, CI `35474632162`.

The internal custom SoundManager resource was renamed to `hqspeaker:hq_audio_source` at `00b07db41c003363cef60ef8ec4134fa387c421c`.

License is MPL-2.0. Fork lineage:

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

## Completed finite stack

Modern finite supports MP3 and the documented common-WAV subset.

It uses reusable immutable server MediaAssets, server-derived metadata/duration, one `FinitePlaybackAuthority` per playback, bounded range transport, progressive client decode, codec-aware seek anchors, normal Minecraft SoundManager rendering, a fixed 32-block core radius, and current-time recovery/rejoin.

M1G focused audible/core Minecraft acceptance passed on NeoForge 21.1.247 on 2026-09-19.

Post-M1G hardening closed KI-062, KI-063, KI-054 and KI-064.

## M1H complete in source

M1H-1 listener membership checkpoint: `84e7bab99009e5871934a960908945ceb00a10a9` / CI `35410830197`.

M1H-2 renderer/reload/starvation recovery checkpoint: `aa72f0d2fc9f8cde53cd956389beca1743d06165` / CI `35411480844`.

M1H-3 movement checkpoint: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c` / CI `35451236630`.

Focused M1H Minecraft checks remain deferred.

Important runtime risk: `HQFiniteMediaServer.isRelevant(...)` still requires `player.level() == level` even after `MovingSourcePosition.resolve(...)` projects the speaker position. A Sable sublevel speaker projected into a parent world may therefore still fail listener admission. This is not proven broken, but CI does not prove it correct.

## M1J modern finite multispeaker complete in source

First major checkpoint: `b557773b9c6f6b8029aec132a1706f0d8da914bd` / CI `35466635285`.

Current behavior:

- one canonical shared playback authority;
- start-time endpoint snapshot;
- no expected-global-member barrier;
- shared asset lifetime;
- independent physical source/listener/transport/renderer/recovery endpoints;
- endpoint removal/replacement does not kill remaining endpoints;
- shared pause/resume/seek/loop/stop;
- endpoint-local volume and mute;
- explicit All/At controls;
- one client `FinitePlaybackProjection` per shared playback.

Group-wide volume/mute targets the surviving playback endpoint snapshot, not a fresh scan of current ComputerCraft attachments.

Focused real-Minecraft multispeaker acceptance remains deferred.

## Concurrency hardening started

Checkpoint: `8349d0883c2521506bbfdaac4546981c1e31af3e` / CI `35477934035`.

Resolved in this slice:

- shared finite pause/resume/seek/loop/terminal fanout no longer traverses other endpoint monitors while the initiating `HQFiniteMediaServer` monitor is held;
- `sharesPlaybackWith(...)` no longer nests one finite endpoint monitor inside another;
- both supported NeoForge targets pass build/tests/package verification after the change.

Still unresolved: composite-level multi-endpoint commands are not yet one atomic transaction. All-start/RAW-All and shared-stop ownership cleanup currently acquire target command/owner locks separately, so concurrent commands can still interleave across endpoints. This is the next core source-hardening item.

## Finite API convergence complete

Legacy-name MP3/WAV methods now use the modern finite engine:

- `speakMp3` / `speakWav`
- `speakMp3All` / `speakWavAll`
- `speakMp3At` / `speakWavAt`

Historical OGG/generic whole-file methods are retired:

- `speakOgg`
- `speakAudio`
- `speakFile`
- `speakPacked`
- related All/At variants.

The duplicate finite engine was removed: old finite server timeline/state, whole-file client decoder, `FiniteAudioTrack`, old finite expected-member sync, and legacy finite control/status payloads.

Finite teardown checkpoint: `fcb6670dd818412c15509129105aa7f54be9d5ba` / CI `35470940030`.

Current protocol is **v9**.

`mp3spi` and `tritonus-share` are removed. JLayer remains because modern finite MP3 and optional live MP3 streaming use it directly.

## RAW current state

HQ RAW is separate from finite playback:

- signed 16-bit PCM;
- 48 kHz;
- maximum 131072 samples per call;
- queue limit 16;
- bounded sample-derived feed lifetime;
- boolean backpressure;
- `hqspeaker_audio_empty` only after observed rejection;
- singular, All and At;
- All preflights every target before replacement;
- All uses a shared future start tick but no expected-member barrier.

Relevant commits:

- `b6866ce99810f1b449d0066c3d25cf6bc7d3baaf` / CI `35471254482`
- `c728a9076073e7a19a7a016f8eaf8e82c6ce68ac` / CI `35471357327`

## Standard CC:T behavior

`HQSpeakerCompositePeripheral` intercepts standard singular/All/At speaker calls and delegates to the real CC:T `SpeakerPeripheral`.

The obsolete fake sine/HQ-PCM standard grouped/indexed implementations were removed at `ef2a917de429c34409ae7866f59cb50f3c191aa1`. The composite explicitly preserves the six standard All/At method names and remains their sole supported implementation.

The obsolete legacy RAW `speakPCMAll/speakPCMAt` duplicates and now-unreferenced helper wrappers were removed at `395a41c1c91a4d1efa41cee9db89ba02fe767785`; composite RAW admission remains authoritative.

## Optional live path

Still present:

- `speakStream` / MP3 stream;
- `speakHLS`;
- `speakTS`;
- ICY metadata;
- grouped/indexed helpers.

This path is optional, not release-core.

Grouped live helpers still use legacy `SyncDispatch` / packet `syncGroupId` / client `SyncGroupState` expected-count machinery. Therefore that sync code is not dead yet.

Known HLS refresh/index progression concerns remain.

## What remains

Near-term non-runtime:

1. finish composite multi-endpoint command/ownership coordination so All-start, RAW-All and shared-stop cannot interleave across endpoint locks;
2. decide the fate of stale inherited compatibility controls `speakStopAll/At`, `speakVolumeAll`, and `setLoopingAll`;
3. keep live-stream sync machinery while grouped live helpers still depend on it;
4. finish RAW/public API truthfulness and obsolete-helper cleanup;
5. continue exact dead-code/import/dependency cleanup;
6. freeze truthful docs/API/capabilities;
7. CI/default-branch/release hygiene where worthwhile.

Deferred runtime/integration:

- M1H listener walk-in/out/re-entry;
- resource reload / renderer loss / sustained starvation;
- Sable/Aeronautics + VS2 movement;
- Sable sublevel parent-world relevance risk;
- modern 2/4/8+ multispeaker behavior;
- replacement/seek/loop stress;
- malformed/extreme media;
- bounded queues/memory/network/workers;
- Sound Physics Remastered;
- final both-target Minecraft acceptance.

Shared decode fan-out remains a profiling gate, not a promised feature.

Optional/future: modern OGG, FLAC, live-stream repair, provider playback, native ordinary Create contraption lifecycle, gapless playback.
