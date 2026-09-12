# CC:HQ Speakers — agent guide

## Start here

This repository improves the normal CC:Tweaked `speaker` peripheral into a **high-quality programmable audio peripheral** for the exact ATM10 target stack.

Before changing code, read in this order:

1. `docs/NEXT-CHAT-HANDOFF.md`
2. `docs/CURRENT-STATE.md`
3. `docs/VERIFIED-FACTS.md`
4. `docs/ARCHITECTURE.md`
5. `docs/M1E-SERVER-AUTHORITY.md`
6. `docs/M1E-FINITE-STREAMING-DESIGN.md`
7. the current milestone in `docs/ROADMAP.md`
8. `docs/KNOWN-ISSUES.md`
9. `docs/TESTING.md`
10. `docs/CC-T-COMPATIBILITY-CONTRACT.md` before changing standard speaker behavior

Then re-read the current branch source and current CI. Exact source/runtime evidence overrides documentation if they disagree.

## Exact target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

Active implementation branch:

`codex/m1e-server-authoritative-finite`

Exact M1E code-bearing checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Exact M1E CI run:

`34658958488`

M1E source/test/package CI passed both target NeoForge versions. Minecraft M1E runtime acceptance is still pending until `scripts/m1e_server_authority_test.lua` actually passes in-game on the target stack.

## Product rule

This is **not** a built-in music player, notification system, sound-effect player, or Java playlist manager.

Lua decides whether audio is music, alarms, speech, ambience, notifications, soundboards, playlists, or anything else. Java distinguishes sources only by technical capabilities.

Do not create permanent Java concepts such as music/effect/notification lanes, automatic application priorities, playlists, albums, or a client music library.

## Standard CC:T compatibility is mandatory

The normal `computercraft:speaker` remains the main product surface and exposed peripheral type remains `speaker`.

Standard behavior must continue to come from CC:T's real `SpeakerPeripheral` unless exact target-stack evidence requires otherwise:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

HQ APIs extend that contract; they do not redefine it.

## Technical source categories

### Standard CC:T speaker audio

Preserve native CC:T behavior and backpressure.

### HQ raw/feed PCM

`speakPCM` is open-ended producer-fed audio. It has bounded backpressure and separate `hqspeaker_audio_empty` pacing. It does not truthfully have finite duration, arbitrary seek, or natural EOF.

### Finite media

Finite files have a known timeline and may truthfully support duration, position, pause/resume, seek, loop, volume, and natural EOF.

Core final finite formats:

- MP3 / MPEG Layer III
- common WAV

Wanted later but separately gated:

- normal native `.flac`

Not final finite requirements:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- exotic/compressed/telephony WAV variants
- >2-channel finite input

One physical speaker renders one mono positional source. Mono stays mono; stereo is downmixed to mono; >2 channels are rejected.

### Live network audio

Internet MP3/HLS/TS is later work. Live sources are open-ended and must not pretend to have finite duration/seek history.

## Settled finite architecture

These are no longer open design questions:

- A successful finite play starts the canonical **server clock immediately**, even with zero listeners.
- The server owns finite generation, state, duration, position, pause/resume, seek, loop, volume, and EOF.
- Client renderer readiness/failure never becomes canonical playback authority.
- Finite media is a reusable server `MediaAsset`, separate from playback and physical speakers.
- Final finite transfer is **client-pulled in bounded encoded byte ranges**.
- There is **no persistent client song cache**, `.part` library, completed client media library, sparse-file cache, LRU database, or cross-restart download resume in the final path.
- Clients keep only bounded temporary encoded/decoded RAM for active playback.
- Seek and late join fetch fresh bounded encoded data around a server-selected anchor/current position.
- A slow client may go silent/refill/rejoin; it never slows or rewinds the canonical server timeline.
- Do not compare server and client `System.nanoTime()` values across JVMs.
- Multispeaker synchronization must not wait for an expected global member/tap count.
- Each audible physical speaker ultimately keeps its own positional renderer for correct spatial audio and future SPR behavior.

## Current source model

The repository intentionally still contains overlapping generations of code:

1. standard CC:T behavior delegated through `HQSpeakerCompositePeripheral`;
2. inherited legacy HQ raw/finite/live code in `HQSpeakerPeripheral`, `HQAudioStream`, and legacy packets;
3. the modern prepared finite path in `HQFiniteMediaServer` / `HQFiniteMediaClient`.

Do not mistake legacy finite renderer authority for the modern prepared-file design. The legacy byte APIs still exist and are migrated later.

M1E already made the prepared finite path server-authoritative, but its transport is intentionally transitional: fixed recipients, whole-file server push, server-tick reads, client `.part/.media` files, and complete-file decoding still exist only as a bridge to M1F/M1G.

## Current milestone: M1F

M1F is **demand-driven finite transport**. Keep its scope narrow.

Implement:

- client -> server bounded range requests carrying source/generation/asset/offset/length;
- server -> client bounded range data carrying source/generation/asset/offset/bytes;
- validation of active playback, generation, asset, player connection, dimension/current relevance, range bounds, and resource limits;
- bounded outstanding request count/bytes and rate protection where needed;
- bounded server IO executor/queue;
- no large asset reads on the server tick;
- safe retained asset lifetime while async reads are in flight;
- re-check generation/player relevance before sending completed async work;
- discard stale completions after replacement/leave/disconnect;
- bounded temporary client encoded RAM only;
- arbitrary encoded offsets so seek/rejoin do not require downloading from byte 0;
- codec-appropriate server-selected seek/stream anchors where needed.

M1F must add deterministic tests for request bounds, stale generation, relevance, outstanding work, cancellation, in-flight asset lifetime, stale async completion, packet sizing, and proof that large reads do not execute on the game tick.

### Do not pull M1G into M1F without evidence

M1G owns the progressive MP3/common-WAV decoder/converter path, bounded PCM queues, mono conversion/downmix, MP3 starvation-vs-EOF handling, and MP3 seek pre-roll.

Keep M1F as a generic bounded encoded-range transport unless implementation evidence proves the split is impossible.

## Do not clean unrelated later work during M1F

Unless a direct M1F dependency forces it, do not spend the M1F diff on:

- legacy `speakMp3(bytes)` / `speakWav(bytes)` migration;
- OGG/AIFF/AU removal from the historical path;
- HLS/TS/live-stream fixes;
- separate `hqspeaker:hq_speaker` block cleanup;
- sound category/gain cleanup;
- multispeaker sync redesign;
- SPR integration;
- license/provenance cleanup.

Those are real issues, but they belong to later milestones.

## Working rules

- Improve the inherited fork; do not rewrite unrelated working behavior merely for architectural cleanliness.
- Preserve useful compatible behavior, but do not preserve a proven bug.
- Prefer exact runtime/source evidence over plausible assumptions.
- Treat ideas as hypotheses until source/tests/runtime evidence support them.
- Keep server semantic state separate from client transfer/decoder/renderer state.
- Never invent duration/seek for open-ended sources.
- Do not classify audio by application meaning.
- Do not build Java playlist/priority policy.
- Avoid polishing concepts explicitly scheduled for deletion.
- Keep the frozen SPR V7.1 acoustics unchanged unless explicitly retuning them.
- Do not call a Minecraft runtime script PASS unless it was actually executed successfully.
- Do not use `git add .`.

## Evidence order

When claims conflict, trust them in this order:

1. successful Minecraft runtime evidence on the exact target stack;
2. exact current source code;
3. exact current CI/build/package evidence;
4. `docs/VERIFIED-FACTS.md`;
5. `docs/CURRENT-STATE.md`;
6. compatibility/design docs;
7. `docs/ROADMAP.md`;
8. milestone historical docs;
9. old prototype/P0 material.

Recommendations and unresolved choices must not be written into `VERIFIED-FACTS.md` as facts.

## Testing rule

Every milestone carries its own deterministic tests. A green Gradle/GitHub Actions build proves compilation/tests/package structure; it does **not** prove audible Minecraft behavior, renderer lifecycle, positional sound, resource reload, or real-client networking.

For the current pre-M1F checkpoint, run:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

It must verify immediate server progression, pause/resume, non-looping exact-duration END, and looping exact-duration wrap before M1E is called a Minecraft runtime PASS.
