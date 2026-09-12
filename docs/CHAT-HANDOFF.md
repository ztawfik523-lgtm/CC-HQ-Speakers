# CC:HQ Speakers — canonical continuation handoff

Date: 2026-09-12

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1e-server-authoritative-finite`

Current documentation freeze before this file: `17205142cc6328b55548fa281e1f511b2670c3bd`

Exact M1E code-bearing checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`

This file is the canonical handoff for another chat. It is intentionally standalone: a new chat should be able to understand the product, completed milestones, current code boundary, accepted architecture, next roadmap, exact evidence, and user preferences from this file alone.

Before modifying code, always re-read the current branch source and current GitHub Actions state. If this file and newer source disagree, current source wins.

---

## 1. How to work with the user

The user wants concrete implementation discussion, not vague architecture language.

Bad:

> decouple playback state from transport and use robust buffering.

Good:

> `HQFiniteMediaServer` starts the canonical finite clock immediately when `playPrepared()` succeeds. A client which finishes buffering later asks for current server state and starts its renderer at that current position. The server never waits for renderer readiness.

Name classes, packets, fields, states, and exact behavior whenever possible.

The user frequently asks to recheck, research, prove, and disprove assumptions. Treat plausible ideas as hypotheses until exact source, tests, CI, or runtime evidence supports them.

When there are multiple meaningful approaches, briefly present the concrete tradeoffs instead of silently picking one. Minor implementation details can be chosen directly.

Do not reopen settled product decisions unless new source/runtime evidence makes them impossible.

---

## 2. Evidence precedence

When information conflicts, use this order:

1. successful exact-target Minecraft runtime evidence;
2. exact current source;
3. exact current CI/build/package evidence;
4. `docs/VERIFIED-FACTS.md`;
5. `docs/CURRENT-STATE.md`;
6. compatibility/design docs;
7. `docs/ROADMAP.md`;
8. milestone-specific historical docs;
9. older prototype/P0 material.

A green Gradle/GitHub Actions run proves compilation, tests, and package checks. It does **not** prove audible Minecraft behavior.

Any runtime script which has not actually passed in Minecraft must remain labeled runtime-pending.

---

## 3. Exact target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Important exact checkpoints:

- inherited fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- completed M1E code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`

Important CI:

- M1D final run `34635484316`: NeoForge 21.1.247 and 21.1.248 success
- M1E code-bearing run `34658958488`: NeoForge 21.1.247 and 21.1.248 success
- documentation head `17205142cc6328b55548fa281e1f511b2670c3bd` run `34663802799`: success

Do not confuse a later documentation-only head with the exact M1E implementation proof point.

---

## 4. Product identity — settled

CC:HQ Speakers is a **programmable ComputerCraft speaker peripheral**, not a Java music player.

Lua decides whether audio is music, an alarm, speech, a notification, ambience, a soundboard entry, a playlist, or something else.

Java exposes technical capabilities only.

Do not add permanent concepts such as:

- music/effects/notification lanes;
- Java playlist management;
- automatic application-level priorities;
- application-specific song libraries.

The normal `computercraft:speaker` remains the product surface.

---

## 5. Source categories — settled

### Standard CC:T speaker

Preserve standard CC:T behavior:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

The current composite delegates standard behavior to the real CC:T `SpeakerPeripheral`.

### HQ RAW/feed

`speakPCM` is an open-ended producer feed with bounded backpressure.

It must not pretend to have finite duration, arbitrary seek, or finite EOF.

### Finite encoded media

Finite files have truthful server-owned:

- duration;
- position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

### Live network audio — later

Live MP3/HLS/TS are open-ended. They must not fake finite duration/seek. Future live pause/resume means reconnecting to the current live point, not resuming old buffered history.

Do not force finite asset semantics onto live streams merely because both send bytes progressively.

---

## 6. Final finite format/channel scope — settled

Core finite product formats:

- MP3 / MPEG Layer III
- common WAV

Wanted but separately gated:

- normal native FLAC, only after exact analyzer/decoder/seek/package/runtime proof

Not required in the replacement finite engine:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- exotic/compressed/telephony WAV variants
- >2-channel finite input

Frozen M1D historically analyzes OGG/AIFF/AU. That is historical implementation evidence, not a final product promise.

One physical Minecraft speaker is one **mono positional source**.

Input policy:

- mono -> mono
- stereo -> downmix to mono
- >2 channels -> reject

Common WAV target:

- unsigned 8-bit PCM
- signed 16-bit PCM
- signed 24-bit PCM
- signed 32-bit PCM
- 32-bit IEEE float

Reject unusual WAV encodings instead of implementing every representation JavaSound can technically open.

---

## 7. Client caching decision — settled

There is **no final client song cache**.

Do not build:

- persistent `.part` music files;
- completed client media library;
- LRU song cache;
- sparse range cache;
- block-file cache;
- cross-restart partial-download resume;
- cache database.

The server owns the complete encoded asset.

The final client keeps only bounded temporary memory for active playback:

- encoded bytes currently needed;
- small codec pre-roll/context;
- bounded decoded mono PCM.

When old bytes are needed again, the client requests them from the server again.

A large file must not imply equivalent client disk or RAM use.

The current M1E `hqspeaker-cache/*.part/.media` client files are a **temporary bridge** which M1F/M1G will delete.

---

## 8. Final finite streaming model — settled

A finite file remains finite even when its encoded bytes are streamed progressively.

Target flow:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server finite playback starts canonical clock immediately
    -> relevant client asks for encoded bytes near current need
    -> server reads bounded range off-thread
    -> bounded encoded packets
    -> bounded client encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

Do not decode an entire song to PCM on the server and stream PCM over the network.

Do not require the whole encoded file on the client before playback in the final engine.

Seek/late-join model:

```text
server chooses codec-appropriate anchor at/before desired time
    -> client requests bytes from that anchor
    -> codec-specific pre-roll/context is rebuilt
    -> audible output joins current server position
```

A slow client never slows canonical server time. If it underruns, it may become locally silent, refill, then rejoin current server position.

---

## 9. Important CC:T compatibility facts

For CC:T 1.120.0:

- peripheral type is `speaker`;
- `playAudio` accepts signed 8-bit samples at 48 kHz;
- maximum contiguous standard `playAudio` input is `128 * 1024` samples;
- native speaker audio has one pending audio/DFPWM buffer and emits `speaker_audio_empty` when another can be accepted;
- notes are independent of sound/audio state;
- native `SpeakerPeripheral.stop()` uses a stop flag handled on a later server tick;
- exact source defaults should be preserved through delegation rather than reimplemented from documentation.

Historical runtime detail: after native `stop()`, immediately sending another native `playAudio` may race the next-tick stop processing; a one-tick/`sleep(0.05)` delay avoids that in tests.

---

## 10. M0.5 / M1A / M1B / M1C summary

### M0.5

Completed lifecycle/redesign preparation. Added deterministic composite/provider cleanup on speaker removal, level unload, and server stop. It did not repair obsolete finite architecture.

### M1A — standard compatibility/output ownership

Important behavior:

- standard speaker methods delegate to CC:T;
- native notes remain independent;
- one incompatible HQ continuous source replaces the prior HQ continuous source;
- repeated accepted `speakPCM` while RAW owns output continues one raw feed;
- `speakMaxSamples()` reports 131072;
- RAW has separate `hqspeaker_audio_empty` pacing;
- inherited 16-packet queue plus sample-duration accounting bounds RAW admission;
- RAW outstanding samples drain at 2400 samples/server tick;
- ownership-changing calls on one physical speaker are serialized.

Known boundary: inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path and are later migration work.

M1A Minecraft runtime acceptance remains pending unless subsequently executed successfully.

### M1B — server media assets

`MediaAssetStore` provides:

- UUID media identity independent of speakers;
- server disk-backed `.part` -> atomic `.media` import;
- bounded exact-size copying;
- per-asset/total quotas;
- reservation before copy;
- retain/release lifetime;
- final-reference deletion;
- seekable reads;
- startup orphan pruning;
- root file lock;
- shutdown/import race handling;
- retryable close cleanup.

This server storage remains important even though final client caching was removed.

### M1C — ComputerCraft local-file import/config

Flow:

```text
ComputerCraft file
    -> temporary writable HQ staging mount
    -> immutable shared server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Helpers/capabilities include prepared import/info/play/release and Lua `prepareFile`, `preparedInfo`, `playPrepared`, `releasePrepared`, `playFile`.

Prepared ownership is tied to ComputerCraft computer ID. Playback takes its own asset reference, so releasing a preparation handle does not kill active playback.

Storage defaults:

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

`0` removes the corresponding HQ-specific quota.

These are only HQ Speaker server-side storage/staging limits. They do not change ComputerCraft filesystem capacity.

Important overflow fix: CC:T writable mounts internally add `MountConstants.MINIMUM_FILE_SIZE` (500 bytes), so the effectively-unlimited staging path clamps supplied capacity to `Long.MAX_VALUE - 500`.

---

## 11. M1D — frozen server media analysis

Frozen head:

`4a2cd5de96228fc091226c7e72fb669b82be258c`

Final run:

`34635484316` — both target NeoForge versions green.

M1D identifies/analyzes the exact immutable committed server asset, not a filename extension or mutable staging file.

Historical formats analyzed:

- MP3
- OGG Vorbis
- WAV
- uncompressed AIFF/AIF
- AU/SND

Useful retained facts:

- one bounded 64 KiB analysis window;
- truthful server-derived format/duration/sample-rate/channels/bits-per-sample/size metadata;
- MP3 seek points are actual scanned frame byte offsets;
- OGG seek points are actual Ogg page offsets;
- seek metadata is bounded to at most 4096 points and self-thins;
- failed analysis releases the unexposed asset;
- immutable committed-byte analysis avoids staging TOCTOU.

Current MP3 duration is encoded-frame duration; gapless encoder delay/padding is not subtracted yet.

M1D Minecraft runtime acceptance remains pending unless subsequently executed successfully.

---

## 12. M1E — completed server-authoritative finite state

M1E exact code-bearing head:

`d0e66ab9135359627086c13647d5241ad778643f`

Exact CI run:

`34658958488` — both NeoForge targets green with tests/package verification.

Minecraft runtime acceptance is still pending.

### What changed

`HQFiniteMediaServer` semantic states are now:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

Removed from canonical server semantics:

- `LOADING` based on renderer readiness;
- `successfulRenderers` authority;
- canonical `observed`;
- 15-second no-renderer failure;
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED authority.

A successful finite play starts canonical server time immediately at position 0, even with no listeners.

Natural non-looping EOF is determined from known server duration/clock. Looping wraps. Non-looping `seek(duration)` ends immediately; looping `seek(duration)` wraps to 0.

Status/control paths finalize elapsed natural EOF before reporting/applying state so canonical state cannot remain PLAYING past duration.

Canonical EOF closes/cancels the temporary old transfer before releasing the playback asset reference, avoiding deletion while that transfer still uses the asset.

### Protocol v4

M1E bumps the HQ Speaker network protocol to `4`.

New `HQFiniteMediaStatePacket` carries mutable authoritative truth:

- source/media/generation;
- PLAYING/PAUSED/ENDED/ERROR;
- canonical position;
- duration;
- volume;
- looping;
- optional server error detail.

`HQFiniteMediaBeginPacket` remains temporary old-bridge setup for immutable/setup data such as source/media/generation, format, total bytes, position, and initial settings.

Client -> server finite telemetry is now only:

- `READY` — temporary complete-file decoder is ready and requests fresh server state;
- `ERROR` — diagnostic only.

Renderer STARTED/PAUSED/RESUMED/SEEKED/ENDED transitions were removed from protocol v4 and can no longer rewrite canonical server truth.

### Temporary M1E client bridge

The client still downloads the complete encoded asset to `hqspeaker-cache` and uses `FileFiniteAudioStream`. That is deliberately temporary.

The semantic correction is:

```text
full transfer completes
    -> client constructs old decoder
    -> client reports READY
    -> server sends fresh authoritative STATE
    -> client seeks/starts at CURRENT server position
```

If server state is paused, the client prepares the authoritative paused position without starting sound. If server state is ended/error, the stale local bridge is destroyed.

The client local `FinitePlaybackClock` is only a renderer projection for local restart/resource behavior; it is not canonical authority.

### M1E tests/runtime state

Pure Java coverage includes deterministic `FinitePlaybackClock.reachedEnd()` behavior plus earlier pause/resume/seek/loop tests.

Focused runtime script:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It checks immediate server PLAYING, position advancement independent of renderer readiness, pause freeze, resume progression, exact-duration non-looping END, and looping exact-duration wrap.

This script has not yet been recorded as a successful Minecraft run, so **do not call M1E runtime PASS**.

---

## 13. What remains transitional after M1E

These are intentionally still old architecture and define the M1F/M1G boundary:

- recipients captured once at playback start;
- server blindly pushes the whole encoded file;
- server asset reads happen during server tick;
- client writes `.part/.media` files on the client thread;
- decoder requires a complete local encoded file;
- no new listener can dynamically join after playback start.

Do not polish these old mechanisms into permanent architecture.

---

## 14. Proven/important decoder constraints

### MP3

Temporary lack of encoded bytes is **not EOF**.

The exact shipped JLayer family was tested with a producer that waits when more bytes have not arrived: progressive decoding can produce the same decoded output as complete-file input. Returning EOF merely because a current network window is empty is incorrect.

MPEG Layer III uses a bit reservoir. Random seek/rejoin should start from an earlier known MP3 frame, silently decode/discard pre-roll, then make audio audible near current server time. Starting exactly at an arbitrary target frame is not guaranteed to reproduce correct immediate output.

Normal network starvation should not require tearing down JLayer if its worker can simply wait for more input. Never block Minecraft's sound thread waiting for network data.

### WAV

The final progressive path should directly support only the common formats listed above and use server-derived PCM layout for time-to-byte mapping/conversion. Do not preserve strange JavaSound-only variants merely because frozen M1D once accepted them.

### FLAC

FLAC is wanted but not proven. It must not block MP3/WAV M1 completion. Only advertise native `.flac` once analyzer, progressive decode, seek/rejoin, malformed-input behavior, packaging, and Minecraft runtime are proven.

---

## 15. Next milestone — M1F demand-driven finite transport

This is the next code work.

Goal: replace fixed-recipient whole-file push without depending on the final decoder yet.

Implement client -> server range demand containing roughly:

```text
source
generation
assetId
offset
bounded length
```

Server response contains roughly:

```text
source
generation
assetId
offset
bytes
```

Required behavior:

- validate active playback, generation, asset identity, player dimension/range, offset/length;
- bound request length, outstanding work, and per-player traffic/IO;
- perform server asset reads on bounded IO workers, never on the server tick;
- retain a safe asset reference while async IO is in flight;
- after read completes, re-check generation/relevance before sending;
- discard stale work after replacement/stop/leave;
- bounded temporary encoded client RAM only;
- no client `.part`, final media file, sparse cache, LRU, or persistent resume;
- arbitrary encoded offsets so seek/rejoin do not require downloading from byte 0;
- response packet maximum may start near current 256 KiB but should remain benchmarkable rather than a permanent semantic constant.

### Seek descriptor rule

Do not ship the entire server seek index to every client.

The server should select a codec-appropriate anchor at/before the desired canonical position and provide the client enough information to request/decode forward from that anchor.

For MP3 this means a known frame offset/time before target plus pre-roll.

For WAV it will mean direct layout/time-to-byte facts once M1G narrows/extends metadata.

### M1F tests

At minimum:

- range bounds;
- stale generation rejection;
- out-of-range/wrong-dimension rejection;
- asset identity validation;
- bounded outstanding work;
- async asset lifetime;
- replacement while IO is in flight;
- leave-range cancellation;
- stale completion discard;
- response packet-size bounds;
- proof that server tick no longer performs finite asset file reads.

M1F is transport only. Do not accidentally fold all decoder/dynamic-listener work into it.

---

## 16. Roadmap after M1F

### M1G — core progressive finite engine: MP3 + common WAV

Build:

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional renderer
```

MP3: starvation != EOF, server-selected frame anchor, pre-roll, bounded worker/PCM queues, non-blocking sound thread.

WAV: mono/stereo only, common 8/16/24/32 PCM + float32, safe stereo downmix, internal data-layout metadata, direct bounded time-to-byte conversion.

When this engine replaces the old file decoder, active prepared/local advertisement can narrow to MP3 + supported WAV. OGG/AIFF/AU are no longer required on the active branch.

### M1H — dynamic listener lifecycle/recovery

- late listener gets current state and current-time bytes;
- leaving range stops local renderer/demand and frees temporary buffers;
- returning rejoins current server time;
- returning after stop/end stays silent;
- dimension change/disconnect/chunk/speaker removal/resource reload safe;
- stale generation/ranges cannot restart old sound;
- underrun causes local refill/rejoin, not server pause;
- preserve VS2 position behavior;
- measure real drift before adding latency prediction.

### M1I — native FLAC extension, gated

Do not let FLAC delay MP3/WAV. Add only after exact analyzer/progressive decoder/seek/error/package/runtime proof. No Ogg-FLAC.

### M1J — functional multispeaker sync

- one server asset can back many playbacks;
- synchronized playbacks use shared server sync-clock ID;
- no expected-member/tap barrier;
- each physical block retains its own positional mono renderer;
- migrate old `*All` / `*At` helpers to current ownership/state semantics.

### M1K — active-session sharing optimization

After M1J correctness, coalesce duplicate range/decode work where useful while keeping physical positional sources independent. Do not add persistent cache to optimize this.

### M1L — legacy finite migration/removal

Route useful MP3/WAV compatibility frontends into the new asset engine; remove obsolete old finite decoder/packet/state paths and legacy OGG-specific APIs when callers are migrated.

### M1M — HQ RAW finalization

Keep bounded RAW producer semantics and migrate multi-speaker RAW lifecycle without fake finite controls.

### M1N — Minecraft/OpenAL cleanup

Correct speaker sound category/gain, F3+T/resource recovery, attenuation, stale-channel cleanup, mono positional output, VS2 movement.

### M1O — lifecycle/performance hardening

Stress many speakers/players, replacement/seek/cancellation storms, world/integrated-server restart, memory/network/tick profiling, packet-size benchmark. Tests are not deferred here: each milestone already carries deterministic tests.

### M1P — CI/package verification

Keep both target NeoForge versions green and verify package/dependencies/ROM Lua module.

### M1Q — consolidated Minecraft acceptance

Final M1 runtime batch: standard CC:T, HQ RAW, MP3, all supported WAV variants, FLAC only if M1I passed, large files, progressive start, finite controls/EOF/replacement, late join/range recovery, reload/disconnect, multispeaker sync, mono downmix, bounded RAM/network and thread-stall checks.

After M1:

- M2 — Sound Physics Remastered
- M3 — live/open-ended streams
- M4 — release cleanup

---

## 17. Known unresolved issues/boundaries

Keep these visible:

- M1E old full-file finite transport/client disk bridge remains until M1F/M1G;
- dynamic listener/range lifecycle is not done until M1H;
- inherited `*All` / `*At` helpers bypass modern ownership until multispeaker migration;
- old legacy finite byte APIs remain until M1L;
- RAW internal/legacy quirks remain later work although current composite reports the truthful 131072 contiguous limit;
- streaming/HLS/TS has known historical defects and is deferred to M3;
- finite/stream gain/category cleanup remains later M1N;
- F3+T/resource lifecycle needs final hardening;
- SPR remains M2;
- separate custom HQ block may be dead/duplicated and needs release decision;
- repository LICENSE vs NeoForge metadata license mismatch must be resolved before public release;
- MP3 gapless delay/padding is not currently subtracted from server duration;
- M1A/M1C/M1D/M1E Minecraft runtime contracts are not PASS unless explicitly executed successfully.

---

## 18. Threading rules going forward

Final finite engine must follow this concrete split:

### Minecraft server thread

- validate requests;
- mutate canonical session state;
- retain/release semantic playback ownership;
- check dimension/range/generation;
- enqueue bounded IO work;
- send completed packets only after revalidation.

### Server IO worker

- open/read bounded server asset ranges;
- hold required asset reference while reading;
- return bytes/result to server thread.

### Minecraft client/main thread

- accept state/range results into bounded coordination structures;
- create/destroy renderer/session state;
- never block on network/disk/decoder progress.

### Decoder worker

- wait for required encoded bytes;
- decode/convert into bounded mono PCM queue;
- cancel cleanly on replacement/seek/leave.

### Sound/OpenAL thread/path

- consume already-ready PCM only;
- never wait for server/network/file IO.

---

## 19. Synchronization rules

Do not compare raw `System.nanoTime()` between server and client JVMs. Its origin is local to a process.

For now, synchronization means server reports canonical position/state and the client joins/seeks to that semantic position.

Do not add ping/2 correction or sophisticated prediction until runtime measurement shows meaningful audible drift.

For several physical speakers, never collapse positional output into one OpenAL source merely because encoded/decode work can be shared. Each physical speaker must remain spatially distinct for attenuation, direction, future SPR occlusion/reverb, and VS2 movement.

---

## 20. Testing rules

Every milestone should ship deterministic unit/component tests for the behavior it introduces. Do not defer all proof to M1O/M1Q.

Runtime acceptance should record:

- exact commit;
- exact JAR SHA-256;
- Minecraft/CC:T/NeoForge versions;
- pass/fail by section;
- relevant logs;
- whether ATM10 or a reduced exact-stack instance was used.

Focused current runtime script:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It has not yet been recorded as a successful Minecraft run.

Final consolidated M1 runtime acceptance comes only after the full replacement architecture is coherent.

---

## 21. Immediate instructions for the next chat

1. Read this file.
2. Fetch the current active branch head and latest CI; do not assume the branch still equals this documentation checkpoint.
3. Confirm that source changes after `d0e66ab9135359627086c13647d5241ad778643f` are still documentation-only before treating M1F as next.
4. Re-read these current source files before implementing M1F:
   - `src/main/java/com/tom/hqspeaker/peripheral/HQFiniteMediaServer.java`
   - `src/main/java/com/tom/hqspeaker/client/HQFiniteMediaClient.java`
   - `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaBeginPacket.java`
   - `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaStatePacket.java`
   - `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaStatusPacket.java`
   - `src/main/java/com/tom/hqspeaker/network/HQFiniteMediaChunkPacket.java`
   - `src/main/java/com/tom/hqspeaker/network/HQSpeakerNetwork.java`
   - `src/main/java/com/tom/hqspeaker/media/MediaAssetStore.java`
   - `src/main/java/com/tom/hqspeaker/media/MediaMetadata.java`
   - `src/main/java/com/tom/hqspeaker/media/MediaSeekPoint.java`
5. Read `M1E-FINITE-STREAMING-DESIGN.md` and `ROADMAP.md` for the M1F contract.
6. Implement **M1F only**: demand-driven bounded range transport + off-thread server reads + bounded temporary encoded client RAM. Do not prematurely implement the whole M1G decoder or M1H listener lifecycle unless a necessary interface requires a small supporting change.
7. Add deterministic tests with the milestone.
8. Run both NeoForge CI targets and package verification.
9. Recheck the implementation against this contract before declaring M1F source-complete.

The target after M1F is not a client media cache. The target remains server-owned finite assets streamed on demand into bounded active-playback RAM.

---

## 22. Reading order for deeper details

If more detail is needed after this handoff, read:

1. `CURRENT-STATE.md`
2. `VERIFIED-FACTS.md`
3. `CC-T-COMPATIBILITY-CONTRACT.md`
4. `ARCHITECTURE.md`
5. `M1E-SERVER-AUTHORITY.md`
6. `M1E-FINITE-STREAMING-DESIGN.md`
7. `ROADMAP.md`
8. `KNOWN-ISSUES.md`
9. `TESTING.md`
10. historical milestone docs as needed

`CHAT-HANDOFF-2026-09-12.md` remains a longer dated deep-context handoff. This file is the canonical current continuation entry point.