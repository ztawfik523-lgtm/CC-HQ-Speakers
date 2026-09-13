# Future cleanup inventory

This file is a parking lot for code, protocol, metadata, tests, and documentation which may become obsolete as the replacement finite engine lands.

It is **not** permission to expand the active milestone. Clean something here only when its replacement owns the required callers/behavior or the item directly blocks the current milestone.

Current checkpoint: documentation/preparation only. M1F implementation has not started.

## Prototype API moving into active M1F scope

### `audioPlayStaged()`

This command was introduced by this project's staged/local-file prototype. It is not part of the untouched inherited HQ Speakers API.

It directly plays a temporary staged file, which conflicts with the modern model:

```text
ComputerCraft file
    -> temporary staging/import
    -> reusable server MediaAsset
    -> playback
```

Project decision on 2026-09-13:

**remove `audioPlayStaged()` when M1F implementation begins.**

This is no longer merely optional future cleanup because M1F is explicitly replacing the modern staged/whole-file transport. New programs use `hq.playFile()` or prepare/play/release.

Do not remove it during the documentation-only checkpoint.

## Temporary prepared-finite bridge

### `FileFiniteAudioStream`

Current role: complete-file client decoder for the transitional prepared finite path.

Known reasons it is not a final foundation:

- requires a complete local file;
- JavaSound/mp3spi MP3 duration is not authoritative;
- current MP3 seek path mixes incompatible decoded/encoded progress semantics;
- seek can report the requested target after incomplete positioning;
- current client restart path can seek the same newly-created stream twice;
- runtime diagnostic showed first PCM read ending immediately.

Target cleanup: once M1G progressive MP3/common-WAV decoding fully replaces it, remove it from the modern prepared path and delete it if no remaining legacy caller needs it.

Do **not** repair/polish it during M1F solely to preserve temporary audibility.

### `HQFiniteMediaClient` whole-file cache/transfer state

Current transitional behavior includes:

- `hqspeaker-cache` client directory;
- `.part` accumulation;
- completed `.media` file;
- complete transfer before decoder construction;
- local renderer projection tied to the file bridge.

Target cleanup:

- M1F replaces modern prepared-file transfer with bounded in-memory encoded ranges;
- M1G replaces the decoder/render portion;
- remove obsolete disk-transfer/cache code when no longer referenced.

If a one-time stale-development-cache cleanup is later added, use narrowly-scoped path checks. Never delete arbitrary user files/directories.

## Transitional finite protocol

### Old whole-file packets

Review/remove as M1F replaces the modern prepared path:

- `HQFiniteMediaBeginPacket` fields which only exist for the old file bridge;
- `HQFiniteMediaChunkPacket`;
- `HQFiniteMediaEndPacket`.

M1F introduces demand-driven range request/data transport and whatever minimal stream/anchor description it needs. Do not keep old chunk-push packets merely for an obsolete modern prepared path.

### `HQFiniteMediaStatusPacket.READY`

READY currently means the complete-file bridge has constructed its decoder and wants fresh authoritative state.

After M1F/M1G, review whether READY still represents a useful client condition. Server authority must never regress into waiting for it.

### `HQFiniteMediaControlPacket` / `HQFiniteMediaStatePacket`

Do not delete automatically:

- STATE is the current authoritative semantic snapshot and may remain useful;
- CONTROL may become partly redundant as the new stream/descriptor handling settles.

Do not merge immutable stream description and moving authoritative state without a reason.

## M1F lifecycle cleanup requirement

When M1F introduces background range reads, shutdown must stop/drain/cancel those reads before the shared server media store closes and removes its asset files.

This is an active correctness requirement for M1F, not optional later cleanup.

## Legacy finite engine

Retained inherited pieces include:

- `HQAudioStream`;
- `FiniteAudioTrack`;
- finite portions of `HQSpeakerAudioPacket`;
- old byte-taking APIs such as `speakMp3`, `speakOgg`, `speakWav`.

Target M1L:

- keep only compatibility frontends worth preserving;
- route useful ones into the new asset engine where sensible;
- remove obsolete OGG-specific finite promises rather than rebuilding Vorbis into the new core;
- large local files remain `hq.playFile`/prepared-asset territory.

Do not polish the old whole-file/whole-PCM engine during M1F.

## Legacy multispeaker bypasses

Inherited `*All` / `*At` helpers can bypass normal ownership/state rules and older shared-group code may depend on expected-member counts.

Targets:

- M1J: functional shared server clocks and one positional renderer per physical block;
- M1K: optional active-session transfer/decode sharing after correctness.

Do not patch obsolete expected-member architecture during M1F.

## Historical format surface

Frozen M1D analyzes MP3, OGG Vorbis, WAV, AIFF, and AU. That remains historical evidence, not the final product promise.

Once M1G owns active prepared/local finite playback:

- final core advertisement becomes MP3 + implemented common WAV;
- remove active prepared/local OGG/AIFF/AU requirements;
- keep frozen milestone docs/commits reproducible;
- M1I may add native `.flac` only if its gated contract passes.

Review extension lists, helper text, README claims, Lua docs, and metadata which still advertise obsolete formats.

## Decoder dependencies

Current packaged decoder stack includes mp3spi, JLayer, and tritonus-share.

M1G currently expects the shipped JLayer family for progressive MP3 unless another path is proven better.

After M1G is stable, review whether mp3spi/tritonus remain necessary. Do not remove dependencies before replacement package/runtime proof.

## Diagnostic instrumentation

The M1E branch contains Java diagnostics added while investigating the finite runtime path.

After the replacement path is proven:

- remove/downgrade noisy first-buffer/wire diagnostics which no longer help;
- retain concise useful failure logging;
- do not leave high-volume debug logging in release paths.

## Client-local playback projection

The current client retains a local finite playback clock/projection. M1G/M1H should review whether the final renderer needs the same model or a clearer projection based on server state + local buffer/render timing.

Canonical authority remains server-side either way.

## Custom HQ block/product duplication

The repository still registers `hqspeaker:hq_speaker` even though the product direction upgrades the normal `computercraft:speaker`.

Target M4: remove it if it has no supported distinct purpose, or explicitly justify/document it. Consider existing worlds/registry data before removal.

## Live/HLS/TS inherited code

Known later issues include double gain, HLS progression, non-incremental TS behavior, unsupported TS decode paths, incomplete live state/lifecycle, and old shared-session behavior.

Target M3. Do not mix these into finite M1F/M1G except for narrowly shared infrastructure.

## Sound/OpenAL cleanup

Target M1N:

- appropriate sound category;
- one logical gain stage plus Minecraft scaling;
- F3+T/resource reload recovery;
- stale-channel cleanup;
- correct attenuation and VS2 movement;
- one mono positional source per physical speaker.

## Test/script cleanup

Historical scripts are evidence for their milestone, not permanent active contracts.

Later review may remove/archive old renderer-authority assumptions, obsolete format fixtures, or superseded scripts once their evidence value is preserved.

The final M1E manual script remains **unrun**, not passed.

## Documentation cleanup

Current continuation entry points are:

- `HANDOFF-2026-09-13-PRE-M1F.md`
- `LUA-API.md`
- `CURRENT-STATE.md`

Older handoffs/preparation docs should stay visibly historical/superseded.

When Lua-facing behavior changes, update `LUA-API.md` and the bundled `hqspeaker.lua` comments in the same milestone.

## Packaging/metadata cleanup

Before public release:

- resolve top-level MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch from actual provenance;
- update mod description so it no longer advertises obsolete format/live behavior;
- verify packaged mixins, dependencies, ROM module, and protocol metadata;
- remove unused decoder/provider dependencies only after proof.

## Rule for promoting cleanup into active work

Promote an item out of this file only when:

- it directly blocks the current milestone;
- a replacement is ready and leaving the old path creates ambiguity/risk;
- runtime evidence identifies it as an active defect in supported behavior;
- release packaging cannot proceed safely without it.

Until then, this file is a reminder, not a scope-expansion list.