# Current state

## Preparation-only checkpoint

The project is currently paused **before** finishing M1E Minecraft runtime acceptance and **before** starting M1F implementation.

Do not implement M1F, change M1E semantics, or repair the temporary decoder from this checkpoint unless explicitly requested.

Read `PRE-M1F-PREPARATION.md` first. It records the current sequencing decisions, clean-break choice for M1F, decoder boundary, and exact acceptance split. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` records the latest runtime evidence. `FUTURE-CLEANUP.md` parks obsolete/legacy cleanup work so it does not expand M1E/M1F scope.

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa`
- verified M1C/config base: `33bcc6e04a2734500b7b15b84bee884562539216`
- frozen M1D source/test/CI head: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- M1E semantic implementation checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`
- pre-preparation diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- active branch: `codex/m1e-server-authoritative-finite`

M1D final CI run: `34635484316` — success on NeoForge 21.1.247 and 21.1.248.

M1E semantic implementation CI run: `34658958488` — success on NeoForge 21.1.247 and 21.1.248.

Latest diagnostic-head CI run: `34686003774` — success on NeoForge 21.1.247 and 21.1.248.

Important history correction: commits after `d0e66ab...` are **not all documentation-only**. The branch received several Java changes used solely for runtime diagnostics. Those commits did not intentionally change the M1E server-authority architecture, but they make the active branch head code-bearing.

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

CC:HQ Speakers upgrades the normal CC:T speaker into a programmable audio peripheral. Lua owns application policy such as playlists, alarms, notifications, speech, ambience, and sequencing. Java exposes truthful technical source capabilities.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a server-owned timeline;
- live/open-ended network streams later.

Do not add Java concepts such as music/effect/notification roles or a playlist manager.

## Final finite direction

Core target formats:

- MP3 / MPEG Layer III;
- common WAV.

Wanted but separately gated:

- normal native FLAC, only after its analyzer/decoder/seek/package path is proven.

Not final product requirements:

- OGG Vorbis;
- Ogg-FLAC;
- AIFF/AIF;
- AU/SND;
- exotic/compressed/telephony WAV variants;
- >2-channel finite input.

One physical speaker renders one mono positional source. Mono stays mono; stereo is downmixed; >2 channels are rejected.

The final finite path is:

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

## M1A retained behavior

The active branch retains the M1A single-speaker compatibility/output work:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`;
- notes remain independent;
- one incompatible HQ continuous source replaces the prior HQ source;
- HQ RAW has separate bounded `hqspeaker_audio_empty` producer pacing;
- `speakMaxSamples()` reports 131072;
- ownership-changing calls on one physical speaker are serialized.

M1A Minecraft acceptance remains pending until its focused runtime contract is actually executed successfully.

## M1B/M1C server asset foundation

`MediaAssetStore` provides server-side UUID media identity, exact disk-backed import, quotas, retain/release lifetime, final-reference deletion, and seekable reads. This is server storage, not a client cache.

The local-file path is:

```text
ComputerCraft file
    -> temporary HQ staging mount
    -> immutable server MediaAsset
    -> prepared reference
    -> separate playback reference
```

Current server-owned storage defaults are 512 MiB per asset and 2048 MiB total; `0` removes the corresponding HQ-specific quota. ComputerCraft filesystem capacity is not changed.

## M1D — frozen historical media analysis

M1D is frozen at `4a2cd5de96228fc091226c7e72fb669b82be258c`. Final GitHub Actions run `34635484316` passed both target NeoForge versions.

M1D moved format/duration truth onto the server and analyzes the exact immutable committed asset with bounded reads. Historical M1D analysis includes MP3, OGG Vorbis, WAV, uncompressed AIFF, and AU. That is historical code truth, not the final format promise.

Useful retained facts include scanned MP3 duration, real MP3 frame-offset seek points, server-known duration/sample-rate/channels/size, bounded seek metadata, and immutable committed-byte analysis before Lua receives the asset UUID.

MP3 duration is encoded-frame duration. Optional gapless delay/padding correction remains later accuracy work.

## M1E — server-authoritative finite playback

**Source/test/CI semantics implemented; Minecraft runtime acceptance remains pending.**

The server-side finite model is no longer renderer-authoritative:

- semantic states are `PLAYING`, `PAUSED`, `ENDED`, `ERROR`;
- there is no server `LOADING` state for client buffering;
- successful finite play starts the canonical clock immediately at position 0;
- the clock advances even with zero listeners;
- `successfulRenderers`, canonical `observed`, and the 15-second no-renderer failure were removed;
- natural non-looping EOF is determined from the known server duration/clock;
- looping uses wrapped server position;
- non-looping `seek(duration)` ends immediately;
- status/control paths finalize elapsed EOF before returning/applying state;
- canonical EOF closes the temporary transfer before releasing the playback asset reference.

Protocol v4 adds `HQFiniteMediaStatePacket`. Client -> server finite telemetry is only READY plus diagnostic ERROR. Client decode/render failure cannot rewrite canonical server truth.

The current M1E bridge still pushes the complete encoded asset to a client `.part/.media` file and then uses `FileFiniteAudioStream`. That bridge is deliberately temporary.

## Latest M1E runtime evidence

The 2026-09-12 diagnostic run repeatedly reached:

```text
BEGIN
-> authoritative PLAYING state
-> full old transfer complete
-> JavaSound/mp3spi decoder open
-> READY
-> fresh authoritative state
-> renderer submission
-> first PCM read returns no data
-> client diagnostic ERROR
```

Despite the local decoder failure, server state/control traffic continued independently, including transitions consistent with pause/resume/end/loop behavior. This supports the intended authority separation.

However the captured logs do **not** contain:

```text
M1E server-authority contract passed
```

Therefore M1E remains Minecraft-runtime pending. Do not promote it to PASS from the diagnostic logs alone.

See `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`.

## MP3 duration/decoder finding

For the runtime fixture:

- server analyzer: `161.304 s`;
- JavaSound/mp3spi client: `322.584 s`;
- runtime operator reports the source is roughly 2:45 (~165 s).

The MP3SPI duration is clearly wrong and is not authoritative. The server analyzer remains canonical.

The temporary MP3 seek path is also known to misuse mp3spi skip semantics and can report a requested seek target after incomplete positioning. This is a bridge defect to remember, not a reason to pull decoder repair into M1E/M1F.

Decision:

**keep decoder requirements in mind while designing transport, but do not expect the current decoder to work or work correctly before M1G.**

## M1F boundary — agreed clean break

M1F remains the next implementation milestone, but implementation has **not started** at this preparation checkpoint.

When explicitly started, M1F should make a clean break for the modern prepared finite path rather than keeping the old whole-file prepared bridge alive in parallel.

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

The M1F client encoded-data layer must nevertheless distinguish temporary missing data from true asset EOF so M1G can consume it progressively without redesigning the transport boundary.

## M1G boundary

M1G owns:

- progressive MP3 via the shipped JLayer family unless a better decoder is proven;
- common WAV conversion;
- temporary-starvation-vs-real-EOF handling;
- MP3 earlier-anchor pre-roll for Layer III reservoir state;
- bounded mono PCM queues;
- decoder cancellation;
- actual Minecraft/OpenAL audible finite rendering;
- final active-branch MP3/common-WAV format narrowing.

## Transitional/legacy code

Do not clean unrelated legacy code during preparation or M1F unless it directly blocks the milestone.

A dedicated inventory now lives in `FUTURE-CLEANUP.md`, including the old file decoder/cache, transitional packets, legacy finite engine, old byte APIs, multispeaker bypasses, old format surfaces, dependency review, custom HQ block, live/HLS/TS issues, diagnostics cleanup, tests/docs, packaging, and license metadata.

## Read order before implementation resumes

1. `PRE-M1F-PREPARATION.md`
2. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`
3. `CURRENT-STATE.md`
4. `VERIFIED-FACTS.md`
5. `ARCHITECTURE.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-FINITE-STREAMING-DESIGN.md`
8. `ROADMAP.md`
9. `KNOWN-ISSUES.md`
10. `TESTING.md`
11. `FUTURE-CLEANUP.md`
12. exact current branch source and current CI

Older handoffs remain useful historical/deep context but must not override exact current source or this preparation checkpoint when their branch-history wording is stale.
