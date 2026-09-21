# Future cleanup

Updated: 2026-09-21

The v10 source/API cleanup phase is closed. The public surface is frozen for runtime acceptance.

## Do not churn before runtime

Do not perform speculative API redesign, codec expansion, shared finite decode fan-out, new block types, playlist/application policy, or provider integrations.

Only make source changes before release when they fix:

- a concrete build/test defect;
- a runtime acceptance failure;
- a security/safety problem;
- clearly proven dead code with no behavior change.

## After runtime

If all runtime gates pass:

- final exact-source dead-code check;
- reconcile bug-fix docs;
- default-branch/release hygiene;
- release version/changelog/artifacts.

## Deferred features

OGG, FLAC, HLS, TS, provider playback, gapless playback and shared finite decode fan-out require fresh product/performance decisions.
