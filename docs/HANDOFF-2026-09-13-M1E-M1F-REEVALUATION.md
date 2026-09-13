# CC:HQ Speakers — M1E/M1F reevaluation handoff

Date: 2026-09-13

## Read this first

The current implementation branch is:

`codex/m1f-demand-driven-finite`

Current Java/source code head remains:

`934e74b8ff619178d703f73df8a16ee97b3fc2af`

No Java was changed during the reevaluation.

The previous documentation head was:

`d748287fe2a5bfa38fe5a3f1bad2a864fc7fc591`

The detailed audit is:

`M1E-M1F-REEVALUATION-2026-09-13.md`

## Current status in one paragraph

M1E's server-authoritative semantic design still looks right and M1F's client-pulled bounded range architecture still looks right. Both target NeoForge CI matrices were green at the recorded checkpoints. However, the previous `M1E finalized` / `M1F source-test-CI complete` wording is now superseded: source review found shared exception/lifetime correctness gaps, M1F's encoded window lacks the intended sliding consume/discard operation, and the original M1F deterministic acceptance matrix is only partially covered by current tests. M1E's final manual Minecraft PASS remains skipped/unrecorded; M1F has no recorded Minecraft runtime acceptance.

## Do not start M1G yet

The owner previously asked to be consulted if a rethink finds something wrong in the implementation. The reevaluation did.

Before Java changes, the owner must choose how much hardening to do now.

### Option 1 — finish M1E/M1F properly before M1G

Fix all reopened issues and add the missing deterministic coverage before decoder work.

Tradeoff: cleanest milestone boundary, most immediate work.

### Option 2 — fix shared correctness now, fold buffer/test completion into M1G entry

Fix packet-send isolation, playPrepared rollback, and reference-release retry semantics now. Keep M1F provisional; make sliding-window + remaining acceptance proof the first M1G substage.

Tradeoff: smaller immediate patch, but M1F stays deliberately unaccepted until early M1G hardening completes.

### Option 3 — documentation-only defer

Leave Java unchanged and carry all findings forward.

Tradeoff: smallest immediate change, but M1G would build on known lifetime/exception risks and a weaker transport-consumer boundary.

Do not choose on the owner's behalf.

## Reopened shared M1E/M1F issues

1. Network sends are not isolated from authoritative transitions. A runtime packet-send failure can escape start/stop/control/state paths even though clients should not govern server truth.
2. `playPrepared()` installs the new session before BEGIN/STATE sends; a later runtime exception releases the asset but can leave the installed session/ownership flag inconsistent, including a ghost-session possibility at the composite layer.
3. `releaseAssetReference()` clears its held flag before `MediaAssetStore.release()` succeeds. A final-file deletion failure leaves the store reference alive but removes the session's retry knowledge.
4. M1F in-flight range release has a related rare failure path: failed final release is reported, but no retry owner survives after the task exits.

## Reopened M1F completeness/testing issues

- `FiniteRangeWindow` can reset/re-anchor but cannot slide/advance while preserving useful unread prefetched bytes.
- Current M1F-specific tests cover the range window and range read service, not the complete original acceptance matrix.
- Missing deterministic coverage includes full server request identity validation, relevance/dimension/stale-completion behavior, shutdown ordering, packet bounds/codec integration, client anchor gating, and consume/discard progression.
- No focused real Minecraft M1F transport run has been recorded.

## Things that remain correct and should not be casually reopened

- Lua decides application meaning; Java should not invent music/effect/notification roles.
- normal CC:T speaker behavior remains delegated/native where possible.
- server MediaAssets remain immutable UUID-addressed encoded sources.
- M1E server time/state remains canonical; client READY/ERROR is non-authoritative.
- M1F protocol v5 range identity remains source + generation + asset + offset/length.
- M1F modern path must not restore `.part/.media` client whole-song caching.
- `audioPlayStaged()` stays removed.
- M1F can remain silent; M1G owns progressive MP3/common-WAV decode and rendering.
- M1H still owns full late-entry/leave-return listener lifecycle.

## Evidence boundaries

M1E:

- server-authority semantics implemented;
- clock math unit-tested;
- historical diagnostic supports authority separation;
- both-target CI/package evidence exists;
- final focused Minecraft PASS not run.

M1F:

- range architecture implemented;
- window/read-service unit tests green;
- both-target CI/package evidence exists;
- original acceptance matrix not fully covered;
- Minecraft runtime transport acceptance not run.

## Recommended read order

1. `M1E-M1F-REEVALUATION-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1F-IMPLEMENTATION-2026-09-13.md`
8. `M1E-FINITE-STREAMING-DESIGN.md`
9. `ROADMAP.md`
10. `LUA-API.md`
11. exact current source and CI

Earlier M1E finalization and M1F implementation documents remain historical evidence, but their old completion labels do not override this reevaluation.
