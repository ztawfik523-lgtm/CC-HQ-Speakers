# Future cleanup inventory

This is a parking lot for obsolete/legacy code and release cleanup. It is not permission to expand the active milestone.

Current checkpoint: **M1E/M1F reevaluation hold; M1G not started.**

The reevaluation's active correctness issues are tracked in `M1E-M1F-REEVALUATION-2026-09-13.md` and `KNOWN-ISSUES.md`; they are not merely optional future cleanup.

## Modern prepared path cleanup state after M1F

M1F already removed these from the modern prepared path:

- whole-file finite CHUNK/END transfer;
- client `.part/.media` accumulation;
- tick-thread whole-file server reads;
- direct prototype `audioPlayStaged()`;
- active modern use of `FileFiniteAudioStream`.

Do not restore them for temporary audibility.

## Active lifetime hardening is not parked cleanup

The reevaluation found a shared ownership rule which current callers do not all obey:

`MediaAssetStore.release()` may fail final file deletion and deliberately keep the reference alive. A caller must therefore not forget ownership until release actually succeeds, or it must preserve an explicit retry owner.

Current active hardening findings include playback-reference release ordering, rare in-flight range final-release retry, and detached prepared-owner cleanup which logs-and-forgets a failed release after detaching the computer.

These belong to the current M1E/M1F hardening decision, not M4 cleanup.

## Old complete-file decoder classes/dependencies

`FileFiniteAudioStream` may remain as source even though M1F's modern prepared client no longer uses it.

Known old defects include wrong mp3spi duration/seek behavior and redundant seek paths.

Target:

- M1G proves the progressive MP3/common-WAV replacement;
- then remove dead complete-file decoder code/provider dependencies if no remaining supported caller needs them.

Current JarJar dependencies include mp3spi/JLayer/tritonus-related pieces. Do not delete dependencies until M1G package/runtime proof shows exactly what remains required.

## Old client cache artifacts

Older development builds may have left an `hqspeaker-cache` directory or `.part/.media` files on disk.

Active M1F source does not use them.

If a one-time cleanup is later added, restrict deletion narrowly to the mod-owned old cache path; never delete arbitrary user files.

## Legacy finite engine

Inherited `HQAudioStream`, `FiniteAudioTrack`, `HQSpeakerAudioPacket`, old byte-taking APIs (`speakMp3`, `speakWav`, `speakOgg`, etc.), and duplicate old finite state remain outside the modern prepared path.

Target M1L:

- preserve only compatibility frontends worth keeping;
- route them into the new asset/transport/decoder engine where sensible;
- remove obsolete whole-packet/whole-PCM implementation;
- do not rebuild OGG Vorbis merely to preserve an old helper name.

## Protocol/state review after M1G/M1H

Current modern finite protocol v5 includes BEGIN, CONTROL, STATE, STATUS, RANGE_REQUEST, and RANGE_DATA.

The reevaluation did not find a reason to redesign protocol v5. The current buffer-comsume gap can be solved inside the client encoded-window API.

Review later rather than deleting blindly:

- whether READY remains useful once the progressive decoder/renderer is attached;
- whether CONTROL can be simplified when STATE/renderer lifecycle is final;
- whether immutable BEGIN fields should move/expand for M1G layout facts;
- whether listener lifecycle needs a specific cancel/subscription message in M1H.

Server authority must never regress into waiting for client readiness.

## M1F tuning values

Current 128 KiB range responses, 512 KiB encoded client window, per-player outstanding caps, two IO workers, and queue size 64 are implementation safety values.

M1O should benchmark packet size/compression, queueing, memory, many-player demand, cancellation storms, and seek spam before release tuning is frozen.

## Historical format surface

Frozen M1D analyzes formats broader than the final product target.

Once M1G owns active prepared playback:

- advertise MP3 + implemented common WAV only;
- remove active OGG/AIFF/AU prepared/local promises;
- preserve frozen milestone docs/commits as history;
- M1I may add native FLAC only if fully proven.

## Multispeaker bypasses

Inherited `*All` / `*At` helpers and expected-member shared groups remain legacy.

Target:

- M1J functional shared server clocks with one positional renderer per physical speaker;
- M1K optional in-memory active-session sharing optimization;
- migrate/remove bypass helpers that no longer fit final ownership.

## Diagnostic instrumentation

Older M1E runtime diagnostic logging was useful for authority/decoder investigation.

After the new progressive renderer is proven, remove/downgrade noisy per-buffer/wire diagnostics while retaining concise operational failure logs.

## Live/HLS/TS inherited code

Known later issues include double gain, HLS sequence/window progression, non-incremental TS behavior, unsupported codecs, and old shared-session lifecycle.

Target M3. Do not mix them into finite M1G unless a narrowly shared primitive truly requires it.

## Sound/OpenAL cleanup

Target M1N:

- correct sound category;
- one logical gain stage plus Minecraft master/category scaling;
- F3+T/resource reload recovery;
- stale channel cleanup;
- correct attenuation and VS2 movement;
- one mono positional source per physical speaker.

## Product/registry cleanup

The repo still registers a separate `hqspeaker:hq_speaker` even though the product direction upgrades normal CC speakers.

Target M4: remove it with proper world/registry migration consideration, or explicitly justify/document it.

## Documentation/test cleanup

Historical scripts/handoffs are evidence, not current contracts.

Later cleanup may archive redundant handoffs and old renderer-authority scripts after current evidence is secure. Do not rewrite historical evidence to pretend newer architecture existed earlier.

## Packaging/license cleanup

Before public release:

- resolve top-level MPL-2.0 versus NeoForge metadata LGPL-3.0 from actual provenance;
- update mod description to final supported feature/format set;
- verify final protocol/mixins/dependencies/ROM module;
- remove unused decoder/provider dependencies only after replacement proof.
