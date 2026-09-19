# CC:HQ Speakers — post-convergence handoff

Updated: 2026-09-20

This is the primary handoff for the next chat.

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

## Exact checkpoint

Latest source checkpoint before this documentation closeout:

`00b07db41c003363cef60ef8ec4134fa387c421c` — `cleanup: rename internal audio source resource`

CI `35474944518`:

- NeoForge 21.1.247 PASS
- NeoForge 21.1.248 PASS
- deterministic tests PASS
- packaged-mod verification PASS
- artifact upload PASS

Current protocol: **v9**, 9 payloads.

If branch head is newer when reading this, inspect those commits first.

## Owner preferences

Keep explanations practical and concrete.

Do not overcomplicate for hypothetical edge cases.

For meaningful tradeoffs, present the practical options and let the owner choose. Handle minor implementation details yourself.

Runtime testing was temporarily deferred during this source-work stretch; runtime testing is not rejected. Batch it later when useful.

CI is not runtime proof.

## Product definition

This mod upgrades normal `computercraft:speaker`.

There is no separate HQ speaker block.

The inherited `hqspeaker:hq_speaker` block/item/block entity/registry was removed at `ce12a8bca2d68e7a6ebfaf106c4206508c26bb98`, CI `35474632162`.

The old block-like sound resource name was renamed to `hqspeaker:hq_audio_source` at `00b07db41c003363cef60ef8ec4134fa387c421c`, CI `35474944518`.

`hq_audio_source` is only an internal SoundManager anchor for custom AudioStream playback.

## Fork/license

Lineage:

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

The original repository carries MPL-2.0, while upstream mod metadata incorrectly said LGPL-3.0. This fork now uses MPL-2.0 consistently.

## Completed foundation

### M1G finite core

Final source `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542` / CI `35297026277`.

Focused audible/core Minecraft PASS recorded 2026-09-19 on NeoForge 21.1.247.

Core: server-authoritative finite time/state, bounded range transport, progressive JLayer MP3, common WAV, codec-aware seek/rejoin, fixed 32-block core range, SoundManager positional renderer, bounded client work.

### Post-M1G hardening

`3d30ce4564de749f32171666df65de739b08ad77` / CI `35406595856`.

Resolved KI-062 DNS/server-lock coupling, KI-063 replacement-before-admission, KI-054 shutdown/root-lock retry, KI-064 import no-progress/rename fallback.

### M1H-1 listener membership

`84e7bab99009e5871934a960908945ceb00a10a9` / CI `35410830197`.

Late entry, leave cleanup, re-entry at current time, membership/relevance gating. Runtime deferred.

### M1H-2 recovery

`aa72f0d2fc9f8cde53cd956389beca1743d06165` / CI `35411480844`.

Renderer/resource loss recovery, READY retry, sustained-starvation recovery, same-revision rebuild. Runtime deferred.

### M1H-3 movement

`5cd6d6ddcad4b5b4887b903f471de0f2f812795c` / CI `35451236630`.

Sable Companion 1.6.0, Sable/Aeronautics projection, VS2 fallback, no continuous position packets. Runtime deferred.

## M1J work completed

Owner-selected model: **one shared playback timeline, independent physical speakers**.

No global expected-count barrier.

Each physical speaker remains its own positional output.

### Shared authority/endpoints

`FinitePlaybackAuthority` owns playbackId, canonical clock/state, looping, stateRevision, decodeRevision and shared failure/time.

Modern group playback has one shared authority/asset lifetime plus independent physical endpoints.

First major checkpoint: `b557773b9c6f6b8029aec132a1706f0d8da914bd` / CI `35466635285`.

### Client shared timeline

Protocol v8 added `playbackId` and `stateRevision`.

Client uses one `FinitePlaybackProjection` per shared playback. Same-revision endpoint packets do not independently re-anchor the shared clock.

### Controls

Shared: play/pause/resume/seek/loop/stop.

Per physical speaker: volume/mute.

All gain/mute applies to every surviving endpoint in the shared playback snapshot, not a fresh scan of current CC:T attachments.

Endpoint removal/replacement does not kill remaining group members.

### Performance decision

Do not build shared decode/network fan-out without profiling. Server range work is already bounded; likely scaling concern is repeated client decode/PCM/render work.

## Finite convergence completed

The owner chose modern behavior rather than preserving odd legacy file edge cases.

### MP3/WAV compatibility names migrated

`536cfe4f7c4576233d5b2e1ab2d5d3483ffc146c` / CI `35469552490`.

Modern-engine compatibility names:

- `speakMp3` / `speakWav`
- All/At variants.

Bridge:

```text
Lua encoded bytes
-> immutable temporary MediaAsset
-> modern analyzer
-> modern prepared start
-> short main-thread commit
-> release temporary import reference
```

### OGG/generic finite retired

Removed:

- `speakOgg`
- `speakAudio`
- `speakFile`
- `speakPacked`
- related All/At.

Duplicate finite internals were removed incrementally: whole-file client decoder, legacy server timeline, control/status payloads, old finite sync, `FiniteAudioTrack`.

Finite teardown checkpoint: `fcb6670dd818412c15509129105aa7f54be9d5ba` / CI `35470940030`.

Protocol is now v9.

### Dependencies

`mp3spi` and `tritonus-share` removed at `c7f3784e3c680cef3a7b01aaa1071dd01a234b1a` / CI `35472719752`.

JLayer remains for modern MP3 and optional live MP3.

## RAW work completed

RAW remains separate:

- signed 16-bit;
- 48 kHz;
- max 131072 samples/call;
- queue limit 16;
- bounded sample lifetime;
- boolean backpressure;
- `hqspeaker_audio_empty` after observed rejection;
- singular/All/At.

Expected-member barrier removed: `b6866ce99810f1b449d0066c3d25cf6bc7d3baaf` / CI `35471254482`.

Admission unified: `c728a9076073e7a19a7a016f8eaf8e82c6ce68ac` / CI `35471357327`.

`speakPCMAll` prepares once, preflights every target, rejects before partial replacement if any target is full, and uses one future start tick without expected count.

## Standard CC:T grouped/indexed correction

Composite now intercepts grouped/indexed standard note/sound/audio and delegates to real CC:T speakers.

This fixed inherited fake-sine/ignored-sound/HQ-PCM behavior on the supported surface.

Old wrong bodies still physically exist in `HQSpeakerPeripheral`. They appear dead now that the standalone block is gone, but verify exact references before deletion.

## Exact current architecture

Standard:

`computercraft:speaker -> CC:T SpeakerPeripheral -> HQSpeakerCompositePeripheral wrapper`

Modern finite:

```text
MediaAsset
 -> shared FinitePlaybackAuthority
 -> endpoint A
 -> endpoint B
 -> endpoint C
```

Each endpoint independently owns source UUID, position, listeners, transport, decoder/render session, recovery, volume and mute.

RAW: composite-owned bounded admission around the RAW queue/packet transport, no global expected-member gate.

Optional live: MP3 URL/ICY/HLS/TS remains legacy-ish.

Grouped live helpers still use `SyncDispatch` and client `SyncGroupState` expected-member machinery. **Do not delete all sync-group code yet.**

## Important unresolved risks

### Sable parent-world listener relevance

Current server code still requires `player.level() == level` after moving-position projection.

A speaker can be projected out of a Sable sublevel while the player remains in the parent world; geometry may be correct but Level identity may reject listener admission.

Needs runtime/source work.

### HLS live progression

Inherited HLS keeps a monotonically increasing segment index against refreshed zero-based playlist windows. Long-running refresh may stop finding new segments.

Optional feature, not core blocker.

### Live grouped expected-member barrier

Finite: removed. RAW: removed. Optional grouped live streams: still present.

### Client decode duplication

Each audible modern finite endpoint decodes independently. Do not optimize without profiling.

## What should happen next

### A. Dead standard All/At cleanup

Recheck whether old `HQSpeakerPeripheral` bodies for:

- `playNoteAll` / `playSoundAll` / `playAudioAll`
- `playNoteAt` / `playSoundAt` / `playAudioAt`

have any direct exposure after standalone-block removal.

If truly unreachable, remove them and only helpers which become unreferenced.

Do **not** delete `SyncDispatch` / packet sync-group fields / client `SyncGroupState` wholesale because optional grouped live streams still use them.

### B. RAW/public API release cleanup

Audit status names, queue helpers, limits, backpressure wording, obsolete aliases, ownership cleanup and events.

Keep behavior simple and truthful.

### C. Repository/release hygiene

Exact dead-code sweep, dependencies/package, docs/API freeze, CI optimization if useful, default-branch/release hygiene, refresh runtime scripts.

### D. Deferred integrated runtime pass

When owner is ready:

- M1H listener entry/leave/re-entry;
- reload/renderer/starvation recovery;
- Sable/Aeronautics + VS2;
- Sable Level-identity risk;
- 2/4/8+ multispeaker;
- endpoint detach/replace;
- pause/seek/loop;
- per-speaker mute/volume;
- malformed/extreme media;
- memory/network/worker bounds;
- Sound Physics Remastered;
- both NeoForge versions.

## Do not do next unless selected

Do not re-add OGG/generic JavaSound finite support, add FLAC just because it is common, build shared decoder fan-out without profiling, rebuild a standalone HQ block, make live HLS/TS a core blocker, or pull Spotify/YouTube into core playback without separate feasibility work.

## Read next

1. this file;
2. `CURRENT-STATE.md`;
3. `KNOWN-ISSUES.md`;
4. `TESTING.md`;
5. `VERIFIED-FACTS.md`;
6. `ARCHITECTURE.md`;
7. `ROADMAP.md`;
8. `LUA-API.md`;
9. exact current source/CI.

Use `HANDOFF-PROMPT-2026-09-20.md` for the next chat.
