# Current state

## Current checkpoint

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at the **source/test/CI/package** level.

M1G is **prepared but not started**. No M1G Java/resource behavior has been added.

Preparation branch:

`codex/m1g-preparation`

Preparation base/current M1F documentation head:

`8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, package verification, and artifact upload.

Baseline 21.1.247 M1F JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

## Fresh M1F re-verification before M1G preparation

The documentation-head verification run `34763711105` ultimately completed green on both NeoForge targets.

At the owner's request it was run again. Fresh rerun jobs:

- NeoForge 21.1.247 job `103742611713`: PASS;
- NeoForge 21.1.248 job `103742612481`: PASS.

Both rerun jobs passed build, the full test suite, packaged-mod verification, and artifact upload.

This is still not a focused Minecraft runtime PASS.

## Current finite architecture

```text
ComputerCraft file
    -> immutable server MediaAsset
    -> server-authoritative finite timeline (M1E)
    -> server-selected encoded anchor
    -> bounded client-requested encoded ranges (M1F)
    -> bounded sliding client encoded RAM
    -> progressive decoder/converter worker (M1G)
    -> bounded mono PCM
    -> one positional renderer per physical speaker
```

## M1E evidence boundary

M1E source/test/CI is complete at `521d4323d9216c8a99e8ec60426997c3330c4068`.

Its final focused Minecraft acceptance was explicitly skipped by the owner, so there is no recorded final M1E runtime PASS.

Server remains canonical owner of finite PLAYING/PAUSED/ENDED/ERROR, time, seek, loop, volume, and natural EOF.

## M1F evidence boundary

M1F source/test/CI/package and deterministic/component acceptance are complete.

Current modern transport has:

- protocol v5 range request/data;
- authoritative STATE-selected encoded anchors;
- first demand gated on STATE;
- 128 KiB maximum range;
- 512 KiB client encoded window;
- bounded per-player outstanding work;
- bounded background server reads;
- in-flight MediaAsset lifetime/retry safety;
- stale request/completion discard;
- arbitrary re-anchor;
- forward sliding/discard preserving unread overlap;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- no modern complete-song `.part/.media` client cache;
- no modern finite CHUNK/END whole-file packets;
- no `audioPlayStaged()` route.

Focused real-Minecraft M1F transport acceptance is not recorded. M1F audibility was not required.

## M1G preparation findings

Exact current source was rechecked before preparation.

### Modern insertion point

`HQFiniteMediaClient` is currently transport-only: BEGIN/state identity, `FiniteRangeWindow`, range pumping, anchor state, and terminal cancellation. It has no modern decoder, PCM queue, or renderer.

M1G should build on that boundary rather than reuse the inherited finite engine.

### Old finite code is not the M1G engine

- `FileFiniteAudioStream` requires a complete local file.
- `HQAudioStream` finite mode decodes the complete finite payload to a retained `FiniteAudioTrack`.
- `FiniteAudioTrack` stores whole decoded PCM in RAM.

Those violate the M1G bounded-duration-independent target.

### MP3 dependency fact

The project already packages JLayer `1.0.1.4`. Inherited live MP3 code proves frame-by-frame JLayer use works in this dependency/package path.

M1G still needs a starvation-aware input bridge over the M1F window so temporary `NEED_DATA` never becomes decoder EOF.

### WAV gap

Current `FiniteMediaAnalyzer` accepts historical WAV shapes broader than the final M1G target. Current `MediaMetadata` does not contain a final normalized common-WAV layout descriptor.

M1G must narrow active prepared/local WAV support to the implemented mono/stereo common PCM/float subset.

## M1G decision gates

M1G implementation is blocked on three owner choices documented with tradeoffs in `PRE-M1G-PREPARATION.md`:

1. **Renderer:** normal Minecraft `AudioStream`/SoundManager vs direct Channel/OpenAL queue.
2. **WAV layout:** server-normalized layout carried over the wire vs client progressive container parsing.
3. **Sample rate:** preserve source sample rate vs normalize finite PCM to 48 kHz.

Do not silently choose among these.

## M1G non-negotiable requirements

Regardless of those choices:

- M1F encoded data stays bounded;
- decoded PCM stays bounded independently of track duration;
- no network/disk/decode blocking on Minecraft or audio threads;
- temporary encoded/PCM starvation is not physical EOF;
- MP3 seek/rejoin includes Layer III pre-roll;
- seek/replacement/stop invalidates stale decoder + PCM + renderer state;
- same coarse encoded anchor does not imply same decoder/audible seek state;
- renderer failure remains local/diagnostic and cannot own server EOF;
- one physical speaker remains one mono positional source;
- standard CC:T speaker behavior remains foundational.

## M1H boundary

Full late-entry, proactive leave cleanup, return/rejoin, dimension/resource reload recovery, robust underrun rejoin, and final VS2 listener lifecycle remain M1H.

## Documentation drift warning

Two transitional paragraphs in `ARCHITECTURE.md` are stale after M1F finalization:

- current active max range is 128 KiB, not the older 256 KiB starting-point statement;
- modern prepared transport no longer uses whole-file push/client `.part/.media` bridging.

Current source, `M1F-FINALIZATION-2026-09-13.md`, and the pre-M1G docs override those old paragraphs.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / no recorded PASS
M1F focused Minecraft transport acceptance: not recorded
M1G implementation: not started
M1G audible runtime PASS: not applicable yet
```

Green CI is not runtime proof.

## Current read order

1. `HANDOFF-2026-09-13-PRE-M1G.md`
2. `PRE-M1G-PREPARATION.md`
3. `M1F-FINALIZATION-2026-09-13.md`
4. `CURRENT-STATE.md`
5. `KNOWN-ISSUES.md`
6. `TESTING.md`
7. `VERIFIED-FACTS.md`
8. `ROADMAP.md`
9. `M1E-FINITE-STREAMING-DESIGN.md`
10. `LUA-API.md`
11. exact current source and current CI
