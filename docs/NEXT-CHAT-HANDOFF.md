# CC:HQ Speakers — next-chat handoff

Updated: 2026-09-19

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

## Current checkpoint

M1G is complete at source/test/CI/package/component level.

Final M1G source checkpoint:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Final M1G CI:

`35297026277`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Artifacts:

- 21.1.247: artifact `10527498657`, SHA-256 `3b873edd94a924cf3922a75cdd059c2b3963500787bd479ec8b386600ef055b1`;
- 21.1.248: artifact `10528645404`, SHA-256 `3b9304a509d37bf2ef1c0797d4449fa2cb6c8cc705678e63a68ed8c2ef72f8b1`.

A separate focused **M1G audible/core Minecraft runtime PASS was recorded on 2026-09-19** using NeoForge 21.1.247 integrated singleplayer, resolving KI-046. NeoForge 21.1.248 remains CI/package verified but was not manually runtime-tested in that session.

## Read first

1. `CURRENT-STATE.md`
2. `HANDOFF-2026-09-18-M1G-COMPLETE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `ROADMAP.md`
7. exact current source/CI

Historical handoffs/preparation docs preserve earlier checkpoints and do not override current records.

## Final M1G architecture

```text
server MediaAsset
-> canonical server playback state
-> protocol v7 STATE decodeRevision + codec-aware anchor
-> M1F bounded ranges / sliding encoded window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmReadAdapter
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

Locked A1/B1/C1/D1/E1 decisions remain unchanged.

M1G implemented:

- server-authoritative decoder/reanchor revision;
- semantic seek increments revision;
- ordinary STATE preserves healthy local decode state;
- stale worker identity invalidates before cancellation;
- STATE-only nonterminal transition authority; explicit STOP remains;
- fixed 32-block core server relevance and live channel attenuation;
- HQ volume changes gain rather than radius;
- global-volume-zero transport/decode/render hibernation while canonical time continues;
- client-local mute support through `canStartSilent()`;
- renderer activation/loss recovery via authoritative rejoin;
- ordinary non-gapless loop replay;
- real packaged-JLayer MP3 test coverage across starvation/refill/sliding/pre-target discard;
- pure renderer-read policy tests;
- whole-owner staging cleanup.

Resolved M1G issues: KI-051, KI-053, KI-055, KI-056, KI-057, KI-058, KI-060, KI-061. KI-059 was already a resolved product decision.

## Next engineering work

M1G should not be reopened merely because these remain:

- **KI-062:** legacy stream DNS can block while the composite monitor is held, making server tick/lifecycle cleanup wait.
- **KI-063:** RAW/prepared replacement can stop valid current playback before replacement admission succeeds.
- **KI-054:** shutdown can lose deletion retry state or leave media-store root lock/registry alive after an earlier close failure.
- **KI-064:** `MediaAssetStore.writeExact()` can spin on repeated zero reads and import lacks an unsupported-`ATOMIC_MOVE` fallback.
- **M1H:** late entry, proactive leave, return/rejoin, dimension/resource-reload recovery, general underrun recovery, and final VS2 moving-source lifecycle.

There are meaningful sequencing tradeoffs among the post-M1G hardening items. Keep them explicit rather than silently folding all of them into one milestone.

## M1H / VS2 boundary

Modern BEGIN carries initial world position and block coordinates. Modern STATE does not carry live x/y/z.

`FiniteSpeakerSound.updatePosition(...)` exists, but the modern finite client does not drive it after renderer creation. M1H may either mirror the inherited client-side VS2 transform from BEGIN block coordinates or add explicit authoritative position updates if later requirements justify it.

## Evidence boundary

- M1E source/test/CI: PASS; final focused Minecraft acceptance skipped/unrecorded.
- M1F source/test/CI/package/component: PASS; focused Minecraft transport acceptance unrecorded.
- M1G source/test/CI/package/component: PASS at `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`.
- M1G focused audible/core Minecraft PASS: recorded 2026-09-19 on NeoForge 21.1.247 integrated singleplayer; KI-046 resolved.

Historical Lua scripts are not substitutes for the modern prepared-path runtime checklist.
