# Documentation index

Updated: 2026-09-19

## Start here

Current checkpoint: **M1E, M1F, and M1G are complete at source/test/CI/package level.**

Active branch: `codex/m1g-progressive-finite-decode`.

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`.

Final M1G CI: `35297026277`, green on NeoForge 21.1.247 and 21.1.248 with tests, packaged-mod verification, and artifact upload.

Focused Minecraft M1F transport acceptance remains unrecorded. Focused **M1G audible/core Minecraft acceptance is recorded PASS** on NeoForge 21.1.247 in integrated singleplayer on 2026-09-19. CI still provides the cross-build proof for both supported NeoForge targets.

## Current authority order

Read in this order:

1. `CURRENT-STATE.md` — current implementation and evidence boundary.
2. `HANDOFF-2026-09-18-M1G-COMPLETE.md` — M1G closeout record.
3. `KNOWN-ISSUES.md` — active/resolved issue inventory.
4. `TESTING.md` — current evidence matrix and runtime acceptance plan.
5. `VERIFIED-FACTS.md` — source-verified facts only.
6. `M1G-SCOPE-DECISIONS-2026-09-14.md` — implemented M1G policy/rationale.
7. `FUTURE-CLEANUP.md` — later/legacy/release parking lot.
8. `ARCHITECTURE.md` — current architecture.
9. `ROADMAP.md` — milestone sequencing.
10. `LUA-API.md` — current user-facing API.
11. `CC-T-COMPATIBILITY-CONTRACT.md` / `SERVER-CONFIG.md` / `SOURCES.md` as needed.
12. exact current source and CI.

Historical preparation/milestone/handoff documents preserve checkpoint evidence but do **not** override the current files above.

## Completed M1G contract

- protocol v7 with server-authoritative `decodeRevision`;
- semantic seek increments revision; ordinary STATE preserves a healthy decoder/window;
- stale local worker identity is invalidated before cancellation;
- STATE is the sole nonterminal transition authority; explicit STOP remains;
- fixed 32-block modern-finite delivery and attenuation radius;
- HQ volume changes gain, not core range;
- global HQ volume zero keeps canonical time running while transport/decode/render hibernates;
- client-local mute supports `canStartSilent()`;
- ordinary non-gapless local replay after physical EOF while authoritative looping remains enabled;
- real packaged-JLayer MP3 coverage across starvation/refill/window slides/pre-target discard;
- deterministic renderer-read policy coverage;
- whole-owner staging leftovers reclaimed.

M1G intentionally does not include gapless MP3, permanent-source loop engineering, dynamic volume-aware listener membership, full late-entry/rejoin lifecycle, final VS2 movement, or SPR range/acoustic integration.

## Current modern finite pipeline

```text
server MediaAsset
-> canonical server playback state
-> protocol v7 STATE decodeRevision + codec-aware anchor
-> bounded encoded ranges / sliding window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmReadAdapter
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

The modern path does not use the inherited complete-file JavaSound/mp3spi bridge.

## Open after M1G

- **Post-M1G hardening:** KI-062/063/054/064 resolved at `e836dfac702dcc438fa0366dc2fba2132b5140c1`, CI `35404646105`.
- **M1H:** late-entry/leave/rejoin/resource recovery and final moving-source/VS2 lifecycle.

Later milestones still own FLAC, multispeaker work, legacy migration, OpenAL cleanup, SPR, live streams, and release cleanup.

## Audit recheck boundary

The September 16 repository audit is supporting evidence, not authority. Exact-source rechecking retracted several first-draft claims. In particular:

- MP3 anchors contain only `(offset, seconds)`;
- modern STATE does not carry live x/y/z;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference;
- inherited HTTP streams close their streams;
- release retry ticking is `ServerMediaAssets.tickPendingReleases()`.

## Historical documents

Files named with earlier milestone/date checkpoints are historical evidence/design snapshots. They may contain pre-v7 or pre-closeout assumptions which are intentionally preserved as historical context.
