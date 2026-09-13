# Roadmap

## Product rule

Build a better **programmable ComputerCraft speaker peripheral**. Java exposes truthful audio capabilities; Lua decides whether they are used for music, alarms, speech, notifications, soundboards, PA systems, ambience, or anything else.

Explain behavior in player/Lua terms before implementation detail.

Technical source categories remain:

- standard CC:T speaker behavior (`playNote`, `playSound`, `playAudio`, `stop`);
- HQ raw/feed PCM;
- finite encoded media with a truthful server-owned timeline;
- live/open-ended network streams, later.

Do not add application roles or a Java playlist manager.

For the intended user-facing API, see `LUA-API.md`.

## Reference points

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`;
- frozen local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`;
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`;
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`;
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`;
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`;
- original M1E semantic head: `d0e66ab9135359627086c13647d5241ad778643f`;
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`;
- last pre-documentation head: `ff8fc52e8660249150e056d1dff4307377afe7c4`;
- active branch: `codex/m1e-server-authoritative-finite`.

M1E finalization CI run `34725651930` passed NeoForge 21.1.247 and 21.1.248 with tests/package verification.

The final manual M1E Minecraft acceptance run was prepared but later skipped by project decision. Therefore M1E has **no recorded final runtime PASS**. That evidence gap remains documented but is no longer treated as a mandatory sequencing gate before future M1F work.

Current checkpoint: documentation/preparation only. M1F implementation has **not started**.

## Settled finite architecture

1. A finite file is a **server asset**, separate from playback and from a physical speaker.
2. Successful finite `play` starts the **server clock immediately**, even with zero listeners.
3. The server owns generation, state, duration, position, pause/resume, seek, loop, volume, and EOF.
4. A client renderer never becomes canonical playback authority.
5. Finite files are streamed progressively in bounded encoded ranges in the final path.
6. There is no persistent client song cache; clients keep bounded temporary encoded/decoded RAM.
7. Seek and late join fetch a bounded encoded window around the server-selected anchor/current position.
8. A slow client may go silent/refill/rejoin; it never slows the canonical server clock.
9. One physical speaker renders mono positional audio. Mono stays mono; stereo is downmixed; >2 channels are rejected.
10. Core finite formats are MP3 and common WAV. Native FLAC is a separately gated extension.
11. OGG Vorbis, AIFF/AIF, AU/SND, Ogg-FLAC, exotic WAV encodings, and surround finite input are not final product requirements.
12. Standard CC:T speaker behavior remains delegated to the real CC:T `SpeakerPeripheral`.
13. Large local finite media is the priority. Live Internet streams and SPR remain later milestones.

## User-facing finite API direction

New programs should use:

```text
hq.playFile
hq.prepareFile
hq.preparedInfo
hq.playPrepared
hq.releasePrepared
```

with the capability-oriented `audio*` finite controls.

`audioPlayStaged()` is **our old staged-file prototype API**, not inherited HQ Speakers compatibility. Project decision: remove it when M1F implementation begins. Do not keep a second old direct-staged transport solely for that function.

## Completed foundation

### M0.5 — cleanup/redesign preparation

Completed at `ad38412a2173f849a0fc8e867030da8a78965c9c`.

### M1A — CC:T compatibility and output ownership

Source/CI implemented. Normal single-speaker standard methods delegate to CC:T; HQ continuous ownership and RAW backpressure are bounded. Broad Minecraft runtime acceptance remains incomplete.

### M1B — reusable server media assets

Completed at `40091ee32f412c1208e9016fca288b8d4f902dfa`. Server-side encoded assets are UUID-addressed, disk-backed, reference-counted, quota-controlled, and seekably readable.

### M1C — ComputerCraft local-file import/config

Verified base `33bcc6e04a2734500b7b15b84bee884562539216`. ComputerCraft files stage/import into reusable server assets. The staging mount is temporary import space, not the media library.

### M1D — server media analysis

Frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`, CI `34635484316` green on both target NeoForge versions. Historical analysis support includes MP3/OGG/WAV/AIFF/AU but does not define the final product surface.

## M1E — server-authoritative finite state + snapshots

**Status: source/tests/CI finalized; final manual Minecraft acceptance skipped / no recorded PASS.**

M1E makes finite playback semantically server-owned before replacing the old transport:

- server states are `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- no server `LOADING` state waits for client buffering;
- prepared play starts canonical time immediately;
- playback advances with zero listeners;
- renderer observation/authority and the no-renderer timeout are removed;
- client finite status is only READY and diagnostic ERROR;
- client failure does not rewrite canonical playback;
- server duration/clock determines natural EOF;
- non-looping exact-duration seek ends immediately;
- looping exact-duration seek wraps to zero;
- canonical EOF closes the temporary transfer before releasing the playback asset reference;
- protocol v4 adds authoritative `finite_state` snapshots;
- READY requests fresh server state rather than telling the server playback started.

The remaining whole-file server push, fixed recipient set, server-tick file reads, client `.part/.media` bridge, and complete-file decoder are intentionally not final architecture.

## M1F — demand-driven finite transport

**Next implementation milestone; not started yet.**

Player-level goal:

```text
server owns the whole file
    -> client asks for a small encoded piece near the current playback point
    -> server sends that bounded piece
    -> client keeps a bounded temporary RAM window
    -> old pieces are discarded
    -> seeking requests a different piece instead of downloading everything in between
```

M1F implementation owns:

- client -> server bounded range requests carrying source/generation/asset/offset/length;
- server -> client bounded range data carrying source/generation/asset/offset/bytes;
- bounded response packets, with size tunable rather than frozen prematurely;
- active playback/generation/asset/player/dimension/relevance/range validation;
- bounded outstanding request count/bytes and rate protection where needed;
- bounded server IO executor/queue;
- no large asset reads on the server tick;
- safe retained asset lifetime while asynchronous reads are in flight;
- re-check generation/player relevance before sending completed asynchronous reads;
- stale-result discard after replacement/leave/disconnect;
- bounded temporary client encoded buffers only;
- arbitrary encoded offsets;
- stop/seek/leave cancellation of obsolete demand;
- removal of the modern prepared-path `.part/.media` client-file requirement;
- removal of `audioPlayStaged()` from the modern API/transport path.

M1F acceptance does **not** require audible MP3/WAV playback.

A deterministic fake/test consumer is sufficient to prove exact ranges, bounded memory, discard behavior, arbitrary jumps, and the distinction between:

- data available now;
- data needed but not arrived yet;
- true asset EOF;
- stale/cancelled playback.

### M1F stream/seek descriptor rule

Do not require clients to own the server's whole seek index. The server may provide an anchor at/before the desired current time with only the codec/layout facts needed later by M1G.

M1F makes earlier-anchor range requests possible; MP3 pre-roll itself is M1G.

### M1F shutdown/lifetime rule

Background range reads must be stopped/drained/cancelled before the shared media asset store closes/deletes its files during server shutdown.

## M1G — core progressive finite engine: MP3 + common WAV

Goal: turn the bounded encoded windows from M1F into real progressive audio.

```text
bounded encoded RAM
    -> decoder/converter worker
    -> bounded mono PCM queue
    -> positional Minecraft/OpenAL source
```

M1G owns:

- progressive MP3 decoding;
- temporary starvation vs true EOF;
- earlier-anchor MP3 pre-roll/bit-reservoir reconstruction;
- common WAV conversion/downmix;
- bounded PCM queues;
- decoder cancellation;
- pause/resume/seek/loop integration with the local renderer projection;
- actual audible positional finite playback;
- final prepared/local format narrowing to MP3 + implemented common WAV.

Common WAV target: unsigned 8-bit PCM, signed 16/24/32-bit PCM, and float32, mono/stereo only.

## M1H — dynamic listener lifecycle and recovery

Goal: make the streamed engine behave correctly as players move.

- newly relevant listeners join current server time;
- leaving range stops local demand/rendering and frees temporary buffers;
- returning while active rejoins current time;
- returning after stop/end stays silent;
- dimension change, chunk/speaker removal, disconnect, resource reload, and VS2 movement are safe;
- underrun causes local refill/rejoin, never canonical pause.

## M1I — native FLAC extension, gated

FLAC is wanted but does not block MP3/WAV completion. Only advertise native `.flac` after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, packaging, and Minecraft runtime behavior are proven. Do not add Ogg-FLAC.

## M1J — multispeaker shared clocks and correct physical renderers

Functional correctness first:

- one server asset may back many playbacks;
- synchronized playbacks reference a shared server sync clock;
- no expected-global-member/tap barrier;
- each physical block keeps its own mono positional renderer;
- inherited `*All`/`*At` helpers stop bypassing modern ownership/state rules.

## M1K — active-session sharing/fan-out optimization

After M1J correctness, coalesce duplicate active encoded/decode work where useful while preserving independent positional outputs. Do not introduce persistent client caching.

## M1L — legacy finite API migration/removal

Review inherited compatibility frontends such as `speakMp3(bytes)` / `speakWav(bytes)`. Route useful ones into the new asset engine where sensible. Large files remain `hq.playFile`/prepared-asset territory. Remove obsolete whole-file/whole-PCM finite machinery after callers are migrated.

## M1M — HQ raw/feed finalization

Preserve bounded RAW backpressure and `hqspeaker_audio_empty`; do not invent finite duration/seek for raw feeds. Migrate legacy multispeaker RAW helpers away from expected-member behavior.

## M1N — Minecraft/OpenAL integration cleanup

Finalize sound category, one logical gain stage, F3+T/resource reload recovery, stale-channel cleanup, attenuation/VS2 movement, and one mono positional source per physical speaker.

## M1O — lifecycle/performance hardening

Stress bounded executors/queues, cancellation/seek storms, repeated replacement/rejoin, server restarts/unloads, memory/network/tick behavior, packet-size tuning, and absence of persistent client song state.

## M1P — CI/package verification

Keep Java 21 builds green on NeoForge 21.1.247 and 21.1.248. Verify metadata, mixins, decoder dependencies, and bundled ComputerCraft ROM module.

## M1Q — consolidated Minecraft acceptance

Run the final batched M1 regression/integration pass across standard CC:T behavior, HQ RAW, MP3, supported common WAV, optional FLAC if gated, large files, progressive start, controls/EOF/replacement, dynamic range/rejoin, resource reload/disconnect, multispeaker behavior, mono downmix, bounded resources, and thread-stall checks.

## M2 — Sound Physics Remastered

Integrate the frozen SPR compatibility work only after finite positional renderer lifecycle is stable. Shared decode must never collapse several physical speakers into one positional OpenAL source.

## M3 — live/open-ended streams

After finite/local media is stable, rebuild live MP3/HLS/TS around truthful live semantics, reconnect-to-current-live behavior, correct gain, HLS progression, incremental TS, explicit unsupported-codec failure, bounded HTTP/resources, and dynamic renderer lifecycle.

## M4 — release cleanup

Finalize docs/API surface, remove obsolete prototype/dead code, resolve the custom HQ block decision, remove obsolete finite-format claims, update packaging metadata, and resolve the MPL-2.0 vs NeoForge metadata LGPL-3.0 mismatch from actual provenance before public release.