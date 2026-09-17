# Documentation index

Updated: 2026-09-17

## Start here

Current checkpoint: **M1E + M1F source/test/CI/package complete; M1G progressive decode/render integrated in source and still in correctness/evidence work.**

Active branch: `codex/m1g-progressive-finite-decode`.

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation/audit commits after that checkpoint do not change implementation source.

Focused Minecraft M1F transport acceptance is unrecorded. Focused audible M1G Minecraft acceptance is also unrecorded.

## Current authority order

Read in this order:

1. `CURRENT-STATE.md` — current implementation and selected target behavior.
2. `M1G-SCOPE-DECISIONS-2026-09-14.md` — owner-selected M1G scope, updated with the September 17 audit/rethink conclusions.
3. `KNOWN-ISSUES.md` — active/resolved issue inventory.
4. `TESTING.md` — current evidence matrix and acceptance plan.
5. `VERIFIED-FACTS.md` — source-verified facts only.
6. `FUTURE-CLEANUP.md` — later/legacy/release parking lot; not permission to expand M1G.
7. `ARCHITECTURE.md` — current architecture and selected near-term design.
8. `ROADMAP.md` — milestone sequencing.
9. `LUA-API.md` — current user-facing API surface/caveats.
10. `CC-T-COMPATIBILITY-CONTRACT.md` / `SERVER-CONFIG.md` / `SOURCES.md` as needed.
11. exact current source and CI.

Historical preparation/milestone/handoff documents preserve checkpoint evidence but do **not** override the current files above.

## Current M1G policy

Selected and no longer open unless the owner explicitly changes scope:

- explicit server-authoritative decoder/re-anchor revision, likely protocol v7; current source remains v6;
- fixed 32-block modern-finite core listening/delivery radius;
- HQ volume changes gain, not core range;
- global HQ volume zero keeps canonical server time advancing but hibernates local decode/render/range work;
- looping is ordinary replay after local EOF while authoritative looping remains enabled; a normal restart gap is acceptable;
- no gapless MP3/LAME padding work, permanent-source loop engineering, dynamic volume-aware range, or SPR range/acoustics in M1G;
- future SPR compatibility owns intentional extended range/acoustics and matching transport relevance.

One implementation-shape choice remains: retain finite CONTROL packets only as optional latency hints or let STATE become the sole transition authority in v7. Correctness must not depend on packet ordering either way.

## Current issue summary

M1G completion:

- **KI-053 / KI-056 / KI-057:** decoder window/cancellation/reanchor cluster; solve coherently with explicit revision.
- **KI-058 / KI-060:** fixed attenuation + renderer-start/volume-zero behavior.
- **KI-051:** selected ordinary replay not implemented.
- **KI-055:** real-MP3 progressive integration and focused renderer-adapter evidence incomplete.
- **KI-061:** per-speaker staging leftovers.

Cross-cutting verified findings:

- **KI-062:** synchronized dynamic stream dispatch can hold the composite monitor across blocking DNS while server tick or synchronized cleanup waits on that monitor.
- **KI-063:** failed/rejected RAW or prepared replacement can destroy valid current playback before replacement admission succeeds.
- **KI-054:** shutdown can lose deletion retry state or fail before media-store close, leaving the root lock/registry alive in the JVM.
- **KI-064:** media import can spin indefinitely on repeated zero reads and lacks an unsupported-atomic-move fallback.

The latest practical sequencing suggestion is KI-062 first, then the M1G v7 decoder/reanchor cluster. A broader safety-first KI-062/063/054/064 batch is also defensible; record that sequencing choice explicitly if used.

## Current integrated M1G pipeline

```text
server MediaAsset
-> canonical server playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded encoded ranges / sliding window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

The modern path does not use the inherited complete-file JavaSound/mp3spi bridge.

## Audit recheck boundary

The September 16 repository audit is supporting evidence, not authority. Exact-source rechecking confirmed KI-062/063/064 and strengthened KI-054, but retracted several claims. Current docs preserve the corrected forms.

Do not repeat as facts that:

- MP3 anchors have hidden frame/skip metadata — they do not; anchor is `(offset, seconds)`;
- STATE carries live x/y/z — it does not;
- `audioPrepareStaged(...)` holds the composite monitor — it does not;
- `HQSpeakerPeripheral` has a composite back-reference — it does not;
- the Level-keyed WeakHashMap is accidentally defeated — source explicitly relies on lifecycle eviction because values reference Level;
- inherited HTTP streams are unclosed — those paths close their streams.

## Historical documents

Files named with earlier milestone/date checkpoints (M0, P0, M1A-F, PRE-*, HANDOFF-*, `CHAT-HANDOFF-2026-09-12.md`, and `M1G-DESIGN-DECISIONS-2026-09-13.md`) are historical evidence/design snapshots.

They may contain choices or assumptions which were correct at that checkpoint and later superseded. In particular, old loop L1/L2/L3 choices, dynamic-range alternatives, pre-v7 reanchor options, and statements that M1G decoder/render integration had not happened are historical only.

## Research documents

`research/` stores transferable/frozen research evidence. It does not override current project decisions.

The current SPR direction is: preserve the frozen acoustic baseline, keep M1G fixed-range, and let later SPR compatibility own any intentional range/acoustic extension and matching transport relevance.

## Evidence boundaries

- M1E final focused Minecraft acceptance: skipped/unrecorded.
- M1F focused Minecraft transport acceptance: unrecorded.
- M1G integrated source/tests/package: green at `957832348eaa6e497282d923f2312c9c7d7c550f`.
- M1G real-MP3 progressive integration coverage: incomplete.
- M1G focused renderer-adapter coverage: incomplete.
- M1G ordinary replay: selected, not implemented.
- M1G audible Minecraft PASS: unrecorded.
