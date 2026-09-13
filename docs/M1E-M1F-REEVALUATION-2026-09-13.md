# M1E + M1F reevaluation — 2026-09-13

> **Historical audit with current resolution:** this document records the reevaluation which reopened M1E and M1F after earlier completion wording proved too strong. The M1E findings identified here were subsequently fixed and revalidated. For current status, read `M1E-FINAL-HARDENING-2026-09-13.md` and `CURRENT-STATE.md` first.

## What the reevaluation established

The architecture remained sound:

```text
M1E: server owns finite playback truth
    -> M1F: client requests bounded encoded ranges from server assets
    -> M1G: decoder consumes those ranges into bounded PCM/rendering
```

The audit did not find a reason to revert server authority, immutable MediaAssets, client-pulled range transport, protocol-v5 range identity, or the M1G milestone split.

It did find that the then-current completion labels were too broad.

## M1E findings found by the audit

The audit reopened M1E for four concrete failure-path issues:

1. client packet-send exceptions were not fully isolated from canonical server transitions;
2. prepared start could install a session before later throwable projection work, allowing a ghost-session failure path;
3. failed final MediaAsset release could leave a live reference without a retry owner;
4. a terminal server `ERROR` could leave the playback clock advancing.

The audit also noted that deterministic coverage focused on the lower-level clock rather than the complete production semantic state-machine/failure boundaries.

## M1E resolution

Those M1E findings are now closed.

Hardening sequence:

- `3b3747cdea8fa5fd22f01c28fab2458dd99146a0` — server-authority and asset-lifetime hardening;
- `3645d5bfcc40f9e6766a48cf1d5fc10de71bd776` — projection/build hardening;
- `c8a1af1b373128212fe43bde6e5ad2c543bc8e4e` — close failure-path acceptance gaps;
- `521d4323d9216c8a99e8ec60426997c3330c4068` — isolate broadcast projection failure per recipient.

Final exact code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build, complete project tests, package verification, and artifact upload.

M1E now has:

- production `FinitePlaybackStateMachine` deterministic coverage;
- canonical ERROR position freeze;
- pre-install prepared-start construction/rollback safety;
- best-effort client projection which cannot abort server truth;
- independent per-recipient broadcast failure isolation;
- retry-safe MediaAsset release ownership;
- range-IO/store shutdown ordering compatible with that lifetime model.

The final strengthened Minecraft M1E acceptance script is still **skipped / no recorded PASS** by explicit owner decision. That runtime-evidence gap was not rewritten as a PASS.

## M1F findings which remain current

The audit also reopened M1F completeness/acceptance. Those findings are still separate active work.

### Encoded window is re-anchorable, not yet a clean sliding consumer

`FiniteRangeWindow` can reset to arbitrary encoded offsets, request/accept/probe/copy/retry/cancel, and remain bounded. It does not yet expose a consume/discard/advance operation which slides forward while retaining useful unread prefetched bytes.

The protocol does not require redesign; the client consumer boundary needs strengthening before M1G relies on it for continuous progressive refill.

### Original M1F deterministic acceptance matrix is not fully covered

Existing tests prove important range-window/read-service behavior, but dedicated component coverage remains incomplete for the full server request identity/relevance/stale-completion path, shutdown integration, packet bounds/codecs, client BEGIN/STATE anchor gating, and true sliding progression.

### M1F focused Minecraft transport acceptance is not recorded

No focused real client/server M1F range-transport PASS is currently recorded.

M1F is also intentionally allowed to be silent; M1G owns progressive decode and audible rendering.

## Status labels after resolution

Use these labels now:

```text
M1E semantic design: complete
M1E source implementation/hardening: complete
M1E deterministic tests: green
M1E both-target CI/package: PASS
M1E final manual Minecraft acceptance: skipped / no recorded PASS

M1F range architecture: implemented
M1F source/test acceptance: provisional / completion work remains
M1F focused Minecraft transport acceptance: not recorded

M1G: not started
```

The old "decision required before Java changes" gate in the original reevaluation no longer applies to M1E. The owner chose to finish M1E, and that work is now done.

## What remains intentionally outside M1F

Full late-listener discovery, proactive leave-range cleanup, return/rejoin, dimension/resource-reload recovery, and underrun rejoin remain M1H.

Progressive MP3/common-WAV decode, PCM queues, Layer III pre-roll, and positional rendering remain M1G.

## Current continuation

Read, in order:

1. `M1E-FINAL-HARDENING-2026-09-13.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `M1E-SERVER-AUTHORITY.md`
7. this audit for historical rationale
8. `M1F-IMPLEMENTATION-2026-09-13.md`
9. exact current source/CI
