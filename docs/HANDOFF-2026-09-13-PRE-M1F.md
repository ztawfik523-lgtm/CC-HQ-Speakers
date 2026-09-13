# Fresh handoff — pre-M1F documentation checkpoint

Date: 2026-09-13

This is the current plain-language-first handoff for continuing CC:HQ Speakers.

## What this mod is

The project upgrades the normal ComputerCraft `speaker` into a better programmable audio peripheral.

Lua decides whether audio is music, a sound effect, an alarm, speech, a notification, ambience, a soundboard clip, or something else. The Java side should expose truthful audio capabilities, not application roles or playlist policy.

The normal `computercraft:speaker` remains the product surface.

## Explain behavior before code

When talking about the project, explain what happens to the Minecraft/ComputerCraft user first.

For finite files, the intended story is:

```text
ComputerCraft has a file
    -> HQ Speakers imports it into server-owned media storage
    -> the speaker starts a server-owned playback timeline
    -> each relevant Minecraft client asks for only the small encoded pieces it currently needs
    -> the client eventually decodes those pieces into positional sound
```

Only after that explanation should implementation details such as packets, classes, buffers, generation IDs, executors, or decoder internals be introduced.

## Current checkpoint

**Do not implement M1F from this handoff unless explicitly asked.**

This checkpoint is documentation/preparation only.

M1E server-authority source/tests/CI are finalized and re-reviewed.

The project owner chose not to perform the final manual M1E Minecraft acceptance run. Therefore:

- M1E has no recorded final Minecraft PASS;
- do not claim that it does;
- the project may still move on to M1F later by explicit owner decision;
- the skipped manual test is no longer a sequencing blocker unless the owner changes that decision.

M1F implementation has **not started**.

## Exact repository state to anchor from

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1e-server-authoritative-finite`

Important implementation checkpoints:

- inherited baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- frozen M1D: `4a2cd5de96228fc091226c7e72fb669b82be258c`
- original M1E semantic checkpoint: `d0e66ab9135359627086c13647d5241ad778643f`
- runtime-diagnostic Java head: `c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`
- M1E finalization code/test candidate: `38cb2a4ce2eac599c58aab9322b23a4e7667e45c`
- last code/documentation head before this documentation refresh: `ff8fc52e8660249150e056d1dff4307377afe7c4`

Known M1E finalization CI:

- run `34725651930`
- NeoForge 21.1.247: success
- NeoForge 21.1.248: success
- build/tests/package verification/artifact upload passed

The documentation refresh after `ff8fc52e...` must not be mistaken for M1F implementation.

## Target stack

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future Sound Physics Remastered target: 1.21.1-1.5.1

## User-facing Lua/API direction

Read `LUA-API.md` before changing Lua-facing behavior.

Recommended modern finite helpers:

```lua
local hq = require("hqspeaker")

hq.playFile(speaker, path, { volume = 0.6 })

local asset = hq.prepareFile(speaker, path)
local info = hq.preparedInfo(speaker, asset)
hq.playPrepared(speaker, asset, { volume = 0.6 })
hq.releasePrepared(speaker, asset)
```

Modern finite controls:

```text
audioStatus
audioPause
audioResume
audioSeek
audioSetVolume
audioSetLooping
audioStop
```

Standard CC:T functions remain native/delegated:

```text
playNote
playSound
playAudio
stop
speaker_audio_empty
```

HQ raw/feed uses `speakPCM` and its separate `hqspeaker_audio_empty` backpressure event.

## Decision: remove `audioPlayStaged()` in M1F

This was explicitly decided on 2026-09-13.

`audioPlayStaged()` is not an original HQ Speakers compatibility API.

It was introduced by this project during the old staged/local-file prototype so a temporary staged file could be played directly.

The modern model supersedes that:

```text
ComputerCraft file
    -> temporary staging/import
    -> reusable server MediaAsset
    -> prepared playback
```

Therefore, when M1F implementation is explicitly started:

**remove the old direct-staging playback command instead of carrying it into the new transport.**

New programs use `hq.playFile()` or prepare/play/release.

Do not remove it during this documentation-only checkpoint.

## M1E — what is actually implemented

The server owns the finite playback timeline.

A successful prepared play:

- starts the server clock immediately;
- does not wait for a Minecraft client to be ready;
- advances even with zero listeners;
- owns pause/resume/seek/loop/volume/EOF;
- ignores client decoder/render failure as canonical authority.

Current semantic states are:

```text
PLAYING
PAUSED
ENDED
ERROR
```

Client finite telemetry is only:

```text
READY  -> asks for a fresh current server state
ERROR  -> diagnostic only
```

The client cannot say “I ended, therefore the song ended for everyone.”

The server owns that truth.

## M1E runtime evidence boundary

The 2026-09-12 runtime logs showed the old full-file transfer completing, the client becoming READY, a fresh server state arriving, and then the old MP3 decoder failing immediately on its first PCM read.

Despite that decoder failure, server state/control continued through pause/resume/end/loop behavior.

That is useful evidence for server authority.

It is **not** a recorded final M1E PASS because the final strengthened ComputerCraft script was never run after finalization.

Do not silently upgrade this evidence into a PASS.

## Temporary decoder — do not repair during M1F

The old prepared client bridge is known-bad for the tested MP3:

- it reported the wrong duration;
- its seek code mixes incompatible units with mp3spi;
- it can claim a requested seek target even when positioning ended early;
- it can perform a redundant second seek;
- the runtime fixture ended on the first PCM read.

This bridge is disposable.

M1G owns the replacement progressive MP3/common-WAV decoder and actual audible finite path.

Do not spend M1F trying to make the old decoder sound correct.

## M1F — what it should mean to the player

M1F is about **moving large finite files intelligently**, not about decoding them yet.

Imagine a 500 MB song:

Old temporary behavior:

```text
download the entire file to the client
    -> create a local temporary file
    -> only then try to decode it
```

M1F target:

```text
server owns the whole file
    -> client asks for a small piece near the current playback point
    -> server sends that piece
    -> client keeps only a bounded temporary RAM window
    -> old pieces are discarded as playback moves on
```

Seeking from 1:00 to 8:00 should not require downloading 1:00 through 8:00. The client should request the encoded area needed around 8:00.

M1F is allowed to be silent. Audible progressive MP3/WAV belongs to M1G.

## M1F implementation requirements when explicitly started

Implementation must provide:

- bounded client-requested encoded ranges;
- arbitrary encoded offsets;
- exact requested server asset bytes;
- bounded response packets;
- bounded per-player outstanding work;
- file reads away from the server tick;
- safe asset lifetime while reads are in flight;
- stale-result discard after replacement/leave/disconnect;
- bounded client encoded RAM independent of full file size;
- no client `.part/.media` song cache in the modern path;
- a client data layer that can tell the difference between:
  - data is available;
  - data is needed but has not arrived yet;
  - true server-file EOF;
  - playback/source became stale or cancelled.

This distinction is important for M1G so temporary network starvation does not look like permanent EOF.

## M1F shutdown/lifetime requirement

The recheck before implementation found one lifecycle requirement that should be handled internally:

When M1F adds background server file reads, server shutdown must stop/drain/cancel those reads before the shared media store closes and deletes its asset files.

This is not a product choice. It is a correctness requirement for the implementation.

## M1G boundary

M1G owns actual progressive finite audio:

- MP3 decode;
- common WAV conversion;
- starvation/refill behavior;
- MP3 pre-roll/bit-reservoir handling;
- bounded PCM queues;
- cancellation;
- actual positional Minecraft/OpenAL sound.

Core finite product target:

- MP3 / MPEG Layer III
- common WAV

Later separately gated:

- native FLAC

Not final core requirements:

- OGG Vorbis
- Ogg-FLAC
- AIFF/AIF
- AU/SND
- unusual/compressed/telephony WAV
- >2-channel finite input

One physical speaker ultimately renders one mono positional source.

## Standard CC:T compatibility remains mandatory

Do not break the normal ComputerCraft speaker while building HQ features.

The exposed peripheral remains type `speaker`.

Standard behavior stays with CC:T's real `SpeakerPeripheral` wherever possible.

Do not replace native `speaker_audio_empty` semantics.

## Legacy code is not the new finite architecture

The repo still contains old inherited HQ Speakers finite/live/multispeaker code.

Examples include:

- one-shot byte APIs such as old `speakMp3`/`speakWav`;
- old whole-file/whole-PCM finite machinery;
- old streaming/HLS/TS behavior;
- old multispeaker expected-member logic.

Do not model M1F on those systems.

Useful compatibility surfaces are reviewed later in their scheduled milestones.

## Do not accidentally expand M1F

Unless directly required for M1F correctness, do not mix in:

- progressive MP3/WAV decode;
- old decoder repair;
- live/HLS/TS fixes;
- multispeaker redesign;
- Sound Physics Remastered integration;
- custom HQ block removal;
- gain/category/OpenAL cleanup;
- license/provenance resolution;
- broad legacy API cleanup unrelated to the modern prepared path.

The one explicit prototype removal already approved for M1F is `audioPlayStaged()` because it directly belongs to the obsolete modern staged-file path being replaced.

## Read order for the next chat

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
12. exact current source and current CI

Older handoffs remain useful historical context but must not override this handoff, exact current source, or newer project decisions.

## Working style for the next chat

Before implementation:

1. re-read the current branch and current CI;
2. recheck the M1F plan against actual code;
3. if a real design/correctness issue changes agreed behavior, ask the project owner **before changing implementation**;
4. handle ordinary low-level implementation details without reopening settled product decisions;
5. explain choices in normal Minecraft/ComputerCraft terms first.

Do not claim work is runtime-proven when it has only passed CI/source tests.