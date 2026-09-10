# Current state

## Reviewed reference

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Reviewed M1 branch/reference:

`codex/m1-player-core`

`fba84a33a94d451af09b983bcb04416c97ff64cf`

Untouched inherited fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

The mod replaces/upgrades the normal CC:Tweaked speaker peripheral. It should be treated as a **programmable ComputerCraft audio device**, not a built-in music player.

Audio is separated by technical capability:

- raw/feed PCM: no finite timeline;
- finite files: known timeline and finite controls;
- live streams: open-ended remote sources.

Application roles such as music/effect/notification/alarm belong to Lua programs.

## Build/CI state

The reviewed M1 HEAD builds successfully in GitHub Actions for:

- NeoForge 21.1.247
- NeoForge 21.1.248

The workflow uses Java 21 and verifies the packaged mod contains:

- `META-INF/neoforge.mods.toml`
- `hqspeaker.mixins.json`
- jarjar metadata
- mp3spi 1.9.5.4
- JLayer 1.0.1.4
- Tritonus share 0.3.7.4

This proves build/package compatibility, not client playback correctness.

During P0, an initial test-only commit failed `compileTestJava` because direct tests of Minecraft-bound packet classes could not resolve `CustomPacketPayload` from the ordinary unit-test source set. Production `compileJava` completed first. Those packet-constructor tests were removed in favor of keeping the pure unit suite independent from Minecraft-generated classes; protocol rules remain scheduled for extracted/component tests.

## M1 finite-media implementation

Current M1 finite media has:

- monotonically increasing per-speaker generations;
- retained decoded signed-16-bit mono PCM;
- exact decoded sample rate;
- frame-aligned independent renderer cursors;
- natural finite EOF;
- duration from frame count/sample rate;
- seek over retained PCM without re-decode;
- loop by retained cursor rewind;
- server semantic states;
- client READY/STARTED/PAUSED/RESUMED/SEEKED/ENDED/ERROR reports;
- real `Channel.pause()` / `Channel.unpause()`;
- live finite volume through Minecraft BLOCKS/MASTER scaling;
- F3+T renderer re-prime logic;
- VS2 position updates.

This is a useful base and should not be discarded.

## Standard CC:T compatibility state

The fork still reports peripheral type `speaker`, but the reviewed code does **not** fully implement CC:T 1.120.0 speaker semantics.

Known mismatches include:

- `playNote` ignores the selected instrument and synthesizes a sine wave;
- its Java signature makes volume/pitch required rather than optional;
- `playSound` ignores the requested sound name;
- standard `stop()` is absent;
- `playAudio` does not preserve CC:T's single-pending-buffer/backpressure behavior;
- omitted `playAudio` volume uses HQ default volume instead of previous `playAudio` volume;
- `speaker_audio_empty` is driven by the server dispatch queue and can fire repeatedly while idle.

The exact CC:T 1.120.0 source and its public docs disagree on the omitted `playNote` pitch default. That discrepancy is recorded explicitly in `CC-T-COMPATIBILITY-CONTRACT.md` rather than silently reconciled.

## Major source-proven blockers

### Raw/finite desynchronization

A raw packet causes the client to reset finite playback state, while the server raw API does not clear its finite semantic queue. The two sides can disagree permanently.

### Raw lifecycle/backpressure

The server queue is a packet-dispatch queue, not an audible raw buffer. It is currently used as though it were CC:T's raw backpressure buffer. Client raw playback may also keep a streaming source alive with silence after data drains.

### Stop/control recipient ownership

Audio is sent to players within speaker range at dispatch time. Later stop/control packets are also sent only to players currently in range, so a player can receive playback, walk away, and miss the stop/control.

### Finite loop clock

Turning looping off after one or more wraps does not rebase the wrapped logical position before changing the clock interpretation.

### Multi-client finite authority

The first successful renderer becomes the anchor and there is no anchor failover. A later generation STARTED report can also promote past an earlier generation which another client is still playing.

### No-renderer finite state

Finite media is one-shot delivered. With no successful nearby renderer, semantic state can remain loading indefinitely.

### Decoder backlog

Finite decode uses a single-thread executor with an unbounded work queue. Stale generation results are rejected, but stale queued work is not cancelled.

### Stream correctness

Current stream code has source-proven problems:

- stream volume is applied to decoded PCM and again in the Minecraft sound;
- HLS live refresh uses a persistent list index rather than media-sequence progression;
- direct TS demux collects frames until EOF before queueing playback;
- unsupported TS audio can fall back to returning compressed bytes as PCM;
- stream server state does not reflect actual renderer/network failure;
- stream pause/reconnect-resume is not implemented.

### Multi-speaker sync

Expected group size is global while packet delivery is range-local. A client receiving only part of a group may wait for members it can never receive.

### Provider cache lifecycle

`HQSpeakerPeripheralProvider` caches by dimension+position but stores a concrete `Level`; an eviction method exists but has no current call path in the reviewed source.

## Test state

Before P0, the Java test suite consisted only of `FiniteAudioTrackTest`.

P0 prep adds:

- finite cursor/end/loop-disable edge coverage;
- pure HLS parser tests;
- a runtime CC:T compatibility acceptance script;
- a finite lifecycle regression script;
- an explicit test matrix mapping every blocker to an automated or runtime proof.

Direct packet-constructor tests were attempted and then deliberately removed after CI proved the ordinary unit-test source set lacks Minecraft's generated packet classes. Packet/status validation remains a future extracted/component test target.

Many source-proven defects are intentionally not "tested green" yet. Tests should encode the desired contract before those fixes land.

## Next milestone

`P0 — implementation readiness`

Exit criteria:

- repository docs describe current source truth;
- exact CC:T speaker compatibility contract is documented;
- the four material architecture choices have explicit options/tradeoffs and user decisions;
- regression harness exists for compatibility and current defect classes.

Only after P0 should broad stabilization implementation proceed.
