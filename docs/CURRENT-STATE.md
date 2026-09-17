# Current state

Updated: 2026-09-17

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, package verification, and artifact upload.

Documentation/audit commits after that checkpoint do not change the implementation baseline.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance remains unrecorded.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Locked M1G media decisions

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer.
- B1: server-normalized common-WAV layout.
- C1: preserve source sample rate.
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support.
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.
- modern prepared formats remain MP3 + supported common WAV only.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Selected M1G policy after later rethinks

These are owner-selected target semantics, even where source work remains:

### Explicit decoder/re-anchor revision

M1G will stop inferring decoder restart intent from codec-anchor movement.

Target rules:

- new media playback => new generation;
- semantic seek => decoder/re-anchor revision changes;
- ordinary STATE, pause/resume, volume changes, and ordinary loop-state snapshots preserve a healthy decoder;
- STATE is self-sufficient for seek/reanchor correctness;
- a client with no usable local decoder may rebuild from authoritative STATE without requiring the server revision itself to change;
- stale decoder identity is invalidated before cancellation can wake/report.

Current implementation is still protocol **v6**. The explicit revision is the selected next protocol design, likely v7.

One implementation-shape choice remains: retain finite PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional latency hints, or remove that duplicate projection path and make STATE the sole transition authority. Correctness must not depend on CONTROL/STATE ordering either way.

### Fixed 32-block core range

M1G will **not** implement volume-dependent network relevance or dynamic audible range.

- current server relevance is 32 blocks and remains the selected M1G core radius unless explicitly changed;
- distance attenuation operates inside that range;
- HQ `volume` changes loudness/gain, not the core range;
- volume >1 must not silently enlarge modern finite channel attenuation distance;
- future Sound Physics Remastered compatibility owns intentional acoustic/range extension and matching transport relevance.

No M1H listener-membership subset is pulled forward merely for volume-dependent range.

### Global volume zero

Canonical server time continues when HQ volume is exactly zero.

Selected local behavior is targeted hibernation:

- retain session metadata;
- stop/cancel decoder and renderer work;
- stop requesting encoded ranges;
- on unmute, rebuild/rejoin from current authoritative time/anchor.

Client-local MASTER/BLOCKS mute is separate and must not influence server transport policy.

### Looping is ordinary replay

At local physical EOF, if authoritative state still says `looping=true`, start the same media again with a fresh local decoder/render iteration.

A normal restart gap is acceptable.

M1G does **not** include sample-gapless boundaries, MP3 encoder-delay/padding trimming, loop-head prefetch solely to hide the boundary, permanent-source loop architecture, or SPR-specific loop continuity.

Do not reopen L1/L2/L3 or L4a/L4b unless the owner explicitly changes the loop requirement.

## Integrated source

M1G source includes the MP3/common-WAV prepared gate, protocol-v6 decode descriptor, exact WAV/E1 MP3 anchors, starvation-aware `FiniteEncodedInputStream`, bounded `FinitePcmQueue`, progressive WAV conversion, progressive JLayer MP3 decoding, decoder-epoch cancellation, bounded prebuffer/catch-up, `FinitePcmAudioStream`, and positional `FiniteSpeakerSound` through `SoundSource.BLOCKS`.

Pause/resume/volume use the Minecraft channel-control path. Seek/replacement/stop discard old decoder/PCM/renderer state. Temporary PCM starvation becomes bounded silence rather than fake EOF.

`FiniteDecodeAnchorSelector.Anchor` contains exactly `(offset, seconds)`. There are no richer frame-index/skip fields being computed and discarded.

## Current correctness/evidence blockers

### Decoder/reanchor cluster — KI-053/KI-056/KI-057

- **KI-053:** an ordinary same-anchor STATE can reset an already-slid encoded window without restarting the decoder, potentially making the live cursor stale.
- **KI-056:** expected SEEK cancellation can race into `decoderFailed()` before replacement state exists because old worker identity is not invalidated first.
- **KI-057:** STATE currently doubles as both timeline snapshot and implicit decoder-reanchor instruction; changed time-derived anchors can restart healthy playback, while same-anchor semantic seek still depends on the preceding CONTROL packet.

These should be solved together with the selected explicit revision, not independently patched.

### Renderer/range/loop — KI-058/KI-060/KI-051

- **KI-058:** live modern-finite channel attenuation does not yet explicitly enforce the selected fixed 32-block core distance while volume changes gain.
- **KI-060:** renderer startup is latched before Minecraft proves the sound actually started; local silent-start/retry and selected global-volume-zero behavior need hardening.
- **KI-051:** ordinary local replay is selected but not implemented.

### Evidence/storage — KI-055/KI-061/KI-054/KI-064

- **KI-055:** real progressive MP3 fixture coverage and focused `FinitePcmAudioStream` coverage are missing.
- **KI-061:** per-speaker persistent staging mounts can leave unreachable files on disk.
- **KI-054:** shutdown can lose completed-file deletion retry state; an earlier `FiniteRangeReadService.close()` failure can also prevent `MediaAssetStore.close()` entirely, leaving the root lock and stopped-server registry entry alive in the JVM.
- **KI-064:** `MediaAssetStore.writeExact()` can spin indefinitely on repeated zero-byte reads and import has no fallback when `ATOMIC_MOVE` is unsupported.

### Cross-cutting ownership/threading — KI-062/KI-063

- **KI-062:** synchronized dynamic stream dispatch can hold the composite monitor across blocking DNS. Server tick `tickOwnership()` and synchronized composite `cleanup()` use the same monitor, so a DNS-parked computer thread can stall tick ownership work and lifecycle cleanup waiting on that composite.
- **KI-063:** RAW/prepared replacement can stop/transfer current ownership before all rejection/failure conditions for the new source are known, so a rejected replacement can destroy valid current playback.

`audioPrepareStaged(...)` is not itself synchronized on the composite monitor; do not broaden KI-062 to every media operation.

## Working implementation sequence

There are two defensible sequencing styles, so this is guidance rather than a permanent product lock.

Current practical order from the latest review:

1. eliminate the KI-062 server-stall hazard before relying on the legacy stream path;
2. fix KI-053/KI-056/KI-057 together with the explicit decoder/re-anchor revision;
3. implement fixed attenuation + volume-zero/renderer-start behavior;
4. implement ordinary local replay;
5. add deterministic cancellation/seek coverage, a real progressive MP3 fixture path, and focused `FinitePcmAudioStream` tests;
6. fix KI-061 staging cleanup;
7. group KI-063 and KI-054/KI-064 around the above according to patch cohesion while not losing them;
8. run both NeoForge targets and focused Minecraft modern-prepared runtime acceptance.

A broader safety-first batch which closes KI-062/063/054/064 before v7 is also reasonable; if chosen, record that sequencing decision explicitly instead of silently expanding M1G.

## Rechecked repository audit: corrections to preserve

The broad September 16 audit was useful but not authoritative by itself. Exact-source rechecking kept the concrete findings above and rejected/qualified several claims:

- `FiniteDecodeAnchorSelector.Anchor` is only `(offset, seconds)`; there are no richer frame/skip fields being discarded.
- modern finite STATE does **not** carry world x/y/z. BEGIN carries initial world/block position.
- `audioPrepareStaged(...)` is not synchronized on the composite monitor. The confirmed monitor/DNS path is synchronized dynamic stream dispatch.
- `HQSpeakerPeripheral` has no composite back-reference. The provider WeakHashMap is explicitly only a fallback; deterministic Level/server lifecycle eviction is the intended mechanism because cached values reference their Level.
- `HQFiniteMediaServer.tick()` was overstated in the first audit draft; it does not itself iterate/project players every tick.
- inherited HTTP stream paths close their streams; do not resurrect the retracted stream-close claim.
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

Inherited HLS progression, legacy format-advertising mismatches, legacy `playNoteAll`/`playSoundAll` semantics, and the incomplete separate `hqspeaker:hq_speaker` product surface are real/largely confirmed but remain later cleanup rather than M1G expansion.

## M1H / VS2 moving-source clarification

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry x/y/z. `FiniteSpeakerSound.updatePosition(...)` exists but the modern M1G client does not call it after renderer creation.

A new wire position-update packet is **not automatically required**. The inherited client already recalculates VS2 ship-transformed position from block coordinates each tick.

M1H may therefore:

1. mirror the legacy client-side transform path using BEGIN block coordinates; or
2. add explicit authoritative position updates if later lifecycle/network requirements justify them.

That tradeoff remains open for M1H and should not be silently selected during M1G.

## Explicitly deferred

Unless a concrete correctness bug proves otherwise, M1G does not include:

- dynamic volume-aware listener radius;
- general late-entry/out-of-range/re-enter lifecycle;
- Sound Physics Remastered integration;
- gapless MP3 metadata handling;
- continuous-source loop engineering;
- generalized long-underrun current-time rejoin;
- native FLAC;
- inherited HLS/TS repair or legacy API migration;
- finishing the separate `hqspeaker:hq_speaker` block as a second product;
- CI/repository/release cleanup.

Those remain later milestones/compatibility work.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F focused Minecraft transport acceptance: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G fixed-range volume/start correctness: open KI-058/KI-060; KI-059 policy selected
M1G staging lifecycle cleanup: open KI-061
Cross-cutting synchronized-DNS/main-thread stall: open KI-062
Cross-source replacement-before-admission: open KI-063
Storage import progress/rename hardening: open KI-064
Shutdown lock/retry hardening: open KI-054
M1G real-MP3 progressive integration coverage: incomplete
M1G focused FinitePcmAudioStream coverage: incomplete
M1G ordinary replay: selected, not implemented
M1G audible Minecraft PASS: unrecorded
```

## Read order

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `FUTURE-CLEANUP.md`
7. `ARCHITECTURE.md`
8. `ROADMAP.md`
9. `LUA-API.md`
10. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
