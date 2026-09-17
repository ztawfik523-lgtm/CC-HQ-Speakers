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

Current `KNOWN-ISSUES.md` includes the M1G decoder/reanchor cluster, renderer/range/start work, staging/shutdown storage issues, and the cross-cutting monitor/ownership/import hardening findings from the 2026-09-16 repository review. Do not downgrade those to release polish merely because some were found during a broad audit.

## Old complete-file decoder classes/dependencies

`FileFiniteAudioStream`, `FiniteAudioTrack`, and old complete-payload finite paths may remain because inherited compatibility APIs still exist.

`FileFiniteAudioStream` is no longer referenced by the modern prepared path and has no focused test coverage. Treat it as migration/removal material, not as an alternate M1G implementation.

Known old bridge defects include historical mp3spi duration/seek behavior. The modern prepared engine no longer uses that bridge.

JLayer is required by the modern progressive MP3 path. mp3spi/Tritonus-related dependencies should only be removed after legacy callers are migrated/removed and package/runtime proof confirms they are no longer needed. Those SPI libraries also register Java Sound providers globally in the JVM, which is another reason to remove them once no legacy caller needs them.

## Old client cache artifacts

Older development builds may have left an `hqspeaker-cache` directory or `.part/.media` files on disk. Active M1F/M1G client source does not use them.

If one-time cleanup is later added, restrict deletion narrowly to mod-owned old cache paths.

## Legacy finite engine / advertised format surface

Inherited `HQAudioStream`, `FiniteAudioTrack`, `HQSpeakerAudioPacket`, old byte-taking APIs (`speakMp3`, `speakWav`, `speakOgg`, etc.), and duplicate old finite state remain outside the modern prepared path.

The 2026-09-16 review confirmed that legacy Lua-visible capability lists are not a reliable statement of the modern prepared contract. For example, `speakSupportedFiles()` advertises `mp2`, `mp4`, `m4a`, and `aac`, while the modern prepared analyzer accepts only MP3 + supported common WAV. Keep modern and inherited capability reporting explicitly separated until the legacy surface is migrated or removed.

Target M1L:

- preserve only compatibility frontends worth keeping;
- route them into the new asset/transport/decoder engine where sensible;
- remove obsolete whole-packet/whole-PCM implementation;
- do not force historical OGG/AIFF/AU support into the modern core merely to preserve old helper names;
- make capability-reporting methods truthful about which engine/surface they describe.

## Legacy multispeaker note/sound helpers

Inherited `*All` / `*At` helpers and expected-member shared groups remain legacy.

The 2026-09-16 source review confirmed that `playNoteAll(...)` synthesizes a sine and ignores the requested instrument, while `playSoundAll(...)` routes into that sine path and ignores the requested Minecraft sound name. These helpers therefore do not preserve normal CC:T note/sound semantics even though the singular standard methods do.

Target M1J/M1K:

- shared server clocks first, optional active-session sharing second, while retaining one positional renderer per physical speaker;
- either route note/sound helpers through actual standard CC:T semantics or remove/reject the misleading helpers rather than emitting the wrong sound.

## Protocol/state review after M1G/M1H

Current modern finite protocol is version 6 and includes BEGIN, CONTROL, STATE, STATUS, RANGE_REQUEST, and RANGE_DATA.

Protocol v6 carries the modern MP3/common-WAV decode descriptor. Forward window sliding is already implemented; the old “window needs consume/discard support” note is obsolete.

Current M1G scope has already selected an explicit server-authoritative decoder/re-anchor revision. Before implementing that protocol revision, compare whether PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets should survive merely as latency hints or disappear in favor of STATE as the sole correctness authority. Do not preserve duplicate paths for compatibility with an unreleased internal protocol.

Review later rather than deleting blindly:

- whether READY remains useful once final listener lifecycle is settled;
- whether M1H needs a specific subscription/cancel message;
- whether protocol fields can be simplified after legacy migration.

Server authority must never regress into waiting for client readiness.

## M1F/M1G tuning values

Current values include 128 KiB max range responses, 512 KiB client encoded window, per-player outstanding caps, two server IO workers, and queue size 64.

M1O should benchmark packet compression/CPU, queueing, memory, many-player demand, cancellation storms, and seek spam before tuning is frozen.

## Historical format surface

Frozen M1D analyzed formats broader than the modern product target. Current modern prepared/local playback is already narrowed to MP3 + supported common WAV.

M1I may add native FLAC only if fully proven.

## Diagnostic instrumentation

After the progressive renderer is runtime-proven, remove/downgrade noisy development diagnostics while retaining concise operational failures.

The 2026-09-16 review also observed that the M1G client is much quieter than the inherited client on refusal/failure paths. Add enough once-per-condition diagnostics during M1G hardening to make renderer-start/session-cap failures debuggable, then tune verbosity before release.

## Live/HLS/TS inherited code

Known later issues include double gain, HLS sequence/window progression, non-incremental TS behavior, unsupported codecs, and old shared-session lifecycle.

The 2026-09-16 source review confirmed a specific live-HLS progression defect: `StreamingAudioSource.streamHLS()` retains a monotonically increasing `currentSegmentIndex`, but each refreshed playlist exposes a fresh zero-based segment list. After the first live window is consumed, refreshed windows can therefore contain no list index at or above `currentSegmentIndex`, leaving the stream alive but producing no new audio. Fix later using media-sequence/absolute segment identity rather than list index.

`streamTS()` also demuxes an entire input into a `List<AudioFrame>` before playback, which is unsuitable as a long-lived live-stream architecture. This remains M3 work.

Target M3. Do not mix inherited live-stream repair into finite M1G unless a truly shared primitive requires it.

## Sound/OpenAL cleanup

Target M1N:

- final category/gain behavior;
- F3+T/resource-reload recovery;
- stale-channel cleanup;
- attenuation/VS2 movement;
- one mono positional source per physical speaker.

Modern moving-source position is also covered by KI-004/M1H: `FiniteSpeakerSound.updatePosition(...)` currently has no active M1G call site after renderer creation, and modern STATE does not carry world position updates.

## Product/registry cleanup

The repo still registers a separate `hqspeaker:hq_speaker` even though product direction upgrades normal CC speakers.

The 2026-09-16 tree review confirmed that this separate block surface is incomplete as a standalone in-game product: there is no `data/hqspeaker` recipe/loot-table content and no block model under `assets/hqspeaker/models/block/`, while the blockstate references such a model. `HQSpeakerBlockEntity` contains a legacy-style peripheral field/getter, but this separate block is not the normal product surface used by current architecture.

Target M4: remove it with world/registry migration consideration or explicitly justify and finish it. Do not spend M1G effort making this second block into a product unless the owner changes direction.

## Dead/parallel implementation cleanup

Candidates confirmed by the broad review include `FileFiniteAudioStream`, `HQSpeakerCluster`, stale diagnostic helpers, and parts of the separate `HQSpeakerBlockEntity` path. Verify each reference count again immediately before deletion; do not delete merely from an old audit note.

The goal is to remove parallel implementations that can mislead future contributors after their compatibility callers are gone, not to churn source during active M1G correctness work.

## Documentation/test cleanup

Historical scripts/handoffs are evidence, not current contracts. Keep them clearly historical rather than rewriting them to pretend newer architecture existed earlier.

Current docs must point readers to `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, and `M1G-SCOPE-DECISIONS-2026-09-14.md` for present behavior.

The bundled ROM module `src/main/resources/data/computercraft/lua/rom/modules/main/hqspeaker.lua` still contains a stale comment saying `audioPlayStaged()` is scheduled for removal when M1F replaces the old whole-file transport. M1F has already removed that route; runtime behavior is correct and only the source comment is stale. When source/comment edits are allowed, update that comment without changing behavior.

Historical M0 smoke-test log markers also predate the current protocol/payload count. Keep the M0 result as historical evidence rather than using its old marker as a current regression expectation.

## CI / repository hygiene

The build workflow currently runs the two-target NeoForge matrix for all pushes and pull requests with no path exclusion or concurrency cancellation. A later CI cleanup can skip full builds for documentation-only changes and cancel superseded runs, while retaining a lightweight docs check if desired.

Do not make CI history statistics into permanent correctness claims; run counts and failure percentages are time-sensitive.

The working implementation branch and default-branch policy should also be normalized before public release so repository visitors land on the actual product or are clearly directed to it.

## Packaging/license cleanup

Before public release:

- resolve top-level MPL-2.0 versus NeoForge metadata LGPL-3.0 from actual provenance;
- update mod description to final supported feature/format set rather than inherited/live features which may be legacy or gated;
- verify final protocol/mixins/dependencies/ROM module;
- remove unused legacy decoder/provider dependencies only after migration proof;
- consider pinning the Gradle wrapper distribution checksum and removing build plugins/configuration which are not used by the release process.
