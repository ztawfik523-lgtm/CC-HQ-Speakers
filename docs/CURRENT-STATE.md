# Current state

## Current checkpoint

The project is at a **documentation-only pre-M1F checkpoint**.

M1E server-authority source/tests/CI are finalized and re-reviewed. The project owner explicitly chose **not to perform the final manual M1E Minecraft acceptance run** and to move on later instead.

That means two things must remain true at the same time:

- M1E must **not** be described as Minecraft-runtime verified or as having a recorded PASS;
- the skipped manual test is no longer being treated as a sequencing gate by project decision.

M1F implementation has **not started**. This checkpoint is documentation/preparation only.

Read `LUA-API.md` for the current ComputerCraft programming surface and `HANDOFF-2026-09-13-PRE-M1F.md` for the fresh plain-language continuation handoff.

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- original M1E semantic implementation: `d0e66ab9135359627086c13647d5241ad778643f`
- 2026-09-12 runtime-diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`
- last pre-documentation branch head: `ff8fc52e8660249150e056d1dff4307377afe7c4`
- active branch: `codex/m1e-server-authoritative-finite`

Known green CI anchors include:

- M1D run `34635484316` — NeoForge 21.1.247 and 21.1.248 passed;
- original M1E run `34658958488` — both targets passed;
- diagnostic head run `34686003774` — both targets passed;
- M1E finalization candidate run `34725651930` — both targets passed;
- documentation head run `34725867558` at `ff8fc52e...` — both targets passed.

The M1E finalization candidate changed acceptance coverage, not server semantics, transport, or decoder behavior.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## How to explain the project

Explain behavior in ComputerCraft/Minecraft terms first, then implementation details only when useful.

The basic finite-file story is:

```text
ComputerCraft has a file
    -> HQ Speakers imports it into server-owned media storage
    -> the speaker starts a server-owned playback timeline
    -> relevant clients ask for only the small encoded pieces they currently need
    -> later the client decodes those pieces and plays positional sound
```

Do not lead explanations with class names, packet names, executor terminology, or internal state-machine abstractions when a user-facing description is sufficient.

## Product identity

CC:HQ Speakers upgrades the normal CC:T speaker into a programmable audio peripheral. Lua owns application policy such as playlists, alarms, notifications, speech, ambience, and sequencing. Java exposes truthful technical source capabilities.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a server-owned timeline;
- live/open-ended network streams later.

Do not add Java concepts such as music/effect/notification roles or a playlist manager.

## Current Lua/API direction

The modern user-facing finite workflow is documented in `LUA-API.md`.

Recommended high-level helpers:

- `hq.playFile(speaker, path [, options])`
- `hq.prepareFile(speaker, path)`
- `hq.preparedInfo(speaker, assetId)`
- `hq.playPrepared(speaker, assetId [, options])`
- `hq.releasePrepared(speaker, assetId)`

Modern finite controls remain:

- `audioStatus()`
- `audioPause()`
- `audioResume()`
- `audioSeek(seconds)`
- `audioSetVolume(volume)`
- `audioSetLooping(loop)`
- `audioStop()`

Lower-level staging/import functions remain documented because the Lua module uses them, but normal programs should prefer the `hqspeaker` helpers.

### `audioPlayStaged()` decision

`audioPlayStaged()` is **our old prototype API**, not an original HQ Speakers compatibility surface.

Source provenance:

- it is absent from the untouched inherited baseline;
- it appears in this project's frozen staged/local-file prototype;
- the current source itself labels it historical direct-staged behavior.

Project decision on 2026-09-13:

**remove `audioPlayStaged()` when M1F implementation begins.**

Do not preserve a second old direct-staged playback transport just for that prototype function. New programs should use `hq.playFile()` or prepare/play/release.

No code removal has happened yet at this documentation checkpoint.

## Final finite direction

Core target formats:

- MP3 / MPEG Layer III;
- common WAV.

Wanted but separately gated:

- native FLAC, only after its analyzer/decoder/seek/package path is proven.

Not final product requirements:

- OGG Vorbis;
- Ogg-FLAC;
- AIFF/AIF;
- AU/SND;
- exotic/compressed/telephony WAV variants;
- >2-channel finite input.

One physical speaker renders one mono positional source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

The target finite path remains:

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> authoritative server timeline/state
    -> bounded client-requested encoded ranges
    -> bounded temporary client encoded RAM
    -> progressive decoder/converter worker
    -> bounded mono PCM
    -> positional Minecraft/OpenAL renderer
```

There is no final persistent client song cache, `.part` library, completed media library, sparse cache, LRU database, or cross-restart resume.

## M1A/M1B/M1C/M1D retained foundation

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`. HQ RAW retains separate bounded `hqspeaker_audio_empty` pacing and one continuous-source ownership boundary.

`MediaAssetStore` provides server-side UUID media identity, exact disk-backed import, quotas, retain/release lifetime, final-reference deletion, and seekable reads. This is server storage, not a client cache.

Prepared local files follow:

```text
ComputerCraft file
    -> temporary HQ staging mount
    -> immutable server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Frozen M1D analyzes committed bytes and provides server metadata including scanned MP3 duration and bounded seek hints. Its historical OGG/AIFF/AU surface does not define the final format promise.

## M1E — server-authoritative finite playback

M1E's server-authority semantics are implemented and were rechecked before finalization.

Current source guarantees:

- semantic states are `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- there is no canonical server `LOADING` state for client buffering;
- successful finite play starts the canonical server clock immediately;
- the clock does not wait for READY or any renderer handshake;
- renderer observation/authority and its no-renderer timeout are gone;
- server duration/clock determines natural non-looping EOF;
- looping uses wrapped server position;
- non-looping `seek(duration)` ends immediately at duration;
- looping `seek(duration)` wraps to zero;
- client READY only requests a fresh state snapshot;
- client ERROR is diagnostic only and cannot globally fail playback;
- canonical EOF closes the temporary transfer before releasing the playback asset reference;
- replay after terminal END creates a new generation while the prepared owner remains valid;
- STOP returns the finite server state to idle.

Protocol v4 carries authoritative mutable truth in `HQFiniteMediaStatePacket`. BEGIN remains temporary setup for the old bridge.

### Runtime evidence status

The 2026-09-12 diagnostic logs strongly support the authority separation: the server continued its own PLAYING/PAUSED/resume/end/loop timeline while the old client decoder failed.

The strengthened final acceptance script exists and the M1E finalization candidate passed CI/package verification, but the project owner chose not to run that final manual Minecraft script.

Therefore:

- do **not** write “M1E runtime PASS”;
- do **not** erase the known runtime-evidence gap;
- do not block future M1F implementation on that skipped manual run unless the project owner later changes that decision.

## Temporary decoder finding

The current prepared finite client still downloads the complete encoded asset to `.part/.media` and uses `FileFiniteAudioStream`.

The MP3 bridge is known unreliable:

- first PCM read can end immediately after its seek path;
- MP3SPI reported `322.584 s` for a roughly 2:45 fixture while the server analyzer reported `161.304 s`;
- the MP3 seek helper mixes decoded-byte assumptions with mp3spi compressed frame/byte skip behavior;
- it can report the requested target after incomplete positioning;
- renderer restart currently performs a redundant second seek.

These defects remain documented and deferred. Do not repair the bridge merely to keep M1F audible.

Agreed rule:

**keep decoder needs in mind architecturally, but do not expect the temporary decoder to work correctly before M1G.**

## M1F boundary — agreed clean break, not started

M1F has not started.

When explicitly started, M1F makes a clean break for the modern prepared finite path rather than maintaining the old whole-file prepared bridge in parallel.

In player terms:

```text
server owns the whole song
    -> client asks for a small piece near where playback is now
    -> old pieces are discarded
    -> seek asks for a different piece instead of downloading everything in between
```

M1F owns:

- client-requested bounded encoded byte ranges;
- server-selected stream/seek anchors;
- generation/asset/range/relevance validation;
- bounded outstanding work/rate controls;
- bounded off-thread server asset reads with safe retained lifetime;
- stale async completion discard;
- bounded temporary client encoded RAM/window;
- arbitrary encoded offsets;
- no persistent client song files/cache.

M1F does **not** require audible finite playback, PCM decoding, MP3 pre-roll, WAV conversion, or a final renderer. Those are M1G.

Its client encoded-data layer must still distinguish temporary missing data from true asset EOF so M1G can consume it progressively without redesigning transport.

### Shutdown/lifetime requirement noticed during recheck

When M1F adds background server reads, shutdown must stop/drain those reads before the server media store deletes/closes its assets. This is an implementation requirement, not a user-facing design choice.

## M1G boundary

M1G owns:

- progressive MP3 and common-WAV decode/conversion;
- temporary-starvation-vs-real-EOF handling;
- MP3 earlier-anchor pre-roll for Layer III reservoir state;
- bounded mono PCM queues;
- decoder cancellation;
- actual Minecraft/OpenAL audible finite rendering;
- final active-branch MP3/common-WAV format narrowing.

## Parked cleanup

Do not mix unrelated legacy cleanup into M1F unless it directly blocks the milestone.

`FUTURE-CLEANUP.md` tracks the old file decoder/cache, transitional packets, legacy finite engine, byte APIs, multispeaker bypasses, old format surfaces, dependency review, diagnostic logging, custom HQ block, live/HLS/TS issues, OpenAL cleanup, tests/docs, packaging, and license metadata.

## Current read order

1. `HANDOFF-2026-09-13-PRE-M1F.md`
2. `LUA-API.md`
3. `CURRENT-STATE.md`
4. `VERIFIED-FACTS.md`
5. `ARCHITECTURE.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-FINITE-STREAMING-DESIGN.md`
8. `ROADMAP.md`
9. `KNOWN-ISSUES.md`
10. `TESTING.md`
11. `FUTURE-CLEANUP.md`
12. `M1E-FINALIZATION-2026-09-13.md`
13. exact current branch source and current CI

Older handoffs and the old pre-M1F preparation file remain historical/deep context only.