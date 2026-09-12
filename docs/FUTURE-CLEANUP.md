# Future cleanup inventory

This file is a parking lot for code, protocol, metadata, tests, and documentation that may become obsolete as the replacement finite engine lands.

It is **not** a current implementation checklist. Do not spend M1E/M1F scope cleaning items here unless a listed item directly blocks the active milestone.

## Cleanup rule

Prefer deleting obsolete architecture only after its replacement owns all required callers and runtime behavior.

Do not preserve a proven bug merely because it is old, but also do not expand an active milestone just to make the tree look cleaner.

## Temporary prepared-finite bridge

### `FileFiniteAudioStream`

Current role: complete-file client decoder for the transitional prepared finite path.

Known reasons it is not a final foundation:

- requires a complete local file;
- JavaSound/mp3spi MP3 duration is not authoritative;
- current MP3 seek path mixes incompatible decoded/encoded byte semantics;
- seek can report the requested target even after incomplete/EOF positioning;
- current client restart path can seek the same newly-created stream twice;
- runtime diagnostic showed first PCM read ending immediately after seek.

Target cleanup: once M1G progressive MP3/common-WAV decoding fully replaces it, remove it from the modern prepared path and delete it if no legacy caller remains.

Do **not** repair/polish it during M1E/M1F solely to preserve temporary audibility.

### `HQFiniteMediaClient` whole-file cache/transfer state

Current transitional behavior includes:

- `hqspeaker-cache` client directory;
- `.part` accumulation;
- completed `.media` file;
- complete transfer before decoder construction;
- temporary local renderer projection tied to the file bridge.

Target cleanup:

- M1F cleanly replaces modern prepared-file transfer with bounded in-memory encoded ranges;
- M1G replaces the decoder/render portion;
- remove obsolete disk-transfer/cache code when no longer referenced.

Consider whether stale `hqspeaker-cache` artifacts from development builds need a one-time best-effort cleanup. Do not delete arbitrary user files or directories without a narrowly-scoped path check.

## Transitional finite protocol

### Old whole-file packets

Review/remove after M1F replacement:

- `HQFiniteMediaBeginPacket` fields that only exist for the old file bridge;
- `HQFiniteMediaChunkPacket`;
- `HQFiniteMediaEndPacket`.

M1F is expected to introduce demand-driven range request/data transport and a stream/anchor descriptor. Do not keep old chunk push packets merely for compatibility if no supported caller needs them.

### `HQFiniteMediaStatusPacket.READY`

READY currently means the complete-file bridge has constructed its decoder and wants fresh authoritative STATE.

After M1F/M1G, review whether READY still represents a useful client condition. Server authority must never regress into waiting for it.

### `HQFiniteMediaControlPacket` and `HQFiniteMediaStatePacket`

These are not automatically obsolete. Review rather than delete:

- STATE is the current authoritative semantic snapshot and may remain useful;
- CONTROL may be partially redundant once STATE/descriptor handling is redesigned.

Do not merge immutable stream description and moving authoritative state without a reason.

## Legacy finite engine

### `HQAudioStream`

Inherited legacy decoder/streaming implementation. Known concerns include old finite decode behavior, unbounded executor/task patterns, whole-decoded-track memory behavior, and unrelated live-stream responsibilities.

Target: M1L/M3 migration/removal rather than incremental polishing during M1F.

### `FiniteAudioTrack`

Legacy whole-PCM finite track representation.

Target: remove when all finite callers use the progressive bounded engine.

### `HQSpeakerAudioPacket`

Contains inherited whole-packet/legacy finite transport with one-shot size limits.

Target: migrate useful compatibility entrypoints to the server asset engine, then remove obsolete finite packet paths.

### Legacy byte APIs in `HQSpeakerPeripheral`

Examples include inherited `speakMp3(bytes)`, `speakOgg(bytes)`, and `speakWav(bytes)` surfaces and related old finite state.

Target M1L:

- keep only compatibility frontends worth preserving;
- route those into the new asset engine where sensible;
- remove OGG-specific finite promises rather than rebuilding Vorbis into the final core;
- large local files remain prepared/server-asset territory.

## Legacy multispeaker bypasses

Inherited `*All` / `*At` helpers can bypass the normal composite ownership/state boundary and older shared-group logic can depend on expected-member counts.

Target:

- M1J: functional shared server clocks and one positional renderer per physical block;
- M1K: optional active-session transfer/decode sharing after correctness;
- migrate or remove bypass helpers that no longer fit the final ownership model.

Do not patch the obsolete expected-member architecture during M1F.

## Historical format surface

Frozen M1D analyzes MP3, OGG Vorbis, WAV, AIFF, and AU. That remains historical evidence, not the final product promise.

Once M1G owns active prepared/local finite playback:

- final core advertisement becomes MP3 + implemented common WAV;
- remove active prepared/local OGG/AIFF/AU requirements;
- keep frozen milestone docs/commits reproducible instead of carrying obsolete format support forever;
- M1I may add native `.flac` only if its full gated contract passes.

Review any extension/name-based helper, UI text, README claim, Lua helper, or metadata which still advertises obsolete formats.

## Decoder dependencies

Current JarJar dependencies include mp3spi, JLayer, and tritonus-share.

M1G currently expects to use the shipped JLayer family for progressive MP3 unless another decoder is proven better.

After M1G is stable, review whether:

- mp3spi is still needed;
- tritonus-share is still needed;
- any JavaSound service-provider packaging exists only for the deleted complete-file bridge.

Do not remove these dependencies before replacement decode/package tests prove they are unnecessary.

## Diagnostic instrumentation

The active M1E branch contains several Java commits added only to diagnose runtime finite playback/wire/PCM behavior.

After the replacement path is proven:

- remove or downgrade noisy first-buffer/wire diagnostics that no longer provide operational value;
- retain concise failure logging which is useful to users/server owners;
- do not leave high-volume per-buffer debug logging enabled in release paths.

The diagnostics are currently useful evidence and should not be removed during preparation.

## Client-local clock/projection

The current `HQFiniteMediaClient` retains a local `FinitePlaybackClock` only as a renderer projection.

M1G/M1H should review whether the final renderer still needs this exact class/model or whether current server state + local buffer/render timestamps provide a clearer projection.

Canonical authority must remain server-side either way.

## Custom HQ block/product duplication

The repository still registers `hqspeaker:hq_speaker` even though the product direction upgrades the normal `computercraft:speaker`.

Target M4 decision:

- remove it if it has no distinct supported purpose; or
- explicitly justify/document it if retained.

Do not casually remove registry content mid-milestone without considering existing worlds/data migration.

## Live/HLS/TS inherited code

Known later issues include:

- stream gain applied twice;
- HLS media-sequence/live-window progression problems;
- non-incremental direct TS behavior;
- unsupported TS audio potentially treated as PCM;
- live server state not matching final renderer/network truth;
- retained shared-session lifecycle problems.

Target M3. Do not mix these fixes into finite M1F/M1G work unless shared infrastructure requires a narrowly-scoped change.

## Sound/OpenAL cleanup

Target M1N:

- correct sound category;
- one logical gain stage plus Minecraft master/category scaling;
- F3+T/resource reload recovery;
- stale channel cleanup;
- correct attenuation and VS2 movement;
- preserve one mono positional source per physical speaker.

## Test/script cleanup

Historical scripts are evidence for their milestone, not permanent active acceptance contracts.

Review later:

- `m1d_media_analysis_test.lua` renderer-`observed` assumptions;
- older P0/finite scripts tied to legacy renderer authority;
- old test fixtures/helpers for formats removed from the final core;
- duplicate acceptance scripts superseded by M1Q.

Do not delete historical tests until their evidence value is preserved in docs/commits.

## Documentation cleanup

Older handoffs and milestone docs intentionally preserve history, but active docs must not repeat stale branch-head claims.

Later cleanup can:

- archive redundant handoff text once the replacement engine is stable;
- remove obsolete P0 wording from release-facing docs;
- ensure historical docs are visibly labeled historical;
- keep exact milestone commit/run evidence discoverable.

## Packaging/metadata cleanup

Before public release:

- resolve top-level MPL-2.0 vs NeoForge metadata LGPL-3.0 license mismatch from actual provenance; do not guess/relicense silently;
- update `neoforge.mods.toml` description so it no longer advertises obsolete format/live behavior;
- verify packaged mixins, dependencies, ComputerCraft ROM module, and final protocol metadata;
- remove unused decoder/provider dependencies only after package/runtime proof.

## When to promote an item out of this file

Move an item into an active milestone/known issue only when one of these becomes true:

- it directly blocks the current milestone contract;
- a replacement is ready and leaving the old path creates ambiguity/risk;
- runtime evidence identifies it as an active user-facing defect in supported behavior;
- release packaging cannot proceed safely without resolving it.

Until then, this file is intentionally a reminder, not permission to expand scope.
