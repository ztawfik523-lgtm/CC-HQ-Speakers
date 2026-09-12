# CC:HQ Speakers — complete new-chat handoff

Date: 2026-09-12

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1e-server-authoritative-finite`

This document is the continuation handoff for another chat. It intentionally includes both current implementation facts and the settled product/architecture decisions which led to the current roadmap.

**Before changing code in a new chat, re-read the current branch source and current CI.** This handoff describes the project state when written. If the branch later moves, exact current source wins.

---

# 1. How to work with the user

The user wants concrete implementation discussion, not abstract architecture language.

Do not say only things like:

- "decouple transport from playback";
- "establish a source of truth";
- "use robust buffering";
- "adopt a range-based architecture".

Instead name classes, fields, packets, states, and behavior. Example:

```text
When Lua calls audioSeek(180), HQFiniteMediaServer changes the canonical
server position immediately. The client discards obsolete encoded/PCM buffers,
requests a server-selected MP3 anchor before the current server position,
decodes/discards pre-roll, and begins audible output near the server's then-current
position. The server clock never waits for the client.
```

The user often asks to recheck/research/prove things. Treat plausible implementation ideas as hypotheses until exact source/tests/runtime evidence support them.

When there are meaningful tradeoffs, present the main choices and their concrete consequences rather than silently choosing. Minor implementation details can be handled directly.

Do not repeatedly ask questions whose answers are already settled below.

---

# 2. Evidence precedence

When sources disagree, use this order:

1. exact current source / exact current runtime evidence;
2. `docs/VERIFIED-FACTS.md` for facts already recorded from source/CI/runtime;
3. `docs/CURRENT-STATE.md`;
4. accepted architecture/design docs;
5. `docs/ROADMAP.md`;
6. milestone-specific historical docs for the code they describe;
7. older P0/prototype docs.

A green Gradle/GitHub Actions run proves source/tests/package structure. It does **not** prove audible Minecraft behavior. Runtime scripts which have not actually passed in Minecraft must stay labeled runtime-pending.

---

# 3. Exact target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Build dependency is CC:Tweaked 1.120.0 for Minecraft 1.21.1.

---

# 4. Important repository checkpoints

Historical/reference commits:

- inherited fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- M1E final code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`

Important CI:

- frozen M1D final run: `34635484316` — both NeoForge 21.1.247 and 21.1.248 green
- M1E code-bearing run: `34658958488` — both NeoForge 21.1.247 and 21.1.248 green
- later M1E docs head `2d56c089aa7c09ec19bb3bf1be4ebcb8aa0913f5` passed run `34659384866`

Documentation-only commits after those checkpoints may move the active branch head. Do not confuse a later docs head with the stable M1E code-bearing checkpoint.

---

# 5. Product identity — settled

The mod is a **programmable ComputerCraft speaker peripheral**, not a Java music player.

Lua decides whether audio is:

- music;
- an alarm;
- speech;
- a notification;
- ambience;
- a soundboard entry;
- a playlist;
- anything else.

Java exposes truthful technical capabilities only.

Do not add permanent concepts such as:

- music lane/channel;
- effects lane/channel;
- notification lane/channel;
- Java playlist/album database;
- automatic application-level priority rules.

---

# 6. Technical source categories

There are three semantic HQ source categories plus normal CC:T behavior.

## Standard CC:T speaker

Keep standard CC:T behavior compatible:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

The current composite delegates these methods to the real CC:T `SpeakerPeripheral`.

## HQ RAW/feed

`HQ speakPCM` is an open-ended producer feed.

It has bounded producer backpressure, but it must not claim:

- finite duration;
- arbitrary seek;
- finite EOF.

## Finite encoded media

Finite media has a known server asset/timeline and truthful:

- duration;
- position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

## Live network streams — later

Examples: live MP3 radio, HLS, TS.

Live streams are open-ended and must not fake finite duration/seek. Future live pause/resume means reconnect to the **current live point**, not resume old buffered history.

Do not force finite asset semantics onto live streams merely because both deliver bytes progressively.

---

# 7. Final finite format scope — settled

Core formats:

- **MP3 / MPEG Layer III**
- **common WAV**

Wanted but gated:

- **normal native FLAC**, only after its exact analyzer/decoder/seek/package/runtime path is proven

Explicitly not required:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- exotic/compressed/telephony WAV variants
- >2-channel finite input

Frozen M1D historically analyzes OGG/AIFF/AU. That historical fact does **not** force the replacement engine to keep them.

Do not re-open the previous STB-vs-JOrbis Vorbis design debate unless the user explicitly changes format scope. OGG was deliberately dropped from the final product target.

---

# 8. Audio channel/sample policy — settled

One physical Minecraft speaker is one **mono positional** source.

Input policy:

- mono input -> mono output
- stereo input -> downmix to mono
- >2 channels -> reject

Do not simulate surround output from one speaker block.

Common WAV target:

- unsigned 8-bit PCM
- signed 16-bit PCM
- signed 24-bit PCM
- signed 32-bit PCM
- 32-bit IEEE float

Reject unusual WAV encodings/bit widths instead of implementing every representation JavaSound can technically open.

Stereo downmix should use widened arithmetic before averaging so integer overflow cannot occur.

---

# 9. Client caching decision — settled

There is **no final client song cache**.

Do not build:

- persistent `.part` song library;
- completed client media library;
- LRU music cache;
- cache database;
- sparse range file;
- block-file cache;
- cross-restart partial-download resume.

The server owns the complete encoded asset.

The final client only keeps bounded temporary memory for active playback:

- encoded bytes currently needed;
- small codec pre-roll/context;
- bounded decoded mono PCM.

If the client later needs an old part of the song again, it asks the server again.

A 500 MiB song must not imply 500 MiB client disk or RAM.

The current M1E `.part/.media` client files are a **temporary bridge only**, not the architecture.

---

# 10. Finite streaming model — settled

A finite file remains finite even though its encoded bytes are streamed progressively.

Target flow:

```text
ComputerCraft file
    -> server MediaAsset
    -> server playback starts canonical clock immediately
    -> relevant client asks for encoded bytes near current playback need
    -> server reads bounded range off-thread
    -> bounded packets to client
    -> bounded encoded RAM
    -> progressive decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

Do **not** decode the entire song to PCM on the server and stream PCM over the network. Send encoded MP3/WAV/FLAC bytes; decode client-side.

Do **not** require full encoded download before audio begins in the final engine.

---

# 11. Server authority — settled and implemented in M1E

The server owns finite semantic truth:

- generation;
- playback state;
- duration;
- current position;
- pause/resume;
- seek;
- loop;
- volume;
- EOF;
- later sync-clock identity.

A successful finite play starts canonical time **immediately**.

No listener is required.

Example:

```text
play() at 0:00
no player nearby
server progresses 0:01, 0:02, ... 0:30
player becomes relevant at 0:30
client should join approximately 0:30
```

Client renderer readiness is not server `LOADING`.

Client renderer failure does not make the canonical song fail for everyone else.

---

# 12. M0.5 summary

Completed at `ad38412a2173f849a0fc8e867030da8a78965c9c`.

Important result: explicit composite/provider cleanup on speaker removal, Level unload, and server stop. It did not repair the obsolete finite architecture.

---

# 13. M1A summary — compatibility/output ownership

The normal CC:T speaker is exposed through `HQSpeakerCompositePeripheral` while preserving the actual CC:T `SpeakerPeripheral`.

Important behavior:

- standard `playNote`, `playSound`, `playAudio`, `stop` delegate to CC:T;
- notes remain independent;
- HQ continuous output has one owner such as RAW / legacy finite / staged-prepared finite / stream / none;
- a new incompatible HQ source replaces the old HQ source;
- repeated accepted `speakPCM` calls while RAW owns output continue one raw feed;
- while HQ continuous output is active, standard `playSound`/`playAudio` are prevented from overlapping it;
- `speakMaxSamples()` reports 131072;
- HQ RAW has its own `hqspeaker_audio_empty` producer pacing;
- RAW server admission is bounded by the inherited 16-packet queue plus sample-duration accounting;
- accepted RAW outstanding samples drain at 2400 samples/server tick;
- ownership-changing calls on one physical speaker are serialized.

Known M1A boundary:

- inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path and are later M1J/M1M work.

Minecraft M1A runtime acceptance remains pending unless the runtime script has subsequently been run successfully.

---

# 14. Exact useful CC:T 1.120.0 facts

Keep these when touching compatibility code:

- peripheral type is `speaker`;
- `playAudio` uses signed 8-bit samples at 48 kHz;
- maximum contiguous standard `playAudio` input is `128 * 1024` samples;
- CC:T has one pending DFPWM/audio buffer and native `speaker_audio_empty` is emitted when another may be accepted;
- native `stop()` sets a flag which is processed on a later server tick;
- notes are stored separately and are not cleared by native `SpeakerPeripheral.stop()`;
- exact source default for omitted `playNote` pitch differs from the documentation; preserve actual target source behavior through delegation rather than reimplementing it.

A historical runtime detail: after native `stop()`, a short delay such as one server tick/`sleep(0.05)` may be needed before immediately pushing another native `playAudio` buffer because the stop flag is processed on the next tick.

---

# 15. M1B — server media assets

Completed at `40091ee32f412c1208e9016fca288b8d4f902dfa`.

`MediaAssetStore` provides:

- UUID media identity independent of speakers;
- server disk-backed `.part` -> atomic `.media` import;
- exact-size bounded copying;
- per-asset and total quota enforcement;
- reservation before copy so concurrent imports cannot overcommit total quota;
- retain/release reference lifetime;
- final-reference deletion;
- seekable encoded reads;
- startup orphan pruning;
- root OS file lock;
- safe shutdown/import race behavior;
- retryable close cleanup.

Server asset storage remains important even though client caching was removed. The authoritative encoded finite asset lives on the server while referenced.

---

# 16. M1C — local ComputerCraft file import/config

Verified base: `33bcc6e04a2734500b7b15b84bee884562539216`.

Flow:

```text
ComputerCraft file
    -> temporary writable HQ staging mount
    -> immutable shared server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Important APIs/helpers:

- `audioPrepareStaged`
- `audioPlayPrepared`
- `audioReleasePrepared`
- Lua `prepareFile`
- Lua `preparedInfo`
- Lua `playPrepared`
- Lua `releasePrepared`
- Lua `playFile`

Prepared ownership is tied to ComputerCraft computer ID. Playback takes a separate reference, so releasing a preparation handle does not kill active playback. One server asset UUID can be played through another physical speaker.

Server config:

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

`0` means no HQ-specific quota for that limit.

These limits apply only to HQ Speaker server-side storage/staging. They do **not** modify ComputerCraft filesystem capacity.

Important overflow hardening: CC:T `WritableFileMount` internally adds `MountConstants.MINIMUM_FILE_SIZE` (500 bytes). The unlimited staging path clamps the supplied capacity to `Long.MAX_VALUE - 500` so adding the overhead cannot overflow negative.

---

# 17. M1D — frozen media analysis

Frozen source/test/CI head:

`4a2cd5de96228fc091226c7e72fb669b82be258c`

Final run:

`34635484316` — success on both target NeoForge versions.

M1D analyzes the exact immutable committed server asset rather than trusting filename extension or a mutable staging file.

Historical M1D formats:

- MP3
- OGG Vorbis
- WAV
- uncompressed AIFF/AIF
- AU/SND

Again: OGG/AIFF/AU are historical M1D facts, not final product commitments.

`MediaMetadata` records finite facts including:

- format;
- positive duration;
- sample rate;
- channels;
- bits per sample where meaningful;
- bounded encoded seek points.

M1D MP3 seek points are **real scanned MP3 frame byte offsets**.

M1D OGG seek points are actual Ogg page offsets, but OGG is no longer a target.

Seek metadata is bounded to at most 4096 points and self-thins for huge files.

M1D uses a bounded 64 KiB analysis window rather than decoding the track.

Prepared flow imports exact bytes to immutable `MediaAssetStore`, analyzes that committed copy, attaches metadata, and only then exposes the asset UUID to Lua. Failed analysis releases the unexposed asset reference.

MP3 duration remains encoded-frame duration and does not yet subtract gapless encoder delay/padding.

---

# 18. M1E — current completed source milestone

M1E final code-bearing head:

`d0e66ab9135359627086c13647d5241ad778643f`

M1E exact CI run:

`34658958488` — both NeoForge targets passed.

Minecraft runtime acceptance is still pending.

## M1E server state

`HQFiniteMediaServer` states are now:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

Removed:

- server `LOADING` based on client readiness;
- `successfulRenderers` canonical authority;
- canonical `observed`;
- 15-second no-renderer error.

Session construction sets duration, starts `FinitePlaybackClock` immediately, and enters PLAYING.

## Natural EOF

`FinitePlaybackClock.reachedEnd(now)` supports deterministic non-looping natural EOF.

When a non-looping server playback reaches duration:

```text
finish/clamp clock
-> state ENDED
-> close/cancel temporary transfer first
-> release playback asset ref
-> save terminal status
-> send/queue authoritative state
```

Looping playback wraps and never naturally ends.

`seek(duration)`:

- non-looping -> immediate ENDED;
- looping -> wraps to 0.

Status/control paths finalize elapsed EOF so a song cannot remain semantically PLAYING after known duration merely because the next tick has not happened yet.

## Protocol v4

Current protocol version: `4`.

M1E adds `HQFiniteMediaStatePacket`.

### Transitional BEGIN packet

Carries setup/immutable bridge information such as:

- source/media/generation;
- format;
- initial volume;
- speaker world/block coordinates;
- total encoded bytes;
- initial loop/pause flags.

### Authoritative STATE packet

Carries:

- source;
- media ID;
- generation;
- PLAYING/PAUSED/ENDED/ERROR;
- canonical position;
- duration;
- volume;
- looping;
- error detail.

Do not send server `System.nanoTime()` and compare it to client `System.nanoTime()`. Java nanoTime origins are JVM-local and unrelated between client/server processes.

## Client -> server status after M1E

`HQFiniteMediaStatusPacket.Transition` is only:

- `READY`
- `ERROR`

The old renderer-authority transitions STARTED/PAUSED/RESUMED/SEEKED/ENDED are gone.

READY means the temporary old complete-file bridge can construct its decoder and requests a fresh canonical STATE.

ERROR is local diagnostic telemetry. It does not make one client authoritative over the server or other clients.

## Temporary M1E bridge

The current client still creates:

- `hqspeaker-cache/*.part`
- then `.media`

and only constructs `FileFiniteAudioStream` after the whole encoded file has arrived.

This is explicitly temporary.

The semantic correction is:

```text
old:
full file arrived -> start at 0 -> client STARTED controls server

M1E:
full file arrived -> client READY
                  -> server sends fresh STATE
                  -> client seeks/starts at current canonical server position
```

If the server is paused, the client prepares at the paused position without starting audible playback. If server state is ENDED/ERROR, the stale local bridge is destroyed.

The client `FinitePlaybackClock` remains only a local renderer projection for reload/restart behavior.

## M1E runtime test

Script:

`scripts/m1e_server_authority_test.lua <small-mp3-or-wav>`

It is intended to verify:

- immediate server PLAYING;
- position advances without renderer readiness;
- pause freezes server position;
- resume advances again;
- non-looping exact-duration seek becomes ENDED;
- looping exact-duration seek wraps near 0 and remains active.

Do **not** call M1E Minecraft-runtime PASS until this script actually passes in-game.

---

# 19. What is still transitional after M1E

These current behaviors are known temporary architecture:

- recipients captured once at playback start;
- whole-file server push;
- up to two current transfer chunks per server tick;
- file reads from server `tick()`;
- current max chunk constant 256 KiB;
- client writes `.part/.media` on Minecraft client thread;
- client requires complete encoded file before creating current decoder;
- no dynamic listener can join after finite playback starts;
- current `FileFiniteAudioStream` is file-backed and not a final progressive source.

Do not polish these into the final design. M1F/M1G replace them.

---

# 20. M1F — next implementation milestone

M1F is **demand-driven finite encoded transport**.

The goal is to delete fixed-recipient whole-file push while not yet requiring the final MP3/WAV decoder.

Conceptual request:

```text
FiniteRangeRequest
    sourceId
    generation
    assetId
    offset
    length
```

Conceptual response:

```text
FiniteRangeData
    sourceId
    generation
    assetId
    offset
    bytes
```

The server is still the byte source. "Client-pulled" only means the client tells the server which bounded bytes it currently needs, providing pacing/cancellation/seek support.

## M1F validation on the server thread

Before scheduling a range read, validate:

1. active finite playback exists;
2. generation matches;
3. requested asset matches active playback;
4. player is connected;
5. player is in the correct dimension;
6. player is currently relevant/in allowed speaker range;
7. offset/length are valid within asset bounds;
8. request length is bounded;
9. per-player outstanding/rate limits permit it;
10. playback still owns the asset.

Then retain a safe read reference and perform file IO on a bounded worker, **not the server tick**.

Before sending completed bytes, re-check:

- generation;
- active playback;
- player connection;
- dimension/relevance.

If stale, discard the completed read rather than sending it.

## M1F resource limits

Protect actual expensive resources, not arbitrary ComputerCraft program counts.

Useful limits:

- maximum range request length;
- maximum outstanding requests/bytes per Minecraft player;
- per-player range byte rate if profiling shows it is useful;
- bounded server IO executor and queue;
- existing server asset/staging quotas;
- later bounded client encoded/PCM buffers.

Do **not** add a low static "finite sessions per ComputerCraft computer" limit without evidence. A server session is cheap; actual network/IO load is tied to players requesting bytes.

## Packet size

256 KiB is a valid starting cap but not sacred. Minecraft connection-level compression may waste CPU trying to recompress already compressed MP3/FLAC. Benchmark 64/128/256 KiB later under real packet compression before final tuning.

No extra TCP-style ACK protocol is needed over Minecraft's reliable connection. Bounded client demand naturally supplies pacing.

## Generation behavior

Generation changes when playback is replaced/new.

Do **not** bump generation on:

- pause;
- resume;
- seek;
- volume;
- loop toggle.

The asset bytes did not become invalid. A seek merely makes previously requested ranges irrelevant to current demand; stale/off-window returned data may be discarded.

## Seek-anchor rule

Do not ship the whole server seek table to clients.

For initial play, late join, or seek, the server may provide a codec-specific anchor:

```text
anchorTimeSeconds
anchorByteOffset
codec/layout facts needed by decoder
```

The server chooses an anchor at/before the desired canonical position. The client requests forward from that anchor and performs codec pre-roll/discard.

M1F tests should cover:

- bounds;
- wrong generation;
- wrong asset;
- relevance/dimension;
- cancellation;
- in-flight asset lifetime;
- async completion after replacement;
- bounded outstanding work;
- stale work discard;
- no large file reads on server tick.

---

# 21. M1G — progressive MP3 + common WAV

M1G removes the complete-file decoder requirement and current client disk bridge.

Target client pipeline:

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> Minecraft/OpenAL positional source
```

The Minecraft sound thread must consume **already-ready PCM only**. It must never wait on network, disk, or decoder work.

## MP3 implementation contract

The exact project currently ships JLayer `1.0.1.4` with MP3SPI/Tritonus.

Critical rule:

```text
next encoded bytes have not arrived yet
!=
real file EOF
```

A progressive input abstraction must wait/refill on a decoder worker when requested encoded bytes are temporarily absent. Returning `-1` to JLayer simply because the network buffer is temporarily empty can cause incomplete/fake-EOF behavior.

### Previous isolated JLayer research from this project conversation

This was not yet turned into repository unit tests and should be re-verified if implementation details change, but it was tested against the exact shipped JLayer binary:

- a real MP3 decoded normally produced 194 frames / 893,952 PCM bytes;
- a custom stream starting with only 4 KiB, whose decoder thread waited while additional 4 KiB pieces were supplied, produced the same 194 frames, same PCM byte count, and the same PCM SHA-256;
- exposing temporary starvation as real `-1` EOF produced only 193 frames / 889,344 bytes and different output.

Conclusion: JLayer can work progressively when its decoder input waits for missing bytes rather than lying about EOF.

### MP3 seek/rejoin pre-roll

MPEG Layer III has a bit reservoir. Starting a new decoder exactly at the target frame may produce incorrect initial frames because frame data can refer to earlier compressed main-data.

Previous isolated testing showed restarting exactly at a valid target frame caused initial mismatches, while restarting earlier and silently decoding forward restored matching target output.

Therefore:

```text
server target time
-> choose earlier known MP3 frame seek point
-> fetch forward
-> construct decoder
-> decode/discard pre-roll
-> expose audible PCM around current canonical time
```

Do not assume one preceding frame is always sufficient. Use conservative earlier seek metadata first; optimize after measured tests.

M1D already records real MP3 frame offsets, which are useful anchors.

## WAV implementation contract

The current historical M1D WAV acceptance is broader than the final converter should support.

Final common WAV contract:

- 1 or 2 channels;
- unsigned 8-bit PCM;
- signed 16-bit PCM;
- signed 24-bit PCM;
- signed 32-bit PCM;
- 32-bit IEEE float.

Server metadata should be extended with internal PCM layout such as:

- audio data offset;
- audio data length;
- encoding/sample representation;
- bits per sample;
- sample rate;
- channel count;
- encoded frame size/block alignment.

For supported uncompressed WAV, time -> encoded byte is arithmetic. The client requests only the needed PCM frames, converts them, and downmixes stereo to mono.

Do not preserve unusual JavaSound-only WAV formats merely because M1D historically accepted them.

## M1G format cleanup

Once MP3/common-WAV progressive playback replaces the historical prepared/local decoder path:

- active prepared/local advertisement becomes MP3 + supported common WAV;
- OGG/AIFF/AU may be removed from active product advertisement and APIs as appropriate;
- frozen M1D remains historical evidence.

---

# 22. M1H — dynamic listener lifecycle/recovery

After the streamed single-speaker decoder works, make relevance dynamic.

Expected behavior:

```text
player enters range at server time 2:00
-> receive current setup/state/seek anchor
-> request encoded bytes near current position
-> prebuffer/pre-roll
-> hear current timeline
```

Leaving range:

- stop/park local renderer;
- stop requesting ranges;
- discard active temporary buffers as appropriate;
- do not pause server playback.

Returning while still active:

- rejoin current server time.

Returning after stop/end:

- stay silent.

Also harden:

- dimension change;
- chunk/speaker removal;
- disconnect;
- F3+T/resource reload;
- stale generation responses;
- stale renderer restarts;
- VS2 position updates.

Underrun behavior:

- server keeps moving;
- client may become locally silent;
- refill/re-anchor;
- rejoin current server position.

Do not add ping/2 correction before measuring real drift. Periodic state correction may be useful later, but raw `System.nanoTime()` values cannot be compared across JVMs.

---

# 23. M1I — native FLAC, gated

FLAC is wanted but **must not block MP3/WAV completion**.

Only advertise normal native `.flac` after proving:

- byte-based FLAC identification;
- STREAMINFO parsing;
- duration/total samples;
- mono/stereo validation;
- bounded progressive decode from server ranges;
- random seek/rejoin strategy;
- malformed-input/checksum behavior;
- cancellation;
- bounded RAM;
- stereo -> mono downmix;
- packaged dependency behavior on NeoForge 21.1.247 and 21.1.248;
- actual Minecraft runtime playback.

Do not add Ogg-FLAC.

If a clean FLAC path becomes disproportionately difficult, leave FLAC unadvertised. MP3/WAV M1 remains valid.

---

# 24. M1J/M1K — multispeaker

## M1J functional behavior

One server asset may back multiple physical speakers.

Synchronized playbacks later reference one shared server sync-clock ID.

Do not use an expected-global-member/expected-tap barrier.

Each physical speaker keeps its own positional mono renderer so direction, attenuation, wall/occlusion, VS2 movement, and future SPR behavior remain physically correct.

A speaker may leave the shared clock if Lua independently pauses, seeks, stops, or replaces it.

Inherited `*All` / `*At` helpers need migration so they no longer bypass modern ownership/state.

## M1K optimization

Only after M1J is behaviorally correct:

- coalesce duplicate active range demand for identical asset/timeline where useful;
- share decode/PCM producer for identical active timelines where safe;
- keep independent physical renderer buffers/sources;
- lagging renderer may discard stale PCM and rejoin current timeline.

Do not reintroduce persistent client caching as an optimization.

---

# 25. Remaining M1 roadmap

- **M1L:** migrate/remove legacy finite byte APIs/old finite decoder; useful `speakMp3(bytes)`/`speakWav(bytes)` may become compatibility frontends to new engine; old OGG-specific APIs may be deprecated/removed.
- **M1M:** HQ RAW finalization and legacy multispeaker RAW cleanup.
- **M1N:** Minecraft/OpenAL cleanup: correct speaker sound category, one gain stage, F3+T recovery, no stale channels, attenuation/VS2 movement.
- **M1O:** lifecycle/performance stress: bounded executors, seek spam, cancellation storms, restart/unload cleanup, packet-size profiling, memory/network/tick checks.
- **M1P:** final dual-version CI/package verification.
- **M1Q:** consolidated Minecraft acceptance across standard CC:T, RAW, MP3, common WAV, optional FLAC if passed, large files, progressive start, controls, dynamic range, multispeaker, bounded resource usage.

Tests are not postponed to M1O/M1Q. Every milestone must add deterministic tests for its own contract.

---

# 26. After M1

## M2 — Sound Physics Remastered

Only after positional renderer lifecycle is stable.

Important constraint: shared decode/PCM work must never collapse several physical speakers into one OpenAL source. SPR needs real physical sources for independent occlusion/reverb.

Existing frozen SPR research lives under `docs/research/SPR-INTEGRATION-BASELINE.md` and related prior work.

## M3 — live/open-ended streams

Rebuild/stabilize live MP3/HLS/TS later.

Known issues include:

- stream gain currently applied twice;
- HLS progression can misuse persistent segment index/media sequence;
- TS path can be whole-list rather than truly incremental;
- unsupported decode can return compressed bytes as if PCM;
- live server state is intent rather than trustworthy renderer/network state;
- expected-tap shared-stream logic can deadlock/leak.

Live pause/resume target: reconnect to current live point.

## M4 — release cleanup

- final docs/API surface;
- remove dead/prototype classes;
- remove obsolete OGG/AIFF/AU finite surfaces after new engine fully replaces them;
- decide whether the separate `hqspeaker:hq_speaker` block stays;
- resolve `LICENSE` MPL-2.0 vs `neoforge.mods.toml` LGPL-3.0 mismatch before public release;
- do not silently relicense.

---

# 27. Current known issues worth remembering

Important active items from `KNOWN-ISSUES.md`:

- range leave can still leave stale client renderer state until dynamic listener work;
- M1E client still requires complete local file before current decoder starts;
- M1E server/client transfer file IO still happens on game threads;
- no final range-request protocol exists yet;
- final MP3 path must distinguish temporary starvation from EOF;
- MP3 seek needs bit-reservoir pre-roll;
- final WAV scope must be narrower than historical JavaSound-parity M1D;
- FLAC is desired but unproven;
- old legacy finite decoder executor is unbounded;
- legacy finite decode can retain complete PCM;
- inherited 8 MiB finite byte APIs remain;
- legacy multispeaker expected-member barrier remains;
- live stream defects remain;
- sound-category/gain cleanup remains;
- license mismatch remains.

---

# 28. Runtime/evidence status

Existing focused runtime scripts include:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua`
- `scripts/m1c_local_import_test.lua`
- `scripts/m1d_media_analysis_test.lua`
- `scripts/m1e_server_authority_test.lua`

Do not claim runtime PASS for scripts which were not actually executed successfully.

Important distinction:

- frozen M1D script contains old renderer-`observed` expectations and describes M1D/prototype semantics;
- M1E script is the active server-authority contract.

Earlier runtime evidence from M0 proved broad client/mod load and some MP3/native/HQ audio behavior, but skipped/inconclusive items should not be counted as pass.

---

# 29. Server storage vs client cache — do not confuse these

The user rejected **client song caching**.

The user did **not** reject server-side authoritative media storage.

These are different:

```text
server MediaAssetStore
= required authoritative prepared encoded files
= quota/config controlled
= reusable across speakers

client persistent cache
= rejected
= do not build
```

The server bearing responsibility for sending finite encoded bytes is intentional.

---

# 30. Direct-staged historical main-thread concern

The old direct `audioPlayStaged` surface can analyze a staged file synchronously because it predates the cleaner prepared-file helper path.

The bundled helper prepares files first and is the preferred large-file path.

Do not silently redesign this historical surface during unrelated transport work. If/when it is addressed, reasonable options include deprecating/removing the prototype direct surface or explicitly handing analysis to worker logic.

---

# 31. Generation, seek, and state invariants

Keep these invariants while implementing M1F+:

- new/replacement playback -> new generation;
- pause/resume -> same generation;
- seek -> same generation;
- volume -> same generation;
- loop toggle -> same generation;
- underlying asset ID remains same through those controls;
- stale generation work never starts/restarts audio;
- current server state is canonical;
- client local clocks are projections only;
- client error is diagnostic unless the server itself loses/invalidates the asset/playback;
- natural canonical EOF is based on server-known duration, not renderer EOF.

---

# 32. What not to build/revisit right now

Do not spend M1F/M1G time on:

- persistent client cache/LRU;
- OGG/Vorbis decoder choice;
- AIFF/AU;
- Ogg-FLAC;
- surround/multichannel finite rendering;
- Java playlist system;
- SPR;
- live HLS/TS;
- global asset content-hash deduplication;
- fancy adaptive congestion control;
- persistent cache database;
- ping/2 clock prediction;
- expected global sync group membership.

The next task is much narrower.

---

# 33. Exact next implementation task

**M1F: bounded demand-driven finite transport.**

Before coding:

1. fetch current branch head and verify it still contains the M1E server-authority code;
2. re-read:
   - `HQFiniteMediaServer`
   - `HQFiniteMediaClient`
   - `HQFiniteMediaBeginPacket`
   - `HQFiniteMediaChunkPacket`
   - `HQFiniteMediaEndPacket`
   - `HQFiniteMediaControlPacket`
   - `HQFiniteMediaStatePacket`
   - `HQFiniteMediaStatusPacket`
   - `HQSpeakerNetwork`
   - `MediaAssetStore`
   - `MediaMetadata` / `MediaSeekPoint`
3. preserve M1E server semantic behavior while replacing only the transfer layer;
4. add deterministic M1F tests with the implementation;
5. build both NeoForge versions and verify package contents;
6. update CURRENT-STATE / VERIFIED-FACTS / KNOWN-ISSUES / ROADMAP / milestone docs with exact evidence;
7. do not call runtime behavior passed until an actual Minecraft runtime contract is run.

The conceptual end-state after M1F should be:

```text
server playback already running
client needs encoded range
-> small request packet
server validates on server thread
-> bounded async asset read
server rechecks generation/relevance
-> bounded response packet
client stores only temporary active encoded bytes in RAM
```

M1F does not need to finish MP3/WAV progressive decoding; that is M1G. But do not design M1F in a way which requires a persistent client file, because M1G must be able to feed decoder workers directly from bounded active range buffers.

---

# 34. Final settled target in one picture

```text
                         normal ComputerCraft speaker
                                  |
          +-----------------------+-----------------------+
          |                       |                       |
      standard CC:T             HQ RAW               finite media
   real SpeakerPeripheral      bounded feed           MP3 / WAV
          |                       |                  (+ FLAC if proven)
          |                       |                       |
      native semantics        no fake seek         server MediaAsset
                                                      |
                                                server clock/state
                                                      |
                                              client range demand
                                                      |
                                            bounded encoded RAM
                                                      |
                                            decoder/converter worker
                                                      |
                                            bounded mono PCM queue
                                                      |
                                         one positional sound source
```

Finite timeline and encoded transport are independent:

- "finite" means known duration/timeline/end;
- it does **not** mean "download the complete file before playing".

That distinction is the central direction of the current redesign.
