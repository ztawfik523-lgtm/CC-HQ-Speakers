# CC:HQ Speakers — complete next-chat handoff

Date: 2026-09-12

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1e-server-authoritative-finite`

Reviewed documentation base before this handoff: `0aba08f70f15eb30830ef053f557103558a24e9b`

Exact M1E code-bearing implementation checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`

Exact M1E CI run: `34658958488`

Frozen M1D checkpoint: `4a2cd5de96228fc091226c7e72fb669b82be258c`

Frozen M1D final CI run: `34635484316`

This document is intended to be sufficient by itself for a fresh chat. Re-read the current branch source and CI before changing code. If newer source disagrees with this file, current source wins.

---

## 1. How to respond/work on this project

The user wants concrete implementation discussion.

Avoid vague wording such as:

- "decouple state from transport";
- "use robust buffering";
- "establish a source of truth";
- "improve lifecycle semantics".

Instead name exact behavior/classes/packets/fields. Example:

> `HQFiniteMediaServer` starts the finite clock immediately when `playPrepared()` succeeds. A client that becomes decoder-ready later receives `HQFiniteMediaStatePacket`, seeks the local bridge to the current canonical server position, then starts audio. Client READY/ERROR telemetry never rewrites the server clock.

The user frequently asks to recheck/prove/disprove things. Do not turn plausible ideas into project facts without exact source, tests, CI, or runtime evidence.

When there are multiple meaningful options, present the concrete tradeoffs instead of silently picking one. Minor implementation details can be chosen directly.

---

## 2. Evidence precedence

Use this order when claims conflict:

1. successful exact-target Minecraft runtime evidence;
2. exact current source;
3. exact current CI/build/package evidence;
4. `docs/VERIFIED-FACTS.md`;
5. `docs/CURRENT-STATE.md`;
6. compatibility/design docs;
7. `docs/ROADMAP.md`;
8. milestone historical docs;
9. old prototype/P0 material.

A green CI/Gradle build is not audible Minecraft proof.

Do not call a runtime script PASS unless it was actually executed successfully in Minecraft on the target stack.

---

## 3. Exact target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Important checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- completed M1E code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`
- reviewed docs base before this handoff: `0aba08f70f15eb30830ef053f557103558a24e9b`

Important CI:

- M1D final run `34635484316`: success on NeoForge 21.1.247 and 21.1.248
- M1E code-bearing run `34658958488`: success on NeoForge 21.1.247 and 21.1.248

A direct comparison from `d0e66ab...` to `0aba08f...` shows only documentation files changed after M1E. Therefore `d0e66ab...` remains the exact implementation proof anchor.

---

## 4. Product identity — settled

CC:HQ Speakers is a programmable ComputerCraft speaker peripheral, not a Java music player.

Lua decides whether audio is music, speech, alarms, notifications, ambience, soundboards, playlists, etc.

Java exposes technical capabilities only.

Do not add permanent application concepts such as:

- music/effects/notification lanes;
- Java playlist management;
- automatic application-level priorities;
- client song-library UX.

The normal `computercraft:speaker` remains the product surface.

---

## 5. Source categories — settled

### Standard CC:T speaker

Preserve native CC:T behavior:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

The composite delegates these to the real CC:T `SpeakerPeripheral`.

### HQ RAW/feed

`speakPCM` is an open-ended producer feed with bounded backpressure.

RAW does not have truthful finite duration, arbitrary seek, or finite EOF.

### Finite encoded media

Finite files have truthful server-owned:

- duration;
- position;
- pause/resume;
- seek;
- looping;
- volume;
- natural EOF.

### Live network audio — later

Live MP3/HLS/TS are open-ended and must not fake finite duration/seek. Future pause/resume reconnects to the current live point.

Do not force finite asset semantics onto live streams just because both move bytes progressively.

---

## 6. Finite format/channel scope — settled

Core final finite formats:

- MP3 / MPEG Layer III
- common WAV

Wanted but separately gated:

- normal native FLAC only after analyzer/decoder/seek/package/runtime proof

Not final requirements:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- unusual/compressed/telephony WAV variants
- >2-channel finite input

One physical Minecraft speaker renders one mono positional source.

Input rule:

- mono -> mono
- stereo -> downmix to mono
- >2 channels -> reject

Common WAV target:

- unsigned 8-bit PCM
- signed 16-bit PCM
- signed 24-bit PCM
- signed 32-bit PCM
- 32-bit IEEE float

Reject unusual WAV encodings rather than preserving every JavaSound edge case.

Frozen M1D historically analyzes OGG/AIFF/AU. That historical fact is not a final product promise.

---

## 7. No client song cache — settled

The final finite engine does not keep client-side songs on disk.

Do not build:

- persistent `.part` music files;
- completed client media library;
- LRU song cache;
- sparse-file range cache;
- block-file cache;
- cross-restart resume;
- cache database.

The server owns the complete encoded asset.

The client keeps only bounded temporary active-playback memory:

- encoded bytes currently needed;
- small codec context/pre-roll;
- bounded decoded mono PCM.

If old encoded data is needed again, request it from the server again.

The current M1E `hqspeaker-cache/*.part/.media` client files are only a temporary bridge. M1F/M1G delete that architecture.

---

## 8. Final finite streaming model — settled

A file can be finite while its encoded bytes are streamed progressively.

Target flow:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> canonical finite server clock starts immediately
    -> relevant client requests encoded bytes near current need
    -> server validates request and reads bounded range off-thread
    -> bounded encoded response packet(s)
    -> bounded client encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

Do not decode the whole song to PCM on the server.

Do not require the whole encoded file on the client.

Seek/late join:

```text
server canonical target time
    -> server chooses codec-appropriate anchor at/before target
    -> client requests encoded bytes from anchor
    -> client rebuilds codec context/pre-roll
    -> audible output joins current canonical server position
```

If a client underruns, canonical server time continues. That client may become locally silent, refill, then rejoin current time.

---

## 9. Important CC:T 1.120.0 compatibility facts

- peripheral type is `speaker`;
- `playAudio` accepts signed 8-bit samples at 48 kHz;
- maximum contiguous standard `playAudio` call is `128 * 1024` samples;
- native speaker audio has one pending DFPWM/audio buffer and emits `speaker_audio_empty` when another can be accepted;
- notes are independent of native sound/audio state;
- native `SpeakerPeripheral.stop()` sets a stop flag handled on a later server tick;
- delegate exact source behavior instead of copying documentation defaults.

Historical runtime note: after native `stop()`, immediately sending another native `playAudio` can race the next-tick stop; `sleep(0.05)` avoids that in tests.

---

## 10. Completed foundation summary

### M0.5

Lifecycle/redesign preparation. Provider/composite cleanup occurs on speaker removal, level unload, and server stop. It intentionally did not repair obsolete finite architecture.

### M1A — standard compatibility/output ownership

Important current behavior:

- standard methods delegate to CC:T;
- native notes remain independent;
- one incompatible HQ continuous source replaces the previous HQ continuous source;
- repeated accepted `speakPCM` continues the same RAW feed;
- `speakMaxSamples()` reports 131072;
- RAW uses separate `hqspeaker_audio_empty` pacing;
- RAW admission is bounded by the inherited 16-packet queue plus 135872 outstanding samples;
- RAW outstanding duration drains by 2400 samples/server tick;
- ownership-changing calls on one physical speaker are serialized.

Known boundary: inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path.

M1A Minecraft runtime acceptance is still pending unless a later exact run is recorded.

### M1B — server media assets

`MediaAssetStore` provides:

- UUID media identity independent of physical speakers;
- server disk-backed `.part` -> atomic `.media` import;
- bounded 64 KiB copying;
- caller-supplied per-asset/total quotas;
- pre-copy reservation;
- retain/release lifetime;
- final-reference deletion;
- seekable encoded reads;
- startup orphan pruning;
- root file lock;
- shutdown/import race handling;
- retryable close cleanup.

This server storage remains required even though client caching was removed.

### M1C — ComputerCraft local file import/config

Flow:

```text
ComputerCraft file
    -> temporary writable HQ staging mount
    -> immutable shared server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Helpers/capabilities include `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, `playFile`.

Prepared ownership is tied to ComputerCraft computer ID. Playback takes its own asset reference, so releasing the prepared handle does not stop active playback.

Server storage defaults:

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

`0` removes the corresponding HQ-specific quota.

These settings do not alter ComputerCraft filesystem capacity.

Important overflow fix: CC:T writable mounts internally add 500 bytes (`MountConstants.MINIMUM_FILE_SIZE`), so effectively-unlimited staging clamps supplied capacity to `Long.MAX_VALUE - 500`.

---

## 11. M1D — frozen server media analysis

Frozen head: `4a2cd5de96228fc091226c7e72fb669b82be258c`

Final CI: `34635484316`, both target NeoForge versions green.

M1D analyzes the exact immutable committed server asset, not filename extension and not mutable staging bytes.

Historical analyzed formats:

- MP3
- OGG Vorbis
- WAV
- uncompressed AIFF/AIF
- AU/SND

Useful retained M1D facts:

- one bounded 64 KiB analysis window;
- actual server-derived format/duration/sample-rate/channels/bits-per-sample/size metadata;
- MP3 seek points are real scanned frame byte offsets;
- OGG seek points are real Ogg page offsets;
- seek metadata is bounded to at most 4096 points and self-thins;
- failed analysis releases the unexposed asset;
- analysis happens on immutable committed bytes, preventing staging TOCTOU.

Current MP3 duration is encoded-frame duration; encoder delay/padding is not subtracted.

M1D Minecraft runtime acceptance remains pending unless later exact evidence records a pass.

---

## 12. M1E — completed server-authoritative finite state

Exact code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`

Exact CI run: `34658958488`, success on NeoForge 21.1.247 and 21.1.248 with tests/package verification.

Minecraft runtime acceptance remains pending.

### Server semantics

`HQFiniteMediaServer` canonical states are:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

Removed from canonical semantics:

- server `LOADING` based on renderer readiness;
- `successfulRenderers` authority;
- canonical `observed`;
- 15-second no-renderer failure;
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED authority.

A successful finite play starts canonical server time immediately at position 0, even with no listeners.

Natural non-looping EOF is server duration/clock driven.

Looping wraps server position.

Non-looping `seek(duration)` immediately ends.

Looping `seek(duration)` wraps to 0.

Status/control paths finalize elapsed EOF before reporting/applying state, so the server cannot truthfully remain PLAYING past known duration.

Canonical EOF closes the old transfer before releasing the playback asset reference, preventing deletion while the temporary transfer still reads the asset.

### Protocol v4

Current HQ Speaker network protocol is `4`.

M1E adds `HQFiniteMediaStatePacket` as authoritative server -> client mutable state.

STATE contains:

- source/media/generation;
- PLAYING/PAUSED/ENDED/ERROR;
- canonical position;
- duration;
- volume;
- looping;
- optional server error detail.

`HQFiniteMediaBeginPacket` remains temporary bridge/setup data for the old transfer.

Client -> server finite telemetry is now only:

- `READY` — temporary complete-file decoder is ready and requests fresh authoritative state;
- `ERROR` — diagnostic only.

STARTED/PAUSED/RESUMED/SEEKED/ENDED were removed from protocol v4 finite status and cannot rewrite canonical state.

### Temporary M1E client bridge

The client still downloads the entire encoded asset to `hqspeaker-cache` and uses `FileFiniteAudioStream`.

But it no longer auto-starts from byte/time 0 after transfer.

Current bridge:

```text
full transfer completes
    -> construct old file decoder
    -> send READY
    -> receive fresh authoritative STATE
    -> start/seek local renderer at CURRENT server position
```

If authoritative state is PAUSED, prepare the correct paused position without audible playback.

If authoritative state is ENDED/ERROR, destroy stale local bridge state.

The client's local `FinitePlaybackClock` is only renderer projection/local restart state. It is not canonical.

### M1E tests

Pure Java includes deterministic `FinitePlaybackClock.reachedEnd()` behavior plus existing seek/loop/pause/resume coverage.

Focused runtime contract:

`scripts/m1e_server_authority_test.lua <small-mp3-or-wav>`

It checks immediate PLAYING, position advancement independent of renderer readiness, pause freeze, resume progression, non-looping exact-duration END, and looping exact-duration wrap.

This script has not yet been recorded as a successful Minecraft run. Do not call M1E runtime PASS.

---

## 13. Current transitional code after M1E

These are intentional old-bridge behaviors and define M1F/M1G work:

- recipients are captured once at play start;
- server pushes the complete encoded file rather than waiting for demand;
- server file reads happen during server tick;
- client writes `.part/.media` on the Minecraft client thread;
- decoder requires a complete client file;
- no new listener can dynamically join after playback starts.

The semantic authority problem is fixed. The transport/decoder architecture is not yet final.

Important current files/classes:

- `src/main/java/com/tom/hqspeaker/peripheral/HQFiniteMediaServer.java`
- `src/main/java/com/tom/hqspeaker/client/HQFiniteMediaClient.java`
- `src/main/java/com/tom/hqspeaker/client/FileFiniteAudioStream.java`
- `src/main/java/com/tom/hqspeaker/media/FinitePlaybackClock.java`
- `src/main/java/com/tom/hqspeaker/media/FiniteMediaAnalyzer.java`
- `src/main/java/com/tom/hqspeaker/media/MediaAssetStore.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaBeginPacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaChunkPacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaEndPacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaControlPacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaStatePacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaStatusPacket.java`
- `src/main/java/com/tom/hqspeaker/network/HQSpeakerNetwork.java`

---

## 14. Proven decoder/streaming constraints

### MP3 progressive input

The exact shipped JLayer family was tested with real MP3 bytes.

When temporary missing bytes cause the decoder input to wait until more bytes arrive, progressive decode can reproduce complete decode output.

When temporary missing bytes are reported as permanent EOF, JLayer can treat the stream as ended/incomplete and output differs.

Therefore:

**temporary lack of encoded bytes is not EOF.**

The decoder worker may wait/refill. The Minecraft sound/game threads must not block waiting for network bytes.

### MP3 seek/rejoin

MPEG Layer III uses a bit reservoir. Starting exactly at the desired frame can produce wrong initial audio because that frame may depend on previous frame data.

Therefore seek/rejoin needs an earlier server-selected MP3 frame anchor and silent decode/discard pre-roll before audible target output.

M1D already stores real scanned MP3 frame offsets, which is useful for this.

### Client/server clocks

Do not compare raw `System.nanoTime()` values between server and client JVMs. Their origins are process-local.

Use server-reported canonical media positions. Measure real drift before adding latency prediction.

### Packet sizing

Current 256 KiB finite chunk size is protocol-valid but not assumed optimal. M1O should benchmark 64/128/256 KiB under actual Minecraft packet compression/performance.

---

## 15. Next implementation milestone — M1F demand-driven finite transport

Do not mix the final decoder rewrite into M1F unless necessary.

M1F goal: replace fixed-recipient whole-file push with validated client-requested encoded ranges and off-thread server IO.

### New request flow

Conceptual client -> server packet:

```text
source UUID
playback generation
asset UUID
encoded offset
bounded requested length
```

Conceptual server -> client range data:

```text
source UUID
playback generation
asset UUID
encoded offset
bytes
```

Exact packet names/fields may be chosen during implementation, but generation/asset/offset/length validation is required.

### Server validation before scheduling IO

Check:

- finite session exists and is active;
- generation matches;
- requested asset is the session asset;
- player is in correct dimension;
- player is currently relevant/in range;
- offset >= 0;
- length > 0 and <= configured/protocol maximum;
- offset + length stays within encoded asset size;
- per-player outstanding request count/rate/bandwidth limits permit work.

### Server IO worker

- retain/hold safe asset lifetime while read is in flight;
- perform seek/read off the Minecraft server tick;
- cap allocations/read size;
- after IO finishes, return to server thread and re-check generation/relevance/session before sending;
- discard stale work after stop/replacement/leave;
- release temporary read lifetime safely.

### Client behavior in M1F

M1F can still feed the temporary bridge as needed, but the final direction is bounded active RAM only.

No final client disk song cache.

Seek/stop/replacement/leave should cancel future demand and drop stale returned ranges.

### Seek-anchor rule

Clients do not need the entire server seek table.

For seek/late join the server chooses a codec-appropriate anchor at/before the desired canonical position and supplies enough layout/anchor information for the client to request/decode forward.

M1F tests should cover:

- bad/stale generation;
- wrong asset;
- negative/out-of-range offset;
- oversized length;
- dimension/range invalidation;
- bounded outstanding work;
- cancellation;
- in-flight asset lifetime;
- replacement while IO is running;
- stale completion discard;
- no server-tick file reads in the new range path.

---

## 16. M1G — core progressive MP3/common-WAV engine

M1G completes the single-speaker progressive decode path without waiting for FLAC.

Pipeline:

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

### MP3

- use exact shipped JLayer path unless another Java decoder is proven better;
- temporary missing bytes wait/refill rather than becoming EOF;
- server MP3 frame metadata supplies seek/rejoin anchor;
- decode/discard earlier frames as pre-roll;
- sound thread only consumes ready PCM.

### WAV

Final supported WAV contract should match our own converter:

- mono/stereo only;
- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit float.

Server metadata needs internal layout sufficient for direct time -> byte mapping:

- audio data offset;
- audio data length;
- encoding;
- bits/sample;
- frame size;
- sample rate;
- channel count.

Stereo downmixes to mono.

At M1G completion, active prepared/local advertisement can narrow from historical M1D formats to MP3 + implemented common WAV.

---

## 17. M1H — dynamic listeners and recovery

After progressive single-speaker decoding works:

- discover newly relevant players dynamically;
- send current state when they enter range;
- late listener starts around current server position;
- leaving range stops/parks local renderer and cancels range demand;
- returning while playback active rejoins current time;
- returning after stop/end stays silent;
- dimension change is safe;
- speaker/chunk removal is safe;
- disconnect/reconnect is safe;
- resource reload/F3+T is safe;
- stale generations/range replies cannot restart old sound;
- underrun means local refill/rejoin, never server pause;
- VS2 position updates remain supported.

This closes the historical stale-audio/range-local-stop issue.

---

## 18. M1I — optional native FLAC extension

FLAC is desired but must never block completion of MP3/WAV.

Only advertise normal native `.flac` after proving:

- byte-based FLAC identification;
- STREAMINFO metadata/duration;
- mono/stereo validation;
- progressive bounded decode;
- seek/rejoin anchor behavior;
- malformed input/checksum handling;
- cancellation/bounded RAM;
- stereo downmix;
- packaged dependency behavior on both NeoForge targets;
- Minecraft runtime playback.

Do not add Ogg-FLAC.

If a clean implementation is not found, leave FLAC unadvertised.

---

## 19. Later M1 milestones

### M1J — functional multispeaker/shared clocks

- one server asset may back many speaker playbacks;
- synchronized playbacks share a server sync-clock ID;
- no expected-global-member/expected-tap barrier;
- each block remains independently programmable;
- each physical speaker keeps its own mono positional renderer;
- migrate inherited `*All`/`*At` paths into the modern ownership/state design.

### M1K — active-session sharing/fan-out optimization

Only after semantics are correct:

- coalesce duplicate active range requests where useful;
- share decode/PCM production for identical timelines where safe;
- keep independent positional sources;
- do not reintroduce persistent client caching.

### M1L — legacy finite migration/removal

- route useful `speakMp3(bytes)` / `speakWav(bytes)` compatibility paths into the asset engine where sensible;
- large files use prepared/playFile assets;
- remove/deprecate legacy OGG finite APIs;
- remove old whole-packet/whole-PCM decoder and obsolete finite packets/state once no callers remain.

### M1M — RAW finalization

Keep bounded RAW backpressure/`hqspeaker_audio_empty`; keep finite duration/seek unavailable; move multi-speaker RAW helpers away from old expected-group design.

### M1N — Minecraft/OpenAL cleanup

- proper speaker sound category;
- one logical gain stage plus Minecraft category/master scaling;
- mono positional finite output;
- F3+T/resource reload recovery;
- no stale channels;
- correct attenuation/VS2 movement.

### M1O — lifecycle/performance hardening

Each milestone must already have tests. M1O is final stress/profile work:

- bounded executors/queues under many players/speakers;
- cancellation storms/seek spam;
- repeated replacement/stop/rejoin;
- integrated-server restart/world unload;
- memory/network/tick profiling;
- packet-size benchmark;
- verify no client song state persists.

### M1P — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify metadata, mixins, decoder dependencies, bundled `hqspeaker.lua`.

### M1Q — consolidated Minecraft acceptance

Final M1 runtime batch should include:

- standard CC:T compatibility;
- HQ RAW;
- MP3;
- every supported common WAV variant;
- FLAC only if M1I succeeded;
- >8 MiB and 50-100+ MiB finite files;
- progressive audible start;
- pause/resume/seek/loop/volume/EOF;
- replacement;
- late join;
- leave/return;
- stop while away;
- dimension change;
- F3+T;
- disconnect/rejoin;
- synchronized/independent multispeaker;
- mono downmix;
- bounded RAM/network;
- server/client/sound-thread stall checks.

OGG/AIFF/AU are not required final acceptance formats.

---

## 20. Post-M1 roadmap

### M2 — Sound Physics Remastered

Only after positional renderer lifecycle is stable. Keep separate physical OpenAL sources for separate speaker blocks so occlusion/reverb remains spatially correct.

### M3 — live/open-ended streams

Live MP3/HLS/TS with truthful live semantics, reconnect-to-current-live pause/resume, one gain stage, correct HLS progression, incremental TS, unsupported-codec failures, bounded HTTP/resources, dynamic listeners.

### M4 — release cleanup

- final docs/API surface;
- remove obsolete prototype/dead/P0 material;
- remove obsolete OGG/AIFF/AU finite surfaces after the replacement engine owns all finite playback;
- decide fate of separate `hqspeaker:hq_speaker` block;
- resolve top-level MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch without silently relicensing.

---

## 21. Known issues that still matter

High priority / next work:

- fixed finite recipient set still prevents true late join;
- server still pushes complete file;
- server bridge still does file reads on server tick;
- client bridge still writes full file on client thread;
- complete local file still required by `FileFiniteAudioStream`;
- no final client-requested range protocol;
- no progressive MP3/WAV decoder path yet;
- MP3 starvation must not be treated as EOF;
- MP3 seek/rejoin needs bit-reservoir pre-roll;
- current historical WAV acceptance is broader than final converter scope;
- FLAC remains unproven.

Later retained issues:

- inherited `*All` / `*At` bypasses;
- inherited legacy finite 8 MiB byte APIs;
- legacy decoder unbounded executor/full decoded PCM allocations;
- stream volume double application;
- HLS media-sequence/window progression;
- non-incremental TS;
- unsupported TS audio fallthrough;
- live server state is still intent, not full renderer/network truth;
- expected-tap multispeaker deadlock/leak design;
- separate custom HQ block duplication;
- repository license metadata mismatch.

See `docs/KNOWN-ISSUES.md` for the maintained issue list.

---

## 22. Runtime/testing status

Do not overstate runtime proof.

Known historical runtime evidence includes M0/basic native/HQ PCM/MP3 behavior, but several later milestone scripts remain unexecuted or not recorded as passing.

Important scripts:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua`
- `scripts/m1c_local_import_test.lua`
- `scripts/m1d_media_analysis_test.lua` — historical M1D semantics, contains obsolete renderer-observed assumptions
- `scripts/m1e_server_authority_test.lua` — active M1E runtime contract

M1E source/test/package CI is green, but M1E Minecraft runtime PASS is still pending.

Testing rule: each implementation milestone ships deterministic unit/component tests with the code; do not defer correctness testing to M1O/M1Q.

---

## 23. Next-chat starting procedure

When a new chat receives this file:

1. Read `docs/NEXT-CHAT-HANDOFF.md`.
2. Read `docs/CURRENT-STATE.md` and `docs/VERIFIED-FACTS.md`.
3. Read `docs/ROADMAP.md`, `docs/M1E-SERVER-AUTHORITY.md`, and `docs/M1E-FINITE-STREAMING-DESIGN.md`.
4. Fetch the current branch head and compare it to the M1E code-bearing checkpoint.
5. Re-read current `HQFiniteMediaServer`, `HQFiniteMediaClient`, finite network packets, `FinitePlaybackClock`, and `MediaAssetStore` before editing.
6. Check current GitHub Actions state.
7. If implementation source is still unchanged after `d0e66ab...`, begin M1F.
8. Keep M1F scoped to demand-driven transfer/off-thread server IO and its tests; do not accidentally implement all of M1G/M1H at once.
9. After M1F, recheck source, tests, CI, docs, and runtime boundary before moving on.

---

## 24. Final settled decisions — do not reopen casually

- programmable peripheral, not Java music player;
- preserve standard CC:T speaker contract;
- server owns finite time/state/EOF;
- finite server clock starts immediately;
- no listener/readiness gating;
- finite files stream encoded bytes progressively;
- no persistent client song cache;
- bounded active RAM only;
- seek while streaming remains supported;
- late join remains supported;
- slow clients never slow canonical time;
- MP3 + common WAV are core;
- native FLAC is optional/gated;
- no final OGG/AIFF/AU requirement;
- mono positional physical speaker output;
- stereo downmix to mono;
- >2 channel finite input rejected;
- large files remain supported;
- server does not stream whole-song PCM;
- server file IO/decode work must not block game/sound threads;
- no raw server/client `System.nanoTime()` comparison;
- functional multispeaker correctness before sharing/optimization;
- SPR after finite positional lifecycle is stable;
- live streams after finite/local media is solid.

This is the project state to continue from unless newer source/runtime evidence disproves it.
