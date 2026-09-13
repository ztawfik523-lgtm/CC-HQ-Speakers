# Historical M1E/M1F reevaluation — 2026-09-13

> **Resolved checkpoint notice:** this document records the reevaluation that reopened M1E/M1F failure-path and acceptance work. Those implementation findings are now resolved by the final M1E/M1F passes. For current status read `M1F-FINALIZATION-2026-09-13.md`, `M1E-FINAL-HARDENING-2026-09-13.md`, and `CURRENT-STATE.md` first.

## What the reevaluation found

The audit correctly identified four important areas:

1. M1E client/network projection failures needed stronger isolation from canonical server state.
2. Prepared-start and MediaAsset release failure paths needed retry-safe/atomic ownership handling.
3. M1F's range architecture needed a true sliding consume/discard client window rather than reset-only progression.
4. M1F's deterministic acceptance matrix needed stronger proof for identity/relevance/lifetime/shutdown/anchor/bounded-progress behavior.

It also correctly preserved the architectural direction:

```text
server MediaAsset
-> server-authoritative timeline
-> authoritative STATE + encoded anchor
-> bounded client range requests
-> bounded off-thread server reads
-> bounded client encoded RAM
-> later progressive decoder/renderer
```

The reevaluation never justified returning finite authority to clients or restoring whole-file transport.

## Resolution

### M1E

Resolved by final hardening code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

CI:

`34757923455`

Resolved items include:

- best-effort/per-recipient projection isolation;
- atomic prepared-start installation boundary;
- retry-safe MediaAsset release ownership;
- server ERROR position freeze;
- deterministic production state-machine/failure-path coverage.

Final focused Minecraft M1E acceptance remains skipped/unrecorded by explicit owner decision.

### M1F

Resolved by final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

CI:

`34763362365`

Resolved items include:

- unanchored window before authoritative STATE;
- forward sliding consume/discard preserving unread prefetch;
- repeated bounded refill beyond one window;
- shared range/source/asset/generation validation;
- current relevance validation;
- exact off-thread range reads;
- in-flight asset lifetime and cancellation cleanup;
- failed-release retry ownership;
- retryable shutdown timeout without premature store close;
- integrated fake server-reader -> client-window proof.

Focused real-Minecraft M1F transport acceptance remains unrecorded. M1F audibility was never required.

## Current conclusion

The reevaluation's implementation blockers are closed at source/test/CI/package level.

M1G is the next implementation milestone. Full dynamic listener lifecycle remains M1H.

Do not use this historical document to reopen M1E/M1F unless new exact source/test/runtime evidence reveals a defect.
