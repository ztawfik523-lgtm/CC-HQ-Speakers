# Architecture

Updated: 2026-09-20

## Product boundary

The mod upgrades the normal CC:T `computercraft:speaker`. There is no standalone HQ speaker block.

`ComputerCraftSpeakerBlockEntityMixin` replaces the exposed peripheral with `HQSpeakerCompositePeripheral` while retaining CC:T's real `SpeakerPeripheral` for native behavior.

Internal custom audio uses SoundManager resource `hqspeaker:hq_audio_source`.

## Output ownership

One physical speaker has one HQ continuous owner at a time:

- NONE
- RAW
- STAGED_FINITE
- STREAM

Native notes remain separate. Native `playSound` / `playAudio` do not overlap an active HQ continuous source.

Replacement paths should validate/admit the new source before ending the current valid one.

## Standard CC:T path

Standard singular/All/At note, sound and DFPWM calls are handled by the composite and dispatched to actual CC:T speaker peripherals.

The obsolete fake grouped/indexed implementations have been removed from `HQSpeakerPeripheral`. The composite explicitly owns and exposes the standard All/At names.

## Modern finite

`HQMediaStaging` / `MediaAssetStore` own immutable encoded assets. `ModernFiniteMediaAnalyzer` accepts MP3 and supported common WAV.

`FinitePlaybackAuthority` owns shared playback facts: playback ID, canonical time/state, looping, state/decode revisions, shared terminal failure and shared playback identity.

Each endpoint independently owns physical source UUID, position, listener membership, range transport, renderer/recovery state, volume and mute.

A multispeaker group is a start-time endpoint snapshot. There is no expected-global-member barrier. Endpoint removal/replacement detaches only that endpoint.

Shared controls: pause/resume, seek, loop, stop. Endpoint controls: volume, mute. All-volume/all-mute targets the surviving playback endpoint snapshot.

Protocol v8 added `playbackId` / `stateRevision`. Current protocol v9 removed retired legacy finite payloads while retaining the shared modern finite semantics.

Client maintains one `FinitePlaybackProjection` per shared playback. Same-revision endpoint packets do not repeatedly re-anchor the shared local clock.

Each endpoint still has its own decoder/PCM/render path. Shared decode fan-out is intentionally not implemented without profiling evidence.

## Listener/range

Modern finite uses a fixed 32-block core relevance radius.

Listener membership is dynamic: outside at start gets no session, entering gets BEGIN + current STATE, leaving gets targeted STOP, returning rejoins current time, and terminal/replacement clears membership.

READY/range traffic is admitted only for relevant members.

## Movement

`MovingSourcePosition` resolves Sable Companion first, VS2 second, static block center otherwise.

No continuous x/y/z packet stream exists.

Known unresolved risk: server relevance also requires `player.level() == level`. A Sable sublevel may project into a parent world while still carrying a different Level object.

## Recovery

Renderer/resource close/loss and sustained starvation are local recovery cases. The client requests authoritative current state and rebuilds from current time.

Shared/global failure should be reserved for genuinely shared failures.

## RAW

RAW is producer-fed signed-16 PCM at 48 kHz.

Composite admission provides max 131072 samples/call, queue limit 16, bounded sample lifetime, non-destructive rejection, `hqspeaker_audio_empty` after observed rejection, singular/All/At, full group preflight for All, and one future group start tick without expected-member synchronization.

The composite owns the public RAW singular/All/At admission path; `HQSpeakerPeripheral` remains the lower-level RAW queue/packet substrate. Obsolete legacy RAW All/At duplicate bodies are removed.

RAW is not a finite MediaAsset and has no real seek/duration/loop model.

## Optional live

`HQSpeakerPeripheral` / `HQAudioStream` retain optional live MP3/HLS/TS behavior and ICY metadata.

Grouped live helpers still use `SyncDispatch`, packet `syncGroupId` / `syncGroupSize`, and client `SyncGroupState` expected-count logic.

This live architecture is optional and must not dictate modern finite/RAW design.

## Storage/workers

Media assets use per-asset and total quotas.

Range IO: 2 workers, queue 64, max response 128 KiB, per-player 4 outstanding requests / 512 KiB, client encoded window 512 KiB.

## Dependencies

Embedded:

- JLayer 1.0.1.4
- Sable Companion 1.6.0

No mp3spi or Tritonus.

## License/provenance

Source is MPL-2.0.

Lineage: `tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`.
