# Future cleanup inventory

This is a parking lot for obsolete/legacy code and release cleanup. It is not permission to expand the active milestone.

Current checkpoint: **M1E/M1F complete at source/test/CI/package level; M1G progressive decode/render is integrated in source and still in correctness/evidence work.**

Current active correctness/evidence issues belong in `KNOWN-ISSUES.md`, not this parking lot.

## Modern prepared path cleanup state

The modern prepared path already removed:

- whole-file finite CHUNK/END transfer;
- client `.part/.media` accumulation;
- tick-thread whole-file server reads;
- direct prototype `audioPlayStaged()`;
- active modern use of `FileFiniteAudioStream`;
- broad historical OGG/AIFF/AU prepared-format promises.

Do not restore these for temporary audibility.

## Active issues which are not optional cleanup

The 2026-09-14 full audit recorded:

- KI-053 — ordinary same-anchor STATE can reset a slid encoded window without restarting the existing decoder epoch;
- KI-054 — `MediaAssetStore.close()` does not retain failed completed-file shutdown deletions for retry;
- KI-055 — real-MP3 progressive integration/renderer-adapter evidence is incomplete and several Lua scripts are historical/legacy rather than modern M1G gates.

These are current engineering/evidence items, not M4 polish. The audit documented them only; it did not change source.

## Old complete-file decoder classes/dependencies

`FileFiniteAudioStream`, `FiniteAudioTrack`, and old complete-payload finite paths may remain because inherited compatibility APIs still exist.

Known old bridge defects include historical mp3spi duration/seek behavior. The modern prepared engine no longer uses that bridge.

JLayer is required by the modern progressive MP3 path. mp3spi/Tritonus-related dependencies should only be removed after legacy callers are migrated/removed and package/runtime proof confirms they are no longer needed.

## Old client cache artifacts

Older development builds may have left an `hqspeaker-cache` directory or `.part/.media` files on disk. Active M1F/M1G client source does not use them.

If one-time cleanup is later added, restrict deletion narrowly to mod-owned old cache paths.

## Legacy finite engine

Inherited `HQAudioStream`, `FiniteAudioTrack`, `HQSpeakerAudioPacket`, old byte-taking APIs (`speakMp3`, `speakWav`, `speakOgg`, etc.), and duplicate old finite state remain outside the modern prepared path.

Target M1L:

- preserve only compatibility frontends worth keeping;
- route them into the new asset/transport/decoder engine where sensible;
- remove obsolete whole-packet/whole-PCM implementation;
- do not force historical OGG/AIFF/AU support into the modern core merely to preserve old helper names.

## Protocol/state review after M1G/M1H

Current modern finite protocol is version 6 and includes BEGIN, CONTROL, STATE, STATUS, RANGE_REQUEST, and RANGE_DATA.

Protocol v6 carries the modern MP3/common-WAV decode descriptor. Forward window sliding is already implemented; the old “window needs consume/discard support” note is obsolete.

Review later rather than deleting blindly:

- whether READY remains useful once final listener lifecycle is settled;
- whether CONTROL can be simplified when STATE/renderer lifecycle is final;
- whether M1H needs a specific subscription/cancel message;
- whether protocol fields can be simplified after legacy migration.

Server authority must never regress into waiting for client readiness.

## M1F/M1G tuning values

Current values include 128 KiB max range responses, 512 KiB client encoded window, per-player outstanding caps, two server IO workers, and queue size 64.

M1O should benchmark packet compression/CPU, queueing, memory, many-player demand, cancellation storms, and seek spam before tuning is frozen.

## Historical format surface

Frozen M1D analyzed formats broader than the modern product target. Current modern prepared/local playback is already narrowed to MP3 + supported common WAV.

M1I may add native FLAC only if fully proven.

## Multispeaker bypasses

Inherited `*All` / `*At` helpers and expected-member shared groups remain legacy.

Target M1J/M1K: shared server clocks first, optional active-session sharing second, while retaining one positional renderer per physical speaker.

## Diagnostic instrumentation

After the progressive renderer is runtime-proven, remove/downgrade noisy development diagnostics while retaining concise operational failures.

## Live/HLS/TS inherited code

Known later issues include double gain, HLS sequence/window progression, non-incremental TS behavior, unsupported codecs, and old shared-session lifecycle.

Target M3. Do not mix them into finite M1G unless a truly shared primitive requires it.

## Sound/OpenAL cleanup

Target M1N:

- final category/gain behavior;
- F3+T/resource-reload recovery;
- stale-channel cleanup;
- attenuation/VS2 movement;
- one mono positional source per physical speaker.

## Product/registry cleanup

The repo still registers a separate `hqspeaker:hq_speaker` even though product direction upgrades normal CC speakers.

Target M4: remove it with world/registry migration consideration or explicitly justify/document it.

## Documentation/test cleanup

Historical scripts/handoffs are evidence, not current contracts. Keep them clearly historical rather than rewriting them to pretend newer architecture existed earlier.

Current docs must point readers to `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, and `VERIFIED-FACTS.md` for present behavior.

The bundled ROM module `src/main/resources/data/computercraft/lua/rom/modules/main/hqspeaker.lua` still contains a stale comment saying `audioPlayStaged()` is scheduled for removal when M1F replaces the old whole-file transport. M1F has already removed that route; runtime behavior is correct and only the source comment is stale. When source/comment edits are allowed, update that comment without changing behavior.

## Packaging/license cleanup

Before public release:

- resolve top-level MPL-2.0 versus NeoForge metadata LGPL-3.0 from actual provenance;
- update mod description to final supported feature/format set;
- verify final protocol/mixins/dependencies/ROM module;
- remove unused legacy decoder/provider dependencies only after migration proof.
