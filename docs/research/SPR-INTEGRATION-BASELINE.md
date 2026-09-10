# SPR compatibility baseline

Source: `ztawfik523-lgtm/cchq-soundphysics-compat`

## Frozen acoustics
Commit: `ffcf5f6e05d85b69f1f1dff8cfae1b082b71604d`

Refs:
- `phase5-diffraction-v7-1-runtime-approved`
- `archive-phase5-diffraction-v7-1-runtime-approved`

Approved JAR SHA-256:
`30d457c2a52672f893b1076938e2fdea3f41759173dfd843ff652bd490692101`

Do not casually retune V7.1.

## Relevant later hardening
- bounded decoder pending work
- session invalidation clears queued work
- stale session/source-generation rejection
- failed decode cache cleanup
- decoded cache count/byte limits
- reduced encoded-payload retention
- OpenAL native-memory cleanup
- invalid source/buffer id guards
- pause/resume/stopAll/emergencyShutdown lifecycle integration

## Later product choice
A. keep a companion compat jar
B. fold it into this fork as an optional isolated module

Do not choose silently because release/maintenance tradeoffs are real.
