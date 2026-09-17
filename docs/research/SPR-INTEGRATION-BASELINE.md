# SPR compatibility baseline

Updated project-context note: 2026-09-17

> **Research/frozen-compatibility evidence, not current M1G implementation guidance.**
>
> Current project authority is `../CURRENT-STATE.md`, `../M1G-SCOPE-DECISIONS-2026-09-14.md`, `../KNOWN-ISSUES.md`, `../TESTING.md`, `../VERIFIED-FACTS.md`, and exact source. This file preserves the approved SPR acoustic baseline and later integration choices.

Source: `ztawfik523-lgtm/cchq-soundphysics-compat`

## Frozen acoustics

Commit: `ffcf5f6e05d85b69f1f1dff8cfae1b082b71604d`

Refs:
- `phase5-diffraction-v7-1-runtime-approved`
- `archive-phase5-diffraction-v7-1-runtime-approved`

Approved JAR SHA-256:
`30d457c2a52672f893b1076938e2fdea3f41759173dfd843ff652bd490692101`

Do not casually retune V7.1.

## Current integration boundary

M1G deliberately does **not** prebuild SPR acoustic/range behavior.

Current owner-selected M1G rules are:

- modern finite core delivery/listening radius stays fixed at 32 blocks;
- HQ finite volume changes gain/loudness, not that core radius;
- dynamic volume-aware relevance is not pulled into M1G;
- ordinary loop replay does not require special SPR continuity or a permanent OpenAL source;
- one physical speaker remains one mono positional Minecraft source.

Later SPR compatibility owns any intentional extension/adaptation of acoustic range and the corresponding server transport relevance. Keep the M1G fixed-radius policy localized so that compatibility can replace/extend it later without redesigning finite-media protocol semantics.

Do not interpret this as a decision to make SPR itself responsible for core playback correctness; M1G still must provide a correct positional Minecraft sound source before SPR integration.

## Relevant later hardening

- bounded decoder pending work;
- session invalidation clears queued work;
- stale session/source-generation rejection;
- failed decode cache cleanup;
- decoded cache count/byte limits;
- reduced encoded-payload retention;
- OpenAL native-memory cleanup;
- invalid source/buffer id guards;
- pause/resume/stopAll/emergencyShutdown lifecycle integration.

## Moving-source / VS2 note

Modern finite BEGIN already carries block coordinates. The legacy HQ client already recomputes VS2 ship-transformed source position from block coordinates each tick.

M1H may later mirror that client-side transform for modern finite playback or use explicit authoritative position updates. Do not make SPR integration depend on one of those approaches until M1H chooses it.

## Later product choice

Two reasonable packaging/maintenance shapes remain:

A. keep a companion compat jar;
B. fold compatibility into this fork as an optional isolated module.

Do not choose silently because release, maintenance, dependency, and failure-isolation tradeoffs are real.
