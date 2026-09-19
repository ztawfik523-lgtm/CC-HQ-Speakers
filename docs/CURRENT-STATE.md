# Current state

Updated: 2026-09-19

## Checkpoint

Active branch: `codex/m1j-multispeaker`.

M1E through M1J are complete at source/test/CI/package level. Focused M1H/M1J Minecraft runtime checks remain deferred to the backlog for now. Post-M1J finite API/engine convergence is complete: MP3/WAV compatibility names use the modern engine, OGG/generic whole-file aliases are retired, and the duplicate legacy finite server/client/packet implementation has been removed.

First M1J implementation checkpoint:

`b557773b9c6f6b8029aec132a1706f0d8da914bd`

CI `35466635285` passed NeoForge 21.1.247 and 21.1.248 with build, deterministic tests, packaged-mod verification, and artifact upload.

Implemented in this checkpoint:

- one thread-safe `FinitePlaybackAuthority` for canonical state/time;
- one shared playback/media reference across N physical endpoints;
- transaction-style multispeaker prepared start with no expected-member barrier;
- endpoint detach/replacement without killing remaining members;
- shared pause/resume/seek/loop/stop semantics;
- endpoint-local volume and mute, including all/indexed controls;
- `hqspeaker.playPreparedAll` / `playFileAll` and mute helpers;
- protocol v8 `playbackId` + `stateRevision` + existing `decodeRevision`;
- one client `FinitePlaybackProjection` per shared playback, with deterministic same-revision/no-reanchor coverage.

M1J source work is functionally complete at the current branch head after the initial checkpoint plus subsequent client-timeline, indexed-control, documentation, endpoint-detach hardening, and early engine/API convergence. Focused Minecraft multispeaker acceptance remains deferred/not yet recorded, so M1J is not claimed runtime-complete. The post-M1J performance gate currently defers shared decode/network fan-out: server range IO is already globally bounded per player, while the remaining duplicated client decode cost needs realistic runtime evidence before adding a multi-reader decode layer.

M1H-1 **source** checkpoint:

`84e7bab99009e5871934a960908945ceb00a10a9`

M1H-1 CI `35410830197` passed NeoForge 21.1.247 and 21.1.248 with deterministic tests, packaged-mod verification, and artifact upload.

- NeoForge 21.1.247: artifact `10574726822`, SHA-256 `62e8f090ddfe5466db3807dd1d78fd738c355887e6e938f811834d9630078cc9`;
- NeoForge 21.1.248: artifact `10573826903`, SHA-256 `2538111207be632a1253a762f6a45218b6d7ca4b24c2739359b4f56c8e648555`.

M1H-2 **source/test checkpoint**:

`aa72f0d2fc9f8cde53cd956389beca1743d06165`

M1H-2 CI `35411480844` passed NeoForge 21.1.247 and 21.1.248 with deterministic tests, packaged-mod verification, and artifact upload.

- NeoForge 21.1.247: artifact `10574298203`, SHA-256 `536399fbb03f1b8009f4abc0c1260a116e5376234f125d4ccd43a25c3263e370`;
- NeoForge 21.1.248: artifact `10573757634`, SHA-256 `c496b3871cb7dae32d323dbdc3d10dd44efb8f9c012d18043a5a075285293790`.

M1H-3 **source/package checkpoint**:

`5cd6d6ddcad4b5b4887b903f471de0f2f812795c`

M1H-3 CI `35451236630` passed NeoForge 21.1.247 and 21.1.248 with the existing deterministic suite, packaged-mod verification, and artifact upload.

- NeoForge 21.1.247: artifact `10586971196`, SHA-256 `ad336ca13f243aa19251f5e4649ab59066e60c91c40a4de6aeab49f5ca4d8a5c`;
- NeoForge 21.1.248: artifact `10587375399`, SHA-256 `31e553d2ed35380958bceb23ecf2479d46b03ad5bcdf8eed82440477b5b760f8`.

Final M1G **source** checkpoint:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Post-M1G hardening **source** checkpoint:

`3d30ce4564de749f32171666df65de739b08ad77`

Latest full hardening verification CI `35406595856` passed NeoForge 21.1.247 and 21.1.248, including build, deterministic tests, packaged-mod verification, and artifact upload. Source after `3d30ce4564de749f32171666df65de739b08ad77` is documentation-only through that run.

Hardening artifacts from CI `35406595856`:

- NeoForge 21.1.247: artifact `10572925494`, SHA-256 `ad0d02119ad3a4a29de00220e195ad117214316b8739f8faca223ac4900f409c`;
- NeoForge 21.1.248: artifact `10573000385`, SHA-256 `aff2eaaccb56b04c12d25bc6774f27c52f45fae90212b0b5e771f90828e6012e`.

CI `35297026277` passed NeoForge 21.1.247 and 21.1.248, including build, deterministic tests, packaged-mod verification, and artifact upload.

Artifacts:

- NeoForge 21.1.247: artifact `10527498657`, SHA-256 `3b873edd94a924cf3922a75cdd059c2b3963500787bd479ec8b386600ef055b1`;
- NeoForge 21.1.248: artifact `10528645404`, SHA-256 `3b9304a509d37bf2ef1c0797d4449fa2cb6c8cc705678e63a68ed8c2ef72f8b1`.

The failed intermediate CI run `35287579710` was a test-source-set problem only: a direct JUnit test referenced Minecraft's client-only `AudioStream` interface. Main source compiled on both targets. The test was replaced with a pure renderer-read adapter shared by `FinitePcmAudioStream`, and the final checkpoint is green.

Green CI by itself is still not Minecraft runtime proof. A separate focused **M1G audible/core Minecraft runtime PASS was recorded on 2026-09-19** using NeoForge 21.1.247 in integrated singleplayer. The run exercised modern WAV/MP3 playback, pause/resume, seek/reanchor, natural EOF, ordinary looping, positional attenuation/fixed-range behavior, stop, prepared-asset lifetime, float32 WAV, and global-volume-zero hibernation/unmute. NeoForge 21.1.248 remains CI/package verified but was not manually runtime-tested in that session.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> current protocol v9; modern finite STATE still carries playbackId + stateRevision/decodeRevision + codec-aware anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> pure renderer-read policy
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Completed M1G contract

Locked media/renderer choices remain:

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer;
- B1: server-normalized common-WAV layout;
- C1: preserve source sample rate;
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support;
- E1: conservative MP3 pre-roll from an earlier analyzed seek point;
- modern prepared formats: MP3 + supported common WAV only.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

### Decoder/re-anchor authority

Current protocol v9 retains the modern finite semantics introduced in v8: server-authoritative `decodeRevision`, shared `playbackId`, and `stateRevision`. v9 removes the obsolete legacy finite control/status payloads and strips legacy finite state from the old RAW/live audio packet.

- new media playback uses a new generation;
- semantic seek increments the revision;
- ordinary STATE, pause/resume, volume, and loop-state snapshots do not restart a healthy decoder;
- STATE alone is sufficient for seek/reanchor correctness;
- nonterminal PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL projection was removed;
- explicit STOP remains because stopping removes the server session;
- a client with no usable local decoder may rebuild from current authoritative STATE without changing the server revision;
- local worker identity is invalidated before cancellation can wake/report.

This closes KI-053, KI-056, and KI-057 at source/component-test level.

### Fixed 32-block core range

M1G intentionally keeps a fixed 32-block server delivery/listening radius.

The active Minecraft channel is explicitly assigned the same fixed attenuation distance. HQ volume changes gain/loudness but does not expand the core radius. Future Sound Physics Remastered work owns deliberate acoustic/range extension and matching transport relevance.

This closes KI-058; KI-059 remains the recorded product decision.

### Global volume zero and renderer lifecycle

Global HQ volume exactly zero keeps canonical server time running while local finite transport/rendering hibernates:

- decoder/renderer state is cancelled;
- client range demand stops;
- server drops new/completing finite range delivery while globally muted;
- non-zero volume rebuilds from current authoritative STATE/anchor.

Player-local MASTER/BLOCKS mute remains separate. `FiniteSpeakerSound.canStartSilent()` allows Minecraft to create the long-lived sound while locally inaudible.

Renderer start is no longer permanently latched before `SoundManager.play(...)` succeeds. The client observes actual activation/EOF and requests authoritative rejoin after failed or unexpectedly lost renderer activation.

This closes KI-060 at source/component level.

### Looping

M1G looping is ordinary replay, not gapless playback.

At physical local EOF, if authoritative state still says `looping=true`, the client starts a fresh local decoder/render iteration and catches up to the current canonical loop position. A normal restart gap is acceptable.

M1G intentionally does not implement LAME/Xing gapless padding trim, loop-head prefetch solely to hide boundaries, or permanent-source loop architecture.

This closes KI-051.

### Deterministic evidence and staging cleanup

M1G now includes:

- pure `FiniteDecodeCoordinator` tests for revision/stale-state/local-worker invalidation behavior;
- a real synthetic mono 44.1 kHz MP3 fixture decoded through packaged JLayer;
- MP3 coverage across initial starvation, repeated bounded range refill, and multiple `FiniteRangeWindow` slides;
- MP3 pre-target discard coverage;
- `FinitePcmReadAdapter` tests for DATA, starvation-as-silence, physical EOF, cancellation, and bounded reads;
- existing bounded PCM queue, WAV decode, range-window, state-machine, and transport tests;
- `HQMediaStaging` whole-owner cleanup of persistent staging leftovers with deterministic tests.

Direct JUnit loading of `FinitePcmAudioStream` is not used because the ordinary NeoForge test source set does not expose Minecraft's client-only `AudioStream` class. The renderer-read policy is extracted into the pure adapter actually used by `FinitePcmAudioStream`; the Minecraft adapter itself is compile/package verified on both targets.

This closes KI-055 and KI-061 at deterministic/source level.

## M1G closeout verdict

M1G is closed as the **core progressive finite engine engineering milestone**.

Resolved in the M1G closeout:

- KI-051 — ordinary replay;
- KI-053 — same-anchor STATE/window reset hazard;
- KI-055 — real MP3 + renderer-read deterministic coverage;
- KI-056 — cancellation/failure race;
- KI-057 — snapshot versus reanchor ambiguity;
- KI-058 — fixed attenuation contract;
- KI-060 — renderer start + global-zero hibernation;
- KI-061 — staging leftovers.

KI-052's deterministic/source portion is satisfied by the final component suite and re-audit. KI-046 is now also satisfied by the recorded focused live Minecraft/SoundManager/OpenAL run on NeoForge 21.1.247. This does not absorb later M1H recovery/resource-reload work. The separate post-M1G KI-062/063/054/064 hardening pass is now complete.

## Post-M1G hardening complete

The Option A hardening pass is complete at source/test/CI/package level.

Resolved after M1G:

- KI-062 — blocking stream URL DNS now runs outside both the ownership monitor and the command-order lock. Single-speaker stream commit rejoins the short command-order lock only after validation, so main-thread prepared playback cannot wait behind DNS. A mutation revision rejects a normal stream start if a newer playback/control command superseded it while DNS was blocked.
- KI-063 — RAW and prepared replacement validate/admit the replacement before destructively stopping the current valid source.
- KI-054 — range shutdown begins during `ServerStopping`; failed final cleanup remains retryable, old stopped-server ownership remains reachable, and a later server instance retries stale cleanup before reopening the media root.
- KI-064 — media import has a bounded no-progress policy and falls back to a safe same-root non-atomic rename when `ATOMIC_MOVE` is unsupported.

`MediaAssetStore.close()` now retains failed-deletion bookkeeping/quota state and keeps the root lock until cleanup actually succeeds.

M1H source work is complete. The active milestone is **M1J modern finite multispeaker**.

Selected direction: one thread-safe shared playback authority owns the canonical timeline/seek/loop/shared terminal state, while each physical speaker remains an independent positional endpoint with its own listeners, movement, transport, renderer, recovery, and eventual endpoint gain. The group is a start-time snapshot and has no expected-member barrier.

## Rechecked repository-audit corrections to preserve

- `FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`;
- modern finite STATE does not carry x/y/z; BEGIN carries initial world/block position;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- M1H-1 now makes `HQFiniteMediaServer.tick()` check listener membership every server tick; the speaker world position is calculated once for that scan;
- inherited HTTP stream paths do close their streams;
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## M1H-1 listener membership implemented

M1H-1 is implemented at source/test/CI/package level at `84e7bab99009e5871934a960908945ceb00a10a9`, CI `35410830197`.

Current behavior:

- players outside the fixed 32-block range are not admitted;
- entering range sends BEGIN + current STATE immediately;
- the normal client READY reply is allowed to produce one harmless follow-up STATE;
- staying in range does not repeat BEGIN or STOP;
- leaving range sends targeted STOP and removes membership;
- returning gets a fresh client session for the same active generation and the current server playback time;
- disconnected players are forgotten; dimension-changed players are sent cleanup when still connected;
- READY and range traffic are accepted only from currently admitted, relevant players;
- stop/replacement sends cleanup to all admitted players;
- natural end/error sends the terminal STATE to admitted players and clears membership.

`FiniteListenerMembershipTest` covers walk-in/stay/leave/return, retry behavior when a projection fails, disconnect/dimension-style pruning, and full clear on stop/replacement/terminal.

Focused real-Minecraft M1H-1 walk-in/walk-out/re-entry acceptance was explicitly deferred by the owner on 2026-09-19 and remains in the backlog. CI does not prove audible cleanup/rejoin behavior.

## M1H-2 recovery implemented

M1H-2 is implemented at source/test/CI/package level at `aa72f0d2fc9f8cde53cd956389beca1743d06165`, CI `35411480844`.

Current behavior:

- if Minecraft/SoundEngine closes the active finite audio stream during reload/loss, that expected close no longer becomes a fatal decoder error;
- an unexpectedly closed or lost renderer requests a fresh authoritative STATE and rebuilds from the current server time;
- recovery READY is retried once per second until a usable STATE arrives, so one failed recovery send cannot leave playback stuck;
- five seconds of continuous renderer starvation triggers the same current-time rejoin;
- normal STATE updates do not hide a continuing starvation problem;
- pause, decoder restart, and volume-zero hibernation clear the starvation timer correctly;
- recovery can rebuild from the same server `decodeRevision`; it does not fake a seek or protocol change;
- client world loss still clears sessions, while dimension/range cleanup is handled by M1H-1 membership.

Focused Minecraft resource-reload/renderer-loss/long-starvation recovery testing is deferred to the runtime backlog. CI does not prove SoundEngine behavior.

## M1H-3 moving-source support implemented

M1H-3 is implemented at source/CI/package level at `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`, CI `35451236630`.

Selected design: **local position resolution, no movement packets**.

Current behavior:

- Sable Companion 1.6.0 is embedded as a lightweight optional compatibility library;
- Sable/Aeronautics-style sublevel speaker positions are projected into current world space locally;
- existing VS2 ship transforms remain the fallback moving-world path;
- static speakers keep their normal block-center position;
- the client updates the existing positional sound from BEGIN block coordinates each client tick;
- the server uses the same resolved speaker position for the fixed 32-block listener/range check;
- protocol v7 is unchanged and no continuous x/y/z traffic was added;
- native ordinary Create contraption assembly/disassembly support is intentionally outside this slice rather than forcing a broader compatibility framework.

Focused Minecraft Sable/Aeronautics and VS2 movement testing is not recorded and remains in the runtime backlog.

## Evidence boundaries

```text
M1E source/test/CI: PASS
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F source/test/CI/package/component: PASS
M1F focused Minecraft transport acceptance: unrecorded
M1G source/test/CI/package/component: PASS at fa679ffcb81a66fd99ab6be8e6d6b77895fbc542
M1G protocol: v7
M1G decoder snapshot/reanchor correctness: source/component PASS
M1G fixed-range volume/start correctness: source/component PASS
M1G real-MP3 progressive coverage: PASS
M1G renderer-read policy coverage: PASS
M1G staging lifecycle cleanup: PASS
M1G ordinary replay: implemented
M1G focused audible/core Minecraft PASS: recorded 2026-09-19 on NeoForge 21.1.247 integrated singleplayer (KI-046 resolved)
Post-M1G KI-062 DNS/monitor hardening: PASS
Post-M1G KI-063 replacement admission hardening: PASS
Post-M1G KI-064 import progress/rename hardening: PASS
Post-M1G KI-054 shutdown lock/retry hardening: PASS
M1H-1 listener membership source/test/CI/package: PASS at 84e7bab99009e5871934a960908945ceb00a10a9 / CI 35410830197
M1H-1 focused Minecraft walk-in/walk-out/re-entry acceptance: deferred to backlog
M1H-2 recovery source/test/CI/package: PASS at aa72f0d2fc9f8cde53cd956389beca1743d06165 / CI 35411480844
M1H-2 focused Minecraft reload/loss/starvation acceptance: deferred to backlog
M1H-3 Sable/VS2 moving-source source/CI/package: PASS at 5cd6d6ddcad4b5b4887b903f471de0f2f812795c / CI 35451236630
M1H-3 focused Minecraft Sable/VS2 movement acceptance: deferred to backlog
M1H source slices 1-3: complete; focused runtime checks deferred; M1J modern finite multispeaker active
M1J first implementation checkpoint source/test/CI/package: PASS at b557773b9c6f6b8029aec132a1706f0d8da914bd / CI 35466635285
M1J current hardened head before this docs cleanup: cfea9664f2a8d7df1e8efe457795811bd14d3f6d / CI 35466946934
M1J protocol: v8
M1J shared playback/client projection deterministic coverage: PASS
M1J focused Minecraft multispeaker acceptance: deferred / not yet recorded
```

## API convergence progress

Legacy-name MP3/WAV compatibility calls now route through the modern finite engine, including `*All` and `*At`. Their byte payloads use transient MediaAssets and the same analyzer/decoder path as prepared files; `*All` therefore uses the shared M1J playback authority instead of the inherited expected-member barrier. OGG/generic/live paths remain legacy pending separate decisions.

## Standalone block and license cleanup

The inherited standalone `hqspeaker:hq_speaker` Minecraft block has been removed. It was present in the original upstream source and was never part of the current product direction. The normal `computercraft:speaker` upgraded by the mixin/composite remains the only block surface.

The internal custom-audio sound event/resource has been renamed to `hqspeaker:hq_audio_source` so it cannot be confused with the removed standalone block. RAW/live and modern finite renderers use that ResourceLocation only as the SoundManager anchor for custom AudioStream playback.

License provenance is now explicit: this fork descends from `tiktop101/CC-HQ-Speakers` via `jvrcruzGAMES/CC-HQ-Speakers`, whose repository license is MPL-2.0. NeoForge metadata has been corrected from the inherited LGPL-3.0 label to MPL-2.0.

## Shipping description truthfulness

NeoForge metadata now advertises the actual supported core surface: modern MP3/common-WAV finite playback, multispeaker control, RAW PCM, and optional stream/ICY helpers. Historical OGG/generic whole-file support is no longer advertised.

## Retired finite surface cleanup

The old OGG/generic whole-file methods are physically absent from `HQSpeakerPeripheral`. The composite no longer carries a redundant blacklist for methods that cannot be reflected in the first place.

## Decoder dependency cleanup

The retired whole-file finite decoder no longer requires Java Sound MP3 SPI support. `mp3spi` and `tritonus-share` are removed from Jar-in-Jar packaging; JLayer remains because both modern progressive MP3 and optional live MP3 streaming use it directly.

## Post-M1J finite teardown

The supported finite surface now has one engine. Legacy-name `speakMp3`/`speakWav` (including `All`/`At`) route through MediaAsset admission and modern finite playback. Historical OGG/generic whole-file aliases are not exposed on the normal upgraded CC:T speaker.

The inherited complete-file finite implementation has been physically removed:

- no legacy finite server timeline/terminal state remains in `HQSpeakerPeripheral`;
- no whole-file finite decoder remains in `HQAudioStream` / `HQSpeakerClientHandler`;
- `FiniteAudioTrack` and its test are removed;
- legacy finite control/status payloads are removed;
- `HQSpeakerAudioPacket` is RAW/live-only;
- network protocol is now v9 with 9 registered payloads.

Checkpoint `fcb6670dd818412c15509129105aa7f54be9d5ba` / CI `35470940030` passed both supported NeoForge targets.

## Post-M1J dead-code recheck

`FileFiniteAudioStream` and `HQSpeakerCluster` were removed as dead code earlier. `FiniteAudioTrack` was initially retained because the old whole-file client used it; after OGG/generic finite retirement and the modern MP3/WAV bridge, that client path was removed and `FiniteAudioTrack` is now removed as well. `HQAudioStream` remains only for RAW/live audio.

## Read order

1. `HANDOFF-2026-09-19-M1H-START.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1G-SCOPE-DECISIONS-2026-09-14.md`
7. `FUTURE-CLEANUP.md`
8. `ARCHITECTURE.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
