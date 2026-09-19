# Future cleanup inventory

Updated: 2026-09-19

This is a parking lot for obsolete/legacy code, later milestones, and release cleanup. M1G is complete; this file is not permission to reopen it without a concrete regression.

Current active correctness/evidence issues belong in `KNOWN-ISSUES.md`.

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

The M1G closeout resolved KI-051/053/055/056/057/058/060/061.

The post-M1G Option A hardening pass closed KI-062, KI-063, KI-054, and KI-064. Do not carry their old pre-fix descriptions forward as active cleanup work.

M1H source work and M1J modern finite multispeaker source work are complete at source/test/CI/package level, with focused runtime checks deferred. Current non-runtime work is engine/API convergence and release-oriented cleanup.

## Old complete-file decoder classes/dependencies

`FileFiniteAudioStream` was rechecked against the current branch reference graph and removed as unreferenced dead code during post-M1J convergence.

`FiniteAudioTrack` remains live: `HQAudioStream` uses it for inherited complete-payload finite playback, so it and its focused test stay until that compatibility path is migrated or retired. The remaining inherited complete-payload finite path is centered on `HQAudioStream`/`HQSpeakerClientHandler` and the old byte-taking APIs.

Known old bridge defects include historical mp3spi duration/seek behavior. The modern prepared engine uses JLayer progressively instead.

JLayer is required by the modern progressive MP3 path. mp3spi/Tritonus-related dependencies should only be removed after legacy callers are migrated/removed and package/runtime proof confirms they are no longer needed. Those SPI libraries register Java Sound providers globally in the JVM, which is another reason to remove them once no legacy caller needs them.

## Old client cache artifacts

Older development builds may have left an `hqspeaker-cache` directory or `.part/.media` files on disk. Active M1F/M1G client source does not use them.

If one-time cleanup is later added, restrict deletion narrowly to mod-owned old cache paths.

## Legacy API convergence matrix

Rechecked after M1J:

| Surface | Current decision |
| --- | --- |
| Standard `playNote`, `playSound`, `playAudio`, `stop` | Keep native CC:T semantics. Grouped/indexed note/sound/audio calls are intercepted by the composite and delegated to the real CC:T speakers. |
| HQ `speakPCM` | Keep as a separate producer-fed signed-16 RAW source. It is not a finite MediaAsset song. |
| Modern `hq.playFile`, `prepareFile`, `playPrepared`, `playPreparedAll` | Keep as the primary finite-file API. MP3 + supported common WAV only today. |
| Legacy byte-taking `speakMp3` / `speakWav` (+ All/At) | Good compatibility candidates for the modern engine, but not a trivial alias. A safe bridge must copy/validate bytes on the ComputerCraft thread, import/analyze into MediaAsset storage off the Minecraft tick thread, then use CC:T main-thread task execution only for the short playback commit. Temporary import ownership must be released on every success/failure path. |
| Legacy `speakOgg` | Keep legacy for now unless OGG is deliberately modernized. Do not silently claim modern OGG support. |
| Legacy generic `speakAudio`, `speakFile`, `speakPacked` | Ambiguous compatibility aliases. Do not route them blindly until the retained format contract is chosen. |
| Direct `speakStream` / HLS / TS + ICY | Optional legacy/future external-stream work, not core finite-engine convergence. |
| `audioStatus` / pause/resume/seek/loop/stop | Composite already routes to the active modern finite owner when present and otherwise preserves legacy behavior. |
| `speakSupportedFiles` | Legacy compatibility surface. Modern code should query `hq.preparedFormats(speaker)` instead. |

The bridge prerequisite is architectural, not a request to add a third engine: reuse `MediaAssetStore.importAsset(..., ReadableByteChannel)` and `ModernFiniteMediaAnalyzer`, then commit through the existing prepared finite server. CC:T dynamic peripheral methods run on the computer thread; world/server-state commit must use the main-thread task API rather than moving import/decode work onto the server tick.

## Legacy finite engine / advertised format surface

Inherited `HQAudioStream`, `FiniteAudioTrack`, `HQSpeakerAudioPacket`, old byte-taking APIs (`speakMp3`, `speakWav`, `speakOgg`, etc.), and duplicate old finite state remain outside the modern prepared path.

Legacy Lua-visible capability lists are not a reliable statement of modern prepared support. For example, `speakSupportedFiles()` advertises `mp2`, `mp4`, `m4a`, and `aac`, while the modern prepared analyzer accepts only MP3 + supported common WAV.

Convergence target:

- preserve only compatibility frontends worth keeping;
- route them into the new asset/transport/decoder engine where sensible;
- remove obsolete whole-packet/whole-PCM implementation;
- do not force historical OGG/AIFF/AU support into the modern core merely to preserve old helper names;
- make capability-reporting methods truthful about which engine/surface they describe.

## Legacy multispeaker note/sound helpers

Inherited `*All` / `*At` helpers and expected-member shared groups remain legacy.

Source recheck confirmed that `playNoteAll(...)` synthesizes a sine and ignores the requested instrument, while `playSoundAll(...)` routes into that sine path and ignores the requested Minecraft sound name. These helpers therefore do not preserve normal CC:T note/sound semantics even though the singular standard methods do.

Current status:

- modern prepared multispeaker now uses one shared authority with independent positional endpoints;
- grouped/indexed standard note/sound/audio calls now route through actual CC:T speaker semantics;
- inherited expected-member finite/live group code remains only for legacy surfaces not yet migrated.

## Protocol/state cleanup after M1G

Modern finite protocol is version 8 and includes BEGIN, CONTROL, STATE, STATUS, RANGE_REQUEST, and RANGE_DATA.

STATE carries explicit server-authoritative `decodeRevision`. PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP are no longer projected through CONTROL; STATE is the sole nonterminal transition authority. CONTROL remains for explicit STOP.

Review later rather than deleting blindly:

- whether READY remains useful once M1H listener lifecycle is settled;
- whether M1H needs a specific subscription/cancel message;
- whether the STOP-only CONTROL packet should be folded into another lifecycle packet in a future incompatible protocol cleanup.

Server authority must never regress into waiting for client readiness.

## M1F/M1G tuning values

Current values include 128 KiB max range responses, 512 KiB client encoded window, per-player outstanding caps, two server IO workers, and queue size 64.

M1O should benchmark packet compression/CPU, queueing, memory, many-player demand, cancellation storms, and seek spam before tuning is frozen.

## Historical format surface

Frozen M1D analyzed formats are broader than the modern product target. Current modern prepared/local playback is MP3 + supported common WAV.

M1I may add native FLAC only if fully proven.

## Diagnostic instrumentation

After the progressive renderer is runtime-proven, remove/downgrade noisy development diagnostics while retaining concise operational failures.

Before release/M1O, keep diagnostics useful for renderer/session failures while removing or downgrading development-only noise.

## Live/HLS/TS inherited code — M3

Known later issues include double gain, HLS sequence/window progression, non-incremental TS behavior, unsupported codecs, and old shared-session lifecycle.

A specific live-HLS progression defect is confirmed: `StreamingAudioSource.streamHLS()` retains a monotonically increasing `currentSegmentIndex`, while each refreshed playlist exposes a fresh zero-based segment list. After the first live window is consumed, refreshed windows can therefore contain no list index at or above `currentSegmentIndex`, leaving the stream alive but producing no new audio.

Fix later using media-sequence/absolute segment identity rather than list index.

`streamTS()` also demuxes an entire input into a `List<AudioFrame>` before playback, which is unsuitable as a long-lived live-stream architecture.

The earlier audit claim that HTTP streaming paths failed to close streams was retracted: current paths do close them. Do not carry that false issue forward.

Do not mix inherited live-stream repair into finite M1G unless a truly shared primitive requires it.

## Sound/OpenAL / moving-source cleanup

Target M1N and M1H together own final lifecycle details such as resource reload, stale-channel cleanup, remaining attenuation behavior, and moving-source/VS2 integration.

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry live x/y/z. `FiniteSpeakerSound.updatePosition(...)` exists but modern M1G does not call it after renderer creation.

A new wire position packet is not automatically necessary. The inherited client already recomputes VS2 ship-transformed positions from block coordinates each tick. M1H may mirror that client-side pattern or add authoritative position updates if later lifecycle/network requirements justify it.

Do not silently choose between those two approaches in cleanup work.

## Provider cache / lifecycle

`HQSpeakerPeripheralProvider` uses a Level-keyed `WeakHashMap`, but cached composite values themselves reference their Level. The source explicitly documents that the weak key is only a fallback and deterministic lifecycle hooks must evict entries.

There is no `HQSpeakerPeripheral -> composite` back-reference. Do not revive the retracted “WeakHashMap defeated by a fabricated back-reference” claim.

If a residual chunk-unload/cache-lifetime issue is investigated later, verify actual Minecraft/CC:T block-entity lifecycle hooks and the provider's `forget`/`forgetLevel`/`clearAll` behavior rather than reasoning from WeakHashMap alone.

## Product/registry cleanup

The repo still registers a separate `hqspeaker:hq_speaker` even though product direction upgrades normal CC speakers.

The separate block surface is incomplete as a standalone in-game product: no `data/hqspeaker` recipe/loot-table content and no block model under `assets/hqspeaker/models/block/`, while the blockstate references such a model.

Target M4: remove it with world/registry migration consideration or explicitly justify and finish it. Do not spend M1G effort making this second block into a product unless the owner changes direction.

## Dead/parallel implementation cleanup

`FileFiniteAudioStream` and `HQSpeakerCluster` were rechecked against the current branch and removed as unreferenced dead code. `FiniteAudioTrack` was rechecked separately and remains live through `HQAudioStream`.

Remaining candidates include stale diagnostic helpers and parts of the separate `HQSpeakerBlockEntity` path. Verify each reference count immediately before deletion; do not delete merely from an old audit note.

The goal is to remove parallel implementations that can mislead future contributors after compatibility callers are gone, without churning the completed modern finite engine unnecessarily.

## Documentation/test cleanup

Historical scripts/handoffs are evidence, not current contracts. Keep them historical rather than rewriting them to pretend newer architecture existed earlier.

Current docs must point readers to `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `TESTING.md`, and `VERIFIED-FACTS.md` for present behavior.

The bundled ROM module comment about `audioPlayStaged()` being scheduled for removal was corrected during the M1G closeout; M1F had already removed that prototype route.

Historical M0 smoke-test log markers also predate the current protocol/payload count. Keep M0 results as historical evidence rather than current regression expectations.

## CI / repository hygiene

The build workflow currently runs the two-target NeoForge matrix for all pushes and pull requests with no docs-only path exclusion or concurrency cancellation.

A later CI cleanup can skip full builds for documentation-only changes and cancel superseded runs while retaining lightweight docs validation if desired.

Do not make CI run-count/failure-rate statistics into permanent correctness claims; they are time-sensitive.

The working implementation branch/default-branch policy should also be normalized before public release so repository visitors land on the actual product or are clearly directed to it.

## Packaging/license cleanup

Before public release:

- resolve top-level MPL-2.0 versus NeoForge metadata LGPL-3.0 from actual provenance;
- update mod description to the final supported feature/format set rather than inherited/live features which may be legacy or gated;
- verify final protocol/mixins/dependencies/ROM module;
- remove unused legacy decoder/provider dependencies only after migration proof;
- consider pinning the Gradle wrapper distribution checksum and removing unused build plugins/configuration.
