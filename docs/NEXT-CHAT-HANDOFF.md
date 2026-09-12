# CC:HQ Speakers — complete next-chat handoff

Date: 2026-09-12

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Active branch: `codex/m1e-server-authoritative-finite`

Current documentation branch head when this handoff was rewritten: `703860451d16272db8c7cba01097b33fd0bc2c15`

Exact M1E code-bearing implementation checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`

Exact M1E CI run: `34658958488`

Frozen M1D checkpoint: `4a2cd5de96228fc091226c7e72fb669b82be258c`

Frozen M1D final CI run: `34635484316`

This file is meant to be enough for a fresh chat to continue the project. Before changing code, re-read the current branch and current CI because the branch may have moved after this document was written.

---

# 1. How to explain this project to the user

The user does **not** want vague architecture language, but they also do **not** want explanations that assume they already understand Java classes, network packets, codec internals, or Minecraft client/server internals.

Use this order when explaining something:

1. Explain what happens in normal Minecraft terms.
2. Give a concrete example with a song/player/speaker.
3. Explain why that behavior is wanted.
4. Only then give the code/class/packet details in a separate implementation note.

Do **not** lead with terms such as “server-authoritative state”, “transport layer”, “canonical timeline”, “renderer lifecycle”, or “seek anchor” without first explaining what they mean in ordinary words.

Do **not** lead with code like “remove `successfulRenderers` and call `clock.start(now)`” either. That is useful to the programmer, but not useful as the explanation to the user.

## Example of the preferred style

Bad because it is too abstract:

> Make finite playback server-authoritative and decouple renderer readiness from the canonical timeline.

Bad because it assumes too much code knowledge:

> Remove `successfulRenderers` and `observed` from `HQFiniteMediaServer.Session`, then call `clock.start(now)` in `playPrepared()`.

Good:

> When a ComputerCraft program starts a 3-minute song, the song should start counting immediately on the Minecraft server. If nobody is standing near the speaker for 30 seconds, the song should still be 30 seconds in when a player walks over. That player's game should then start hearing the song around 0:30 instead of restarting it from the beginning. A slow or broken client should never be able to pause or rewind the song for everybody else.
>
> Implementation note for the coding work: M1E already changed `HQFiniteMediaServer` so its `FinitePlaybackClock` starts immediately and client READY/ERROR messages no longer control the server clock. `HQFiniteMediaStatePacket` carries the current server position/state to the client.

That is the communication style to use throughout this project.

The user frequently asks to recheck, prove, and disprove assumptions. Treat plausible ideas as hypotheses until source/tests/CI/runtime evidence supports them.

When there are multiple meaningful choices, explain the visible difference to the user first. Example: “Option A may briefly go silent during a network hiccup but is simpler; Option B keeps playing silence to avoid a click but needs more audio-buffer logic.” Then give implementation differences if useful.

---

# 2. Tiny glossary for a non-technical reader

**Server** — the Minecraft server. It owns the real song file and decides where finite playback currently is.

**Client** — one player's Minecraft game. It receives audio data from the server and actually produces sound through that player's speakers/headphones.

**Media asset** — the server's stored copy of an imported MP3/WAV/etc. It has an ID so several speakers can refer to the same encoded file without making unnecessary duplicate server copies.

**Encoded audio** — the compressed/original file bytes, such as MP3 bytes or FLAC bytes. These are much smaller than fully decoded raw audio.

**PCM** — raw decoded audio samples. Minecraft/OpenAL ultimately needs decoded samples to play sound.

**Decoder** — code on the client that turns MP3/WAV/FLAC data into raw PCM samples.

**Buffer** — a small temporary amount of data kept in memory so audio can continue smoothly while the next network piece is arriving.

**Seek** — jump to a different time in a finite song, for example from 0:20 to 2:00.

**EOF / end of file** — the natural end of a finite song.

**Generation** — a number used to distinguish the current playback from an older playback on the same speaker. If song A is replaced by song B, late packets from song A must be ignored.

**Renderer** — the client's local Minecraft/OpenAL sound source which actually produces positional audio at the speaker block.

**Late join** — a player comes into hearing range after a song has already been playing for some time.

---

# 3. Evidence rules

When facts conflict, trust them in this order:

1. successful Minecraft runtime evidence on the exact target stack;
2. exact current source code;
3. exact current CI/build/package evidence;
4. `docs/VERIFIED-FACTS.md`;
5. `docs/CURRENT-STATE.md`;
6. compatibility/design docs;
7. `docs/ROADMAP.md`;
8. milestone historical docs;
9. old prototype/P0 material.

A green Gradle/GitHub Actions build proves compilation/tests/package checks. It does **not** prove that the audio was actually audible or behaved correctly in Minecraft.

Never call a runtime script PASS unless it was actually executed successfully in Minecraft on the target stack.

---

# 4. Exact target stack and important checkpoints

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Important commits:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- completed M1E code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`
- docs were later updated on the same active branch; docs-only heads are not substitutes for the code-bearing M1E proof commit.

Important CI:

- M1D final run `34635484316`: success on NeoForge 21.1.247 and 21.1.248
- M1E code-bearing run `34658958488`: success on NeoForge 21.1.247 and 21.1.248

M1E Minecraft runtime acceptance is still pending.

---

# 5. What the mod is trying to be

CC:HQ Speakers upgrades the normal CC:Tweaked speaker. It is a **programmable speaker peripheral**, not a Java music application.

Lua decides whether audio is used as music, alarms, speech, ambience, notifications, playlists, soundboards, or anything else.

Java should only provide technical capabilities such as playing finite files, feeding raw PCM, seeking finite files, pausing, changing volume, and later playing live network streams.

Do not build permanent Java concepts such as “music lane”, “notification lane”, built-in playlists, application priorities, or a client music library.

The normal `computercraft:speaker` remains the main product surface.

---

# 6. The three important kinds of audio

## Normal CC:T speaker audio

Keep native CC:T behavior for:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

These delegate to the real CC:T `SpeakerPeripheral`.

## HQ RAW/feed audio

`speakPCM` is an open-ended stream of raw samples produced by Lua.

There is no truthful total duration, seek position, or natural finite EOF because Lua might continue feeding more samples forever.

RAW has bounded backpressure and separate `hqspeaker_audio_empty` pacing.

## Finite files

Examples: MP3, normal WAV, and possibly normal native FLAC later.

A finite file has a known duration and a real beginning/end. Therefore it can truthfully support:

- duration
- current position
- pause/resume
- seek
- looping
- volume
- natural end

The Minecraft server owns those facts.

## Live network audio — later

Examples: Internet radio, HLS, TS.

Live audio is not a finite song. It should not pretend to have an ordinary duration/seek history. Future pause/resume should reconnect to the current live point rather than preserve an old buffered timeline.

---

# 7. Final finite-file scope — settled

Core formats:

- MP3 / MPEG Layer III
- common WAV

Wanted later, but separately gated:

- normal native `.flac`

Not required final formats:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- unusual/compressed/telephony WAV variants
- surround or >2-channel finite input

For WAV, target the common/easy representations:

- unsigned 8-bit PCM
- signed 16-bit PCM
- signed 24-bit PCM
- signed 32-bit PCM
- 32-bit IEEE float

Input channel rule:

- mono stays mono
- stereo is converted/downmixed to mono
- more than two channels is rejected

Why mono? One Minecraft speaker block is one physical positional sound source. We want sound to come from that block in the world. A stereo file can still be accepted; its left/right channels are combined into one mono positional signal.

Frozen M1D historically knows OGG/AIFF/AU. That remains historical evidence, not a promise that the final engine must keep those formats.

---

# 8. No client song cache — settled

The final engine should **not save downloaded songs on the player's disk**.

Do not build:

- a persistent `.part` library
- a completed client music library
- an LRU song cache
- a sparse-file cache
- block-file cache storage
- cross-restart download resume
- a cache database

The client only keeps a small temporary amount of active playback data in RAM.

Example:

A player hears a 500 MB MP3. Their game does not save a 500 MB copy. It asks the server for only the pieces needed around the current playback position, decodes them, plays them, and eventually discards old pieces.

If the same old bytes are needed again later, the client can simply ask the server again.

The `hqspeaker-cache/*.part/.media` files which still exist in M1E are **temporary old bridge behavior**. M1F/M1G are specifically meant to delete that architecture.

---

# 9. The final finite streaming behavior we want

This is the most important user-visible design.

Imagine a 10-minute MP3 on the server.

When Lua starts it:

```text
0:00  server starts the song timer immediately
0:01  server says the song is at 1 second
0:30  even if nobody is nearby, server says 30 seconds
```

If a player walks near the speaker at 0:30, their Minecraft client should ask for encoded MP3 data around the current song position and begin hearing approximately 0:30. It should **not** restart the song from 0:00.

The client receives encoded file pieces, keeps a small amount in memory, decodes them into mono PCM, and feeds Minecraft/OpenAL.

Conceptually:

```text
server's MP3/WAV file
        ↓
client asks for the piece it needs
        ↓
small encoded RAM buffer
        ↓
decoder worker
        ↓
small mono PCM buffer
        ↓
Minecraft positional speaker sound
```

The server should not decode the whole song and send huge raw PCM traffic.

The client should not download the whole song before hearing it.

## Seek example

Lua seeks from 0:45 to 3:00.

The server immediately says the real song position is 3:00.

The client throws away now-useless buffered data from 0:45, asks the server for encoded data needed around 3:00, rebuilds the decoder state, and starts hearing the song around whatever the server's current time is by the time buffering finishes.

If the refill takes 0.4 seconds, it may audibly rejoin around 3:00.4. The server never waits for that one client.

## Network hiccup example

The server is at 1:42. A client's next bytes arrive late.

That client may briefly become silent while it refills. The server continues to 1:43, 1:44, etc. When the client has enough data again, it rejoins the current server position rather than making everyone wait at 1:42.

## Pause example

If Lua pauses the song at 2:10, the server freezes the real position at 2:10. Clients should pause there. A player who comes into range while the song is paused should prepare the correct 2:10 position and remain silent until resume.

## Loop example

If looping is on and the song reaches its end, the server wraps the position back to the beginning. Clients follow that server position.

---

# 10. Important CC:T 1.120.0 facts

The normal peripheral type remains `speaker`.

Standard `playAudio` uses signed 8-bit samples at 48 kHz and accepts at most `128 * 1024` samples in one contiguous call.

Native CC:T speaker audio has its own one-buffer/backpressure behavior and native `speaker_audio_empty` event.

Notes are independent from sound/audio state.

Native `SpeakerPeripheral.stop()` uses a stop flag which is handled on a later server tick. Historical runtime tests found that sending a new native `playAudio` immediately after `stop()` can race this; `sleep(0.05)` avoids it in test scripts.

Preserve actual CC:T source behavior rather than reimplementing documentation assumptions.

---

# 11. Completed foundation before M1E

## M0.5 — cleanup/redesign preparation

Completed at `ad38412a2173f849a0fc8e867030da8a78965c9c`.

It added important cleanup on speaker removal, level unload, and server stop, and deliberately did not spend time repairing finite prototype ideas which were already scheduled for replacement.

## M1A — normal speaker compatibility and HQ output ownership

Important current behavior:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T
- native notes remain independent
- one incompatible HQ continuous source replaces the previous HQ continuous source
- repeated accepted `speakPCM` continues the same RAW feed
- `speakMaxSamples()` reports 131072
- RAW uses separate `hqspeaker_audio_empty`
- RAW admission is bounded by the inherited 16-packet queue plus 135872 outstanding samples
- RAW outstanding duration drains by 2400 samples per server tick
- output/source replacement calls on one physical speaker are serialized

M1A Minecraft runtime acceptance remains pending unless later exact evidence says otherwise.

Known boundary: inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path. That is later multispeaker work.

## M1B — reusable server media assets

`MediaAssetStore` gives the server a safe place to keep imported encoded files.

It provides:

- UUID identity independent of a physical speaker
- disk-backed `.part` -> atomic `.media` import
- bounded 64 KiB copying rather than whole-file RAM copies
- per-asset and total quotas
- reservation before copy
- retain/release references
- deletion after the final reference disappears
- seekable encoded reads
- startup orphan cleanup
- root file locking
- shutdown/import race handling
- retryable cleanup

This storage is on the **server** and remains needed. Removing client caching does not mean removing server assets.

## M1C — import ComputerCraft files into server assets

Flow:

```text
ComputerCraft file
    ↓
temporary HQ staging mount
    ↓
immutable server MediaAsset
    ↓
prepared reference
    ↓
separate playback reference when played
```

Lua helpers/capabilities include:

- `prepareFile`
- `preparedInfo`
- `playPrepared`
- `releasePrepared`
- `playFile`

Releasing the prepared handle does not stop active playback because playback holds its own reference.

Server storage defaults:

```toml
[mediaStorage]
maxAssetMiB = 512
maxTotalMiB = 2048
```

`0` removes the corresponding HQ-specific quota.

These settings do **not** change ComputerCraft's own filesystem size.

Important overflow fix already done: CC:T writable mounts internally add 500 bytes of accounting overhead, so effectively unlimited staging clamps to `Long.MAX_VALUE - 500` rather than overflowing.

## M1D — frozen server-side media analysis

Frozen head: `4a2cd5de96228fc091226c7e72fb669b82be258c`

Final CI run: `34635484316`, green on both target NeoForge versions.

M1D identifies media from the actual encoded bytes rather than trusting the filename extension.

It analyzes the committed immutable server asset, not a still-writable staging file.

Historical analyzed formats:

- MP3
- OGG Vorbis
- WAV
- uncompressed AIFF/AIF
- AU/SND

Useful facts retained from M1D:

- server already knows finite duration before playback
- MP3 seek points are real scanned MP3 frame byte offsets
- metadata includes format, duration, sample rate, channels, bits/sample when meaningful, size
- seek metadata is bounded to at most 4096 points and self-thins for long files
- analysis uses a bounded 64 KiB window rather than whole-song PCM decode
- failed analysis releases the unexposed asset
- immutable committed-byte analysis prevents staging-file TOCTOU

Current MP3 duration is based on encoded frames and does not subtract encoder delay/padding from gapless metadata.

M1D Minecraft runtime acceptance remains pending unless later exact evidence records a pass.

---

# 12. M1E — completed source/test/CI behavior

Exact M1E code-bearing head: `d0e66ab9135359627086c13647d5241ad778643f`

Exact CI run: `34658958488`, successful on NeoForge 21.1.247 and 21.1.248 with tests/package verification.

Minecraft runtime acceptance is still pending.

## What M1E means in normal gameplay

Before M1E, the server behaved too much like “the song has not really started until a client successfully starts playing it.” That caused bad behavior when nobody was nearby or a client was slow.

After M1E:

- the song starts on the server immediately
- the song keeps progressing even with zero listeners
- one player's client cannot rewind/pause/end the song for everyone
- the server decides when a finite song naturally ends
- if the client takes time to finish the temporary old download, it asks the server where the song is **now** before starting locally

Example:

```text
Lua starts song at 0:00
client needs 6 seconds to finish old temporary download
server song reaches 0:06
client becomes ready
client receives current server state
client starts around 0:06, not 0:00
```

## M1E implementation notes for the next coding chat

`HQFiniteMediaServer` now uses canonical states:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no canonical server `LOADING` state based on client buffering.

Removed canonical renderer authority includes:

- `successfulRenderers`
- canonical `observed`
- 15-second no-renderer failure
- client STARTED/PAUSED/RESUMED/SEEKED/ENDED control over server state

Protocol version is now `4`.

`HQFiniteMediaStatePacket` is the server -> client packet containing current mutable truth:

- source/media/generation
- PLAYING/PAUSED/ENDED/ERROR
- current server position
- duration
- volume
- loop state
- optional server error detail

`HQFiniteMediaBeginPacket` still exists for temporary old setup/transfer data.

Client -> server finite status is only:

- `READY` — temporary complete-file decoder is ready and asks for fresh server state
- `ERROR` — diagnostic only

Client renderer failures do not end the server song.

Natural non-looping EOF comes from server duration/clock.

Non-looping `seek(duration)` immediately ends.

Looping `seek(duration)` wraps to 0.

When canonical EOF happens, M1E closes the temporary transfer channel before releasing the playback asset reference. This prevents a short song from deleting the server file while the old transfer is still reading it.

The client still has a local `FinitePlaybackClock`, but it is only local renderer projection/restart state. It is not the real song authority.

Focused runtime script:

`scripts/m1e_server_authority_test.lua <small-mp3-or-wav>`

It checks immediate server PLAYING, position advancement without renderer readiness, pause freeze, resume progression, non-looping exact-duration END, and looping exact-duration wrap.

That script has not yet been recorded as passing in Minecraft. Do not say M1E runtime passed.

---

# 13. What is still old after M1E

M1E deliberately did **not** replace the transfer/decoder yet.

Current transitional behavior still includes:

- players who receive the finite file are chosen once when playback starts
- the server blindly pushes the whole encoded file
- server file reads happen during the server tick
- client file writes happen on the Minecraft client thread
- client writes `.part/.media` files into `hqspeaker-cache`
- client decoder waits for the entire file before opening it
- a player who walks into range after playback starts cannot yet dynamically join

These are not bugs in the M1E scope; they are exactly the things M1F/M1G/M1H replace.

---

# 14. Decoder/streaming research already established

## MP3 progressive decoding

The project ships JLayer `1.0.1.4`.

Testing against that exact JLayer showed that progressive MP3 decoding works correctly if the decoder input **waits** when the next network bytes have not arrived yet.

Important rule:

```text
network bytes not here yet ≠ end of song
```

If the input falsely returns EOF during a temporary network gap, JLayer can treat it like a real truncated/end condition and decoded output differs.

Therefore the decoder worker may wait for more encoded bytes, but the Minecraft sound thread must never block waiting for network data.

## MP3 seek/rejoin

Layer III uses a bit reservoir, meaning a frame can depend on compressed data carried by earlier frames.

Testing showed that opening exactly at the target frame can give wrong initial decoded frames. Starting earlier and silently decoding/discarding some frames restores correct output.

Therefore a seek/rejoin should begin from an earlier server-known MP3 frame offset, decode forward silently, then make audio audible near the requested/current server time.

## WAV

The new progressive path should implement only the common formats we actually want instead of preserving every JavaSound edge case.

Direct time-to-byte mapping is straightforward once server metadata includes the WAV audio-data offset/layout.

## FLAC

FLAC is wanted, but is **not yet proven or implemented** in this project.

Do not advertise it until there is an exact analyzer, progressive decoder, seek/rejoin path, malformed-file behavior, packaging proof, and Minecraft runtime pass.

## OGG

OGG/Vorbis is no longer a final product requirement, so do not spend M1F/M1G effort solving Vorbis unless the user explicitly changes scope later.

---

# 15. Next milestone: M1F demand-driven finite transport

This is the next implementation milestone.

## What M1F should mean to the user

Right now the server starts sending the entire song to the players chosen at play start, whether they need all of it or not.

M1F changes that to a request/response flow.

Example:

```text
client: I need encoded bytes around here
server: validates request and sends that bounded piece
client: consumes it
client: asks for the next piece when needed
```

If playback is stopped, replaced, or the player leaves range, the client should stop asking for more data and old requests should become useless instead of continuing to dump the whole file.

M1F does **not** need to finish the final MP3/WAV audio decoder. It builds the safe transport which M1G will consume.

## M1F implementation notes

Add a client -> server range request containing at least:

- speaker/source identity
- playback generation
- asset ID
- encoded byte offset
- bounded requested length

Add server -> client range data containing at least:

- source
- generation
- asset ID
- offset
- returned bytes

The server must check before accepting a request:

- playback still exists
- generation is current
- requested asset is the active asset
- player is in the correct dimension and relevant range
- offset/length are inside the asset
- request is within configured/hard bounds
- player is not exceeding allowed outstanding/rate limits

Server file reads must move off the Minecraft server tick onto a bounded IO worker.

While async IO is in flight, hold a safe asset reference.

After the read finishes but before sending the bytes, check again that the playback/generation/player relevance is still valid. If it became stale, discard the work.

The final M1F client side should keep only bounded temporary encoded RAM. Do not create final `.part/.media` song files.

256 KiB is a reasonable starting maximum response packet size because the old packet already uses it, but it is not sacred. M1O can benchmark 64/128/256 KiB under Minecraft packet compression.

For seek/late join, the server can choose a codec-appropriate starting point at or before the target time. The client does not need the entire server seek-index table.

M1F tests should cover bounds, stale generation, player relevance, cancellation, safe asset lifetime during async IO, replacement while IO is running, bounded outstanding work, and proof that asset reads no longer happen on the game tick.

---

# 16. M1G: progressive MP3 + common WAV

## What it should mean to the user

This is where a large file finally becomes audibly progressive.

A 500 MB MP3 should be able to start playing after a small initial buffer instead of downloading 500 MB first.

The player's disk should not accumulate the song.

The client should keep only a bounded moving window of encoded bytes plus a small decoded PCM queue.

## Implementation notes

Target pipeline:

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> Minecraft/OpenAL positional source
```

MP3:

- use the exact shipped JLayer path unless another Java decoder is deliberately proven better
- temporary missing bytes must make the decoder worker wait/refill, not return permanent EOF
- use server MP3 frame metadata for earlier seek/rejoin anchors
- silently pre-roll after seek/rejoin to rebuild bit-reservoir state
- never make Minecraft's sound thread wait for network/decoder progress

WAV:

- mono/stereo only
- 8-bit unsigned PCM
- 16/24/32-bit signed PCM
- float32
- stereo downmix to mono
- reject >2 channels and unusual/compressed/telephony WAV encodings

Server metadata needs the internal WAV layout required for direct time-to-byte mapping, such as audio-data offset/length, sample format, bits/sample, frame size, sample rate, and channel count.

After the progressive MP3/WAV path is working, active prepared/local format advertising can be narrowed to MP3 + supported common WAV. Frozen M1D remains historical evidence for OGG/AIFF/AU rather than forcing them into the new engine.

---

# 17. M1H: players moving in/out of hearing range

## User-visible behavior

A player walks toward a speaker which has already been playing for 2 minutes:

- they should begin hearing approximately the current 2:00 point
- they should not need the song from 0:00

A player walks away:

- local sound should stop
- that client should stop requesting more encoded bytes
- the actual server song keeps playing

They come back 20 seconds later:

- if the song is still active, rejoin current time
- if it ended/stopped while they were away, remain silent

Network underrun:

- only that client may briefly go silent/refill
- server playback never pauses for them

Also harden dimension changes, chunk/speaker removal, disconnect, resource reload, stale generations, and VS2 movement.

Do not add latency/ping prediction just because it sounds sophisticated. Measure real drift first.

---

# 18. M1I: optional normal native FLAC

FLAC should not block completion of MP3/WAV.

Only advertise `.flac` if all of these are proven:

- byte-based native FLAC identification
- STREAMINFO/duration/sample facts
- mono/stereo validation
- bounded progressive decode from server ranges
- seek/rejoin support
- malformed-input/checksum behavior
- bounded RAM/cancellation
- stereo -> mono downmix
- packaged dependency works on NeoForge 21.1.247 and 21.1.248
- real Minecraft playback passes

Do not add Ogg-FLAC.

If the implementation becomes disproportionately troublesome, leaving FLAC unsupported is acceptable; MP3/WAV still form the core finite engine.

---

# 19. M1J and later

## M1J — functional multispeaker sync

Several physical speakers may use the same server media asset and optionally share one server sync clock.

Do not require an “expected number of speakers/clients” barrier before starting.

Each physical speaker still needs its own positional mono renderer so direction, attenuation, later occlusion/reverb, VS2 movement, and future SPR behavior remain correct.

Migrate inherited `*All` / `*At` helpers so they stop bypassing the modern ownership/state rules.

## M1K — active-session sharing optimization

Only after multispeaker behavior is correct, optimize duplicate work:

- coalesce duplicate active encoded requests where useful
- share decoding/PCM production for identical active timelines where safe
- still keep one positional output source per physical speaker
- do not reintroduce persistent client song caching

## M1L — legacy finite API migration/removal

Route useful `speakMp3(bytes)` / `speakWav(bytes)` compatibility frontends through the new engine where sensible.

Large files stay on `hq.playFile` / prepared assets.

Deprecate/remove old OGG-specific finite APIs instead of bringing Vorbis back.

Delete the old whole-packet/whole-PCM decoder and obsolete prototype packets once nothing depends on them.

## M1M — RAW finalization

Keep `hqspeaker_audio_empty`, bounded producer backpressure, and no fake finite duration/seek.

Move old multispeaker RAW behavior away from expected-group barriers.

## M1N — Minecraft/OpenAL cleanup

Fix final speaker sound category, one logical volume/gain stage, F3+T/resource reload behavior, stale channels, attenuation, and VS2 movement.

Finite output remains mono positional.

## M1O — lifecycle/performance hardening

Every earlier milestone should already have tests. M1O is a stress sweep, not where correctness testing begins.

Stress many speakers/players, seek spam, stop/replace/rejoin storms, integrated-server restart/world unload, bounded queues/executors, memory/network/tick usage, and packet-size benchmarks.

## M1P — CI/package freeze

Keep Java 21 builds green on both NeoForge targets and verify packaged dependencies/resources/scripts.

## M1Q — consolidated Minecraft acceptance

Run one final broad in-game pass only after the architecture is coherent:

- standard CC speaker compatibility
- HQ RAW
- MP3
- supported common WAV
- FLAC only if M1I was actually implemented
- >8 MiB and 50–100+ MiB finite files
- progressive start before full transfer
- pause/resume/seek/loop/EOF/volume
- replacement
- late range entry
- leave/return
- stop while away
- dimension change
- F3+T
- speaker replacement
- disconnect/rejoin
- synchronized multispeaker playback
- independent speaker desync
- mono downmix
- bounded RAM/network use
- server/client tick and sound-thread stall checks

After M1:

- M2 = Sound Physics Remastered integration
- M3 = live/open-ended Internet MP3/HLS/TS rebuild
- M4 = release cleanup/documentation/API/license cleanup

---

# 20. Important known issues that remain

The current active high-priority gaps are:

- **KI-004:** leaving range can still leave stale client renderer state; target M1H
- **KI-026:** current M1E client still downloads a complete local file; target M1F/M1G
- **KI-027:** current finite file IO still touches game threads; target M1F/M1G
- **KI-030:** no final demand-driven range protocol yet; target M1F
- **KI-031:** progressive MP3 must distinguish temporary starvation from real EOF; target M1G
- **KI-032:** MP3 seek/rejoin needs earlier-frame pre-roll; target M1G
- **KI-033:** historical WAV acceptance is broader than final common-WAV converter; target M1G
- **KI-034:** FLAC is desired but unproven; target M1I

Retained later issues:

- legacy finite decoder has an unbounded executor queue
- legacy finite paths can materialize whole decoded PCM
- streaming/live gain is currently applied twice
- HLS media-window progression can stall
- TS path is not incremental
- unsupported TS audio can be misidentified as PCM
- live server state is intent rather than real renderer/network truth
- old multispeaker expected-member barriers can deadlock partial listeners
- old shared stream sessions may leak before start
- separate `hqspeaker:hq_speaker` block may be duplicated/dead architecture
- inherited 8 MiB old byte APIs remain until migration
- repository `LICENSE` is MPL-2.0 while NeoForge metadata says LGPL-3.0; resolve before public release

Already resolved in source by M1E include the renderer-authority problem, client STARTED/SEEKED/ENDED rewriting server state, the no-renderer timeout, exact-duration seek semantics, early canonical EOF vs transfer lifetime, and lack of an authoritative state snapshot.

---

# 21. Runtime/testing state

Important runtime scripts:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua`
- `scripts/m1c_local_import_test.lua`
- `scripts/m1d_media_analysis_test.lua`
- `scripts/m1e_server_authority_test.lua`

Do not report a script as PASS unless it was actually executed successfully in Minecraft.

Known real runtime evidence from earlier work includes native `playAudio`, HQ `speakPCM`, MP3 dependencies, a roughly 2.87 MB MP3 playback, volume differences, and clean shutdown. Some older OGG/URL/multispeaker/reload/loop tests were skipped or inconclusive; skipped is not pass.

M1E source/unit/CI is green but the focused M1E Minecraft runtime script is still pending.

Testing rule going forward: each milestone must ship its deterministic tests with the implementation. Do not postpone all proof to M1Q.

---

# 22. Things not to accidentally rebuild

Do not silently reintroduce:

- client persistent song cache
- full-file-before-playback as the final design
- OGG/AIFF/AU as required final formats
- >2-channel finite audio
- Java playlist logic
- client authority over finite playback time
- an anchor renderer which defines whether a server song is “really playing”
- a fixed expected number of listeners/speakers before sync starts
- server-side decoding of the whole song into PCM for network transfer
- fake finite duration/seek for RAW or live streams
- `System.nanoTime()` subtraction between server and client JVMs

---

# 23. What the next chat should do first

Before writing M1F code:

1. Fetch the **current** `codex/m1e-server-authoritative-finite` branch head.
2. Compare it to M1E code-bearing checkpoint `d0e66ab9135359627086c13647d5241ad778643f` and identify whether anything newer changed runtime Java or only docs.
3. Re-read `HQFiniteMediaServer`, `HQFiniteMediaClient`, protocol packet classes, `MediaAssetStore`, `MediaMetadata`, `FinitePlaybackClock`, `ROADMAP.md`, `CURRENT-STATE.md`, and `KNOWN-ISSUES.md`.
4. Recheck current CI before claiming the branch is green.
5. Keep M1F limited to demand-driven transport/off-thread IO/bounded active buffers. Do not prematurely fold all M1G decoder work into M1F unless there is a concrete dependency which makes the split impossible.
6. Add focused deterministic M1F tests while implementing it.
7. After M1F source/test/CI work, re-read the changed source and try to disprove the implementation before declaring it complete.

When reporting progress to the user, explain the Minecraft behavior first. Example:

> “The old server used to send the entire song whether the client still needed it or not. M1F now makes the player's game ask only for the pieces it needs. If the player walks away or the song is replaced, the server stops doing useless work.”

Then add the implementation note:

> “Implementation: `HQFiniteMediaServer` now services bounded range requests off-thread and stale generation/relevance checks discard obsolete reads.”

That order is important.

---

# 24. Primary docs to read after this handoff

After reading this standalone handoff, use these as the current supporting documents:

- `docs/CURRENT-STATE.md` — what the active implementation currently does
- `docs/VERIFIED-FACTS.md` — source/CI/runtime facts
- `docs/ARCHITECTURE.md` — target architecture
- `docs/M1E-SERVER-AUTHORITY.md` — what M1E actually implemented
- `docs/M1E-FINITE-STREAMING-DESIGN.md` — M1F+ finite-streaming implementation contract
- `docs/ROADMAP.md` — milestone order
- `docs/KNOWN-ISSUES.md` — unresolved problems
- `docs/TESTING.md` — testing/evidence rules
- `docs/CC-T-COMPATIBILITY-CONTRACT.md` — exact normal CC:T compatibility boundary

Historical milestone docs remain useful evidence for the code they describe, but they do not override current source or the current product scope.
