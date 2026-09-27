# Testing

Updated: 2026-09-27

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does not prove real Minecraft/OpenAL behavior.

Runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Build baseline: NeoForge 21.1.247 only.

## Evidence already passed

A1-A19 and R1-R9 are real-runtime PASS on the current candidate.

C3 radio/membership is also PASS: sustained grouped radio, metadata, strict late membership, rerun membership, singular and indexed radio all completed.

## Target failures and what they actually mean

### C1

The movement itself worked. Attempt 7 measured roughly 52-53 blocks of both requested and actual OpenAL movement with sources healthy at the end.

The runner's generic `playingToStoppedTransitions == 0` assertion is inappropriate for a long Sable movement scenario which can cross the listener relevance boundary and legitimately detach/rejoin.

### C2

Current diagnostics record Sound Physics environment writes. Attempt 7 saw those writes but no open/wall gain or HF change.

This is insufficient to decide whether HQ audio fully entered SPR's `processSound`/ray path. Add direct process-call evidence before changing product code.

### C4

Eight finite endpoints exposed a transport-scale stall. One source started ~2.219 seconds after the other seven.

The source contract currently permits each client source 2 pending range requests but admits only 4 requests per player server-side; over-limit requests are silently dropped and only retried after a 2-second client request expiry.

C4 RAW did not run because the finite assertion stopped that scenario.

## Required deterministic regression coverage before rerun

Add tests for:

- finite range admission under multiple simultaneous endpoint clients;
- no silent `OVER_LIMIT` request that remains client-pending until timeout;
- bounded fairness/progress for at least 8 endpoints sharing one player;
- C1 allowing expected relevance detach/rejoin while still requiring healthy final tracking;
- C4 finite and RAW subchecks completing/reporting independently;
- SPR diagnostics distinguishing `setEnvironment` observation from actual `processSound` execution.

## Next runtime evidence

After fixes:

1. C1 focused rerun for a clean harness PASS;
2. C2 static-world SPR proof with direct `processSound` evidence;
3. C2 Sable-geometry comparison if Sable-world obstruction matters to the target;
4. C4 exactly-8 finite rerun;
5. C4 8+ RAW;
6. explicit >8 source-capacity test only if >8 simultaneous playback is a required product target.

The master command remains:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

Use `RUNTIME-INVESTIGATION-2026-09-27.md` for the forensic evidence behind these changes.
