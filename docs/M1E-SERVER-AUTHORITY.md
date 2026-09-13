# M1E — server-authoritative finite playback

## Current status after 2026-09-13 reevaluation

M1E's **semantic design remains accepted and present in current source**, but the old `finalized` label is superseded by `M1E-M1F-REEVALUATION-2026-09-13.md`.

The reevaluation found exception/lifetime failure paths which mean M1E implementation hardening is reopened even though the normal-path server-authority model still looks correct.

Original semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

M1E finalization candidate:

`38cb2a4ce2eac599c58aab9322b23a4e7667e45c`

Current M1F Java/source containing the inherited M1E semantics:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

Historical CI for M1E finalization `34725651930` passed both NeoForge targets. Current M1F code-head CI `34731827907` also passed both targets.

The final manual Minecraft M1E acceptance script was prepared but not run. The owner explicitly chose to skip it, so there is no recorded final M1E runtime PASS.

## What M1E changed

M1E removed client renderer readiness/status as canonical playback authority.

A successful modern prepared finite start is conceptually:

```text
server has prepared MediaAsset
-> create playback generation
-> duration already known server-side
-> state = PLAYING
-> server clock starts immediately
-> clients are consumers/renderers, not the clock owner
```

No client is required for semantic playback to begin.

## Server semantic states

Current finite server states remain:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no canonical server LOADING state for client buffering.

## Canonical controls and EOF

Current source still makes the server authoritative for:

- position/duration;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF.

Non-looping natural EOF comes from the server clock reaching the known duration.

Exact-end semantics remain:

- non-looping `audioSeek(duration)` -> ENDED at duration;
- looping `audioSeek(duration)` -> wrap to zero and remain active.

`FinitePlaybackClockTest` covers the core clock arithmetic for these rules.

## Current protocol relationship

The authority model survived the M1F transport rewrite.

Historical M1E used protocol v4 and a temporary whole-file client bridge.

Current source uses protocol v5. STATE still carries canonical server truth and now also includes the encoded seek anchor used by M1F:

- source/media/generation;
- PLAYING / PAUSED / ENDED / ERROR;
- canonical position;
- duration;
- volume;
- looping;
- `anchorOffset`;
- `anchorTime`;
- optional server error detail.

The old M1E whole-file CHUNK/END packets are no longer current.

## Client finite status

Modern prepared finite client status remains:

- READY — asks for a fresh current server snapshot;
- ERROR — diagnostic only.

A client decoder/render failure does not canonically end or rewind server playback.

## Reevaluation finding 1 — send failures are not fully isolated from server truth

M1E's design says client delivery must not own canonical playback truth.

Current normal-path logic follows that idea, but failure handling is weaker than the design:

`HQFiniteMediaServer.sendToRelevant()` directly sends packets per player without a local runtime-exception catch.

Authoritative operations call send helpers inline. A runtime packet-send exception can therefore escape prepared start, stop, or control/state publication.

No such runtime incident has been recorded, but the source path means network projection is not yet fully isolated from canonical transition execution.

## Reevaluation finding 2 — prepared-start rollback is not atomic

Current `playPrepared()` does:

```text
retain asset
-> create Session
-> session = next
-> send BEGIN
-> send STATE
-> queue Lua state event
```

The surrounding runtime-exception catch releases the retained asset, but it does not remove the installed session or repair its asset ownership flag.

If an exception occurs after session assignment, the method can throw while leaving finite PLAYING state installed. The composite only sets STAGED_FINITE ownership after a normal successful return, so this can become an unowned/ghost finite session.

This is a failure-path correctness problem, not a change to the intended M1E semantics.

## Reevaluation finding 3 — playback asset-release retry can be lost

Current `releaseAssetReference()` clears the session's `assetReferenceHeld` flag before `MediaAssetStore.release()` succeeds.

The store intentionally preserves the entry/reference if final file deletion fails. If that happens, the session has already forgotten the still-live reference and cannot retry it.

The intended ownership model remains correct; the failure-path bookkeeping order needs hardening.

## Evidence boundary after reevaluation

What M1E evidence supports today:

- current source still implements the intended normal-path server-authority semantics;
- `FinitePlaybackClockTest` deterministically covers core clock math;
- both-target CI/package evidence exists;
- 2026-09-12 runtime diagnostics support client/server authority separation.

What remains unproven/uncovered:

- the final focused Minecraft M1E PASS was not run;
- no dedicated deterministic `HQFiniteMediaServer` state-machine test currently exists;
- packet-send failure isolation is not tested;
- prepared-start rollback atomicity is not tested;
- playback-reference release-failure retry is not tested.

Accordingly, do not describe M1E as fully finalized at the current checkpoint.

## Historical runtime diagnostic

The 2026-09-12 diagnostic reached behavior like:

```text
BEGIN
-> authoritative PLAYING STATE
-> complete old temporary transfer
-> old decoder open
-> READY
-> fresh server STATE
-> renderer submission
-> client decode failure
```

The server timeline continued independently through control behavior. That remains useful authority-separation evidence.

It is not a substitute for the skipped final script or the reopened failure-path tests.

## Decoder boundary

The old JavaSound/mp3spi bridge remains non-authoritative historical code and is no longer the current M1F prepared transport consumer.

For the tested MP3, historical evidence included:

- server analyzer: `161.304 s`;
- old JavaSound/mp3spi: `322.584 s`;
- first PCM read failure after setup;
- incompatible seek/skip unit assumptions.

M1G owns the replacement progressive decoder/audio path.

## User-facing Lua path

Recommended helpers remain:

- `hq.playFile`
- `hq.prepareFile`
- `hq.preparedInfo`
- `hq.playPrepared`
- `hq.releasePrepared`

Finite controls remain capability-oriented.

`audioPlayStaged()` was this project's prototype API and has now been removed by M1F.

## Current continuation

Read, in order:

1. `M1E-M1F-REEVALUATION-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `TESTING.md`
4. `KNOWN-ISSUES.md`
5. `M1F-IMPLEMENTATION-2026-09-13.md`
6. exact current source/CI

This file remains the M1E authority contract, with its current status corrected by the reevaluation.
