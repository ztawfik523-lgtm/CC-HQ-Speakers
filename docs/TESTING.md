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

The product movement evidence was already good. The runner has now been corrected to accept expected relevance leave/rejoin history while requiring final PLAYING health and real movement.

### C2

The old evidence was insufficient because it observed only `setEnvironment` and used a Sable wall.

The branch now records the exact SPR `processSound` call for HQ custom sounds and C2 uses ordinary world geometry. It also keeps the same sound running while moving behind the wall before doing a fresh behind-wall restart, so stale long-running behavior is automatically distinguished from startup failure.

### C4

The demonstrated range-admission mismatch has a source fix: all finite sessions on one client share the same four-request per-player budget enforced by the server, with fair request-slot assignment.

The runner also guarantees RAW runs independently from finite.

## Required deterministic regression coverage before rerun

Implemented/covered in source tests and CI:

- Lua syntax/runner invariants for corrected C1;
- C4 finite/RAW independent-subcheck structure;
- direct SPR process hook presence;
- HQ-only SPR diagnostic scoping so unrelated/recycled OpenAL ids cannot create false evidence;
- direct SPR evidence required for finite, radio and RAW target paths.

The new finite request scheduler is intentionally validated by the real exactly-8 C4 runtime test rather than by inventing a second mock scheduler implementation in tests.

## Next runtime evidence

Use the new branch JAR and the existing master log with `--resume`.

The next run should produce all four target results:

1. corrected C1 clean Sable tracking result;
2. C2 normal-world open/live-wall/restarted-wall SPR result;
3. fresh C3 radio + SPR-path result;
4. C4 exactly-8 finite plus independently recorded 8+ RAW result.

Interpret C2 narrowly: only if restarted-wall occlusion works while the live source stays stale do we add a client-only HQ SPR refresh mechanism.

After exactly 8 is clean, test >8 separately only if that concurrency level is an intended guarantee.

The master command remains:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

Use `RUNTIME-INVESTIGATION-2026-09-27.md` for the forensic evidence behind these changes.
