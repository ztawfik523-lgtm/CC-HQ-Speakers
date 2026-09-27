# Roadmap

Updated: 2026-09-27

The public v10 API/product semantics remain frozen, but runtime acceptance has now found concrete target-scope work. Do not start unrelated cleanup.

## Completed / runtime-proven

- A1-A19 deterministic matrix;
- R1-R9 real-client core matrix;
- native CC:T delegation on Sable world coordinates;
- finite MP3/common-WAV rendering and 2-speaker sync;
- endpoint-local finite controls;
- continuous RAW continuation;
- loop recovery, listener leave/rejoin and F3+T recovery;
- C3 MP3/ICY grouped radio + metadata + strict membership/rerun;
- single NeoForge 21.1.247 build/release policy.

## Active work — evidence-driven fixes

1. **C1 harness correction** — remove the unrelated no-stop-history requirement from Sable movement tracking while keeping movement/final-health assertions.
2. **Finite scale transport fix** — resolve the 4-per-player admission vs per-source in-flight mismatch and eliminate silent over-limit requests waiting 2 seconds before retry.
3. **Scale test separation** — ensure finite failure does not prevent 8+ RAW evidence.
4. **SPR diagnostic truth** — instrument actual `SoundPhysics.processSound`, then test known static-world geometry and Sable geometry separately.
5. **SPR architecture review** — reevaluate the historical `cchq-soundphysics-compat` design against the current fork. Reuse proven acoustic pieces where useful, but prefer simpler integration when it provides the same correctness.
6. **Scale rerun** — exactly 8 finite + RAW after fixes.
7. **>8 capacity check** — only if the release intends more than eight simultaneous HQ sources; treat Minecraft streamed-source capacity as a separate question.

## Release gate

Do not release until:

- corrected C1 is PASS;
- C2 has direct SPR processing evidence and intended geometry behavior;
- C4 finite is fixed and PASS;
- 8+ RAW has actually run and passed.

Dedicated-server/multiplayer, VS2 and a separate NeoForge 21.1.248 build remain outside the selected target.

## Deferred feature bucket

Not part of this release: OGG, FLAC, HLS, MPEG-TS, provider playback, playlists, standalone HQ block, or unrelated feature expansion.
