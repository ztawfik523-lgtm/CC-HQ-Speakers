# Handoff — M1G complete — 2026-09-18

> **2026-09-19 continuation note:** M1G remains complete. The separate Option A post-M1G hardening pass also completed at source checkpoint `3d30ce4564de749f32171666df65de739b08ad77`, latest full verification CI `35406595856`. KI-062/063/054/064 are closed. Current continuation is `HANDOFF-2026-09-19-M1H-START.md`; do not use this older handoff as the active task list.

## Verdict

M1G — the core progressive finite MP3/common-WAV engine — is complete at source/test/CI/package/component level.

Final source checkpoint:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Final source commits in this closeout pass:

- `9798f270ebea54ee5fd4d2db9b7f5d5d7754fd4e` — protocol/lifecycle/loop/staging implementation;
- `ed2d4a5a1845f2bfb99afac65674647e4904e635` — CI test-harness correction;
- `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542` — pure renderer-read policy coverage and renderer-loss retry refinement.

## Final CI

Workflow run: `35297026277`.

Both jobs passed:

- NeoForge 21.1.247;
- NeoForge 21.1.248.

Each passed build, tests, packaged-mod verification, and artifact upload.

Artifacts:

- 21.1.247: artifact `10527498657`, SHA-256 `3b873edd94a924cf3922a75cdd059c2b3963500787bd479ec8b386600ef055b1`;
- 21.1.248: artifact `10528645404`, SHA-256 `3b9304a509d37bf2ef1c0797d4449fa2cb6c8cc705678e63a68ed8c2ef72f8b1`.

## Implemented M1G contract

- protocol v7;
- explicit server-authoritative `decodeRevision`;
- semantic seek increments revision;
- ordinary STATE does not restart a healthy decoder/window;
- stale revision STATE is ignored;
- local worker identity invalidates before cancellation;
- STATE is sole nonterminal transition authority; explicit STOP remains;
- fixed 32-block server relevance and live channel attenuation;
- HQ volume changes gain rather than radius;
- global HQ volume zero hibernates client transport/decode/render and server range delivery while canonical time continues;
- client-local mute supported through `canStartSilent()`;
- renderer start/loss retry through authoritative READY/STATE rejoin;
- ordinary non-gapless loop replay after physical EOF;
- persistent per-speaker staging leftovers reclaimed on whole-owner cleanup;
- modern prepared support remains MP3 + supported common WAV only.

## Deterministic evidence

- existing bounded transport/window/range-service tests;
- existing WAV analysis/conversion/progressive decode tests;
- bounded PCM queue backpressure/starvation/cancel tests;
- `FiniteDecodeCoordinatorTest` for revision and local epoch invalidation;
- real synthetic mono 44.1 kHz MP3 fixture decoded by packaged JLayer;
- real MP3 initial starvation + repeated bounded refills + multiple range-window slides;
- real MP3 pre-target discard check;
- `FinitePcmReadAdapterTest` for renderer-facing DATA/SILENCE/EOF/CANCELLED behavior;
- staging cleanup tests including continued deletion attempts after one failure.

The direct Minecraft `AudioStream` interface cannot be loaded by the ordinary JUnit source set, so the renderer read policy is extracted into a pure adapter actually used by `FinitePcmAudioStream`. The Minecraft class itself is compile/package verified by both CI targets.

## M1G issues closed

- KI-051;
- KI-053;
- KI-055;
- KI-056;
- KI-057;
- KI-058;
- KI-060;
- KI-061.

KI-059 was already a resolved product decision.

KI-052 is closed at deterministic/source/component level. A focused live SoundManager/OpenAL runtime PASS was subsequently recorded on 2026-09-19 under KI-046.

## Runtime evidence update — 2026-09-19

Focused M1G audible/core Minecraft acceptance is now **recorded PASS** on NeoForge 21.1.247 integrated singleplayer, resolving KI-046. The run covered modern WAV/MP3 audibility, pause/resume, seek/reanchor, float32 WAV, natural EOF, ordinary looping, positional/fixed-range behavior, stop, and a dedicated loop-safe global-volume-zero mute/unmute retest.

This runtime PASS is separate from CI and does not claim a manual NeoForge 21.1.248 runtime pass. It also does not claim M1H resource-reload/late-entry/rejoin/moving-source behavior or post-M1G hardening KI-062/063/054/064.

## Open post-M1G work

Do not reopen these as M1G unless a concrete regression proves the completed finite engine is wrong:

- KI-062 — synchronized legacy stream DNS/server-tick cleanup coupling;
- KI-063 — replacement-before-admission;
- KI-054 — shutdown retry/root-lock hardening;
- KI-064 — asset import no-progress/atomic-move fallback;
- M1H — listener lifecycle/rejoin/resource recovery/final VS2 movement;
- M1I — gated native FLAC;
- later multispeaker/migration/OpenAL/release work;
- M2 SPR;
- M3 live streams;
- M4 release cleanup.

## Final re-audit conclusion

No remaining known source issue in KI-051/053/055/056/057/058/060/061 blocks the M1G engineering milestone.

The remaining known issues are cross-cutting ownership/storage hardening or explicitly later milestones; KI-046 is no longer open.

Therefore M1G is closed.
