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

The source-side corrections from attempt 7 are implemented and the cumulative NeoForge 21.1.247 build is green at `87d08d3a62857dfeba16756591560eaa0e62d193` / CI `36293523518`.

The next phase is evidence, not another redesign.

1. **Target rerun** — install artifact `10922887347` and run C1-C4 with `--resume`; A/R prior PASS evidence is retained, and C3 is intentionally forced fresh.
2. **C2 decision point** — if normal-world restart occlusion works but the continuously-playing source stays stale, add the smallest client-only HQ refresh path. If the live source already refreshes, add nothing.
3. **C4 verification** — confirm fair shared finite admission removes the ~2.2-second outlier and obtain the missing RAW result.
4. **Exactly-8 closure** — only once finite + RAW are clean do we consider the scale blocker resolved.
5. **>8 capacity** — test separately only if more than eight simultaneous streamed HQ sounds is a required guarantee.
6. **Sable acoustics** — deferred until ordinary SPR integration is complete; do not mix Sable-wall geometry into this phase.

No old compat playback engine, custom OpenAL rewrite, adaptive room scheduler, smoothing or diffraction port is planned unless runtime evidence identifies a specific need.

## Release gate

Do not release until:

- corrected C1 is PASS;
- C2 has direct SPR processing evidence and intended geometry behavior;
- C4 finite is fixed and PASS;
- 8+ RAW has actually run and passed.

Dedicated-server/multiplayer, VS2 and a separate NeoForge 21.1.248 build remain outside the selected target.

## Deferred feature bucket

Not part of this release: OGG, FLAC, HLS, MPEG-TS, provider playback, playlists, standalone HQ block, or unrelated feature expansion.
