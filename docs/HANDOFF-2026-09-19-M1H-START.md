# Handoff — post-M1G hardening complete / M1H start — 2026-09-19

## Finished baseline

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Completed-hardening branch: `codex/post-m1g-hardening`

M1G is closed.

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

M1G CI: `35297026277`

Focused M1G audible/core Minecraft acceptance passed on 2026-09-19 using NeoForge 21.1.247 integrated singleplayer. It covered modern WAV/MP3 playback, pause/resume, repeated seek/reanchor, MP3 seek, float32 WAV, natural EOF, ordinary loop replay, fixed-range positional attenuation, prepared-asset lifetime, stop, and loop-safe global-volume-zero hibernation/unmute.

The selected Option A post-M1G hardening pass is also closed.

Final hardening **source** checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`

Latest full verification of that source tree: `35406595856`

- NeoForge 21.1.247: PASS, artifact `10572925494`, SHA-256 `ad0d02119ad3a4a29de00220e195ad117214316b8739f8faca223ac4900f409c`;
- NeoForge 21.1.248: PASS, artifact `10573000385`, SHA-256 `aff2eaaccb56b04c12d25bc6774f27c52f45fae90212b0b5e771f90828e6012e`.

Commits after `3d30ce...` through that verified checkpoint are documentation-only.

## Hardening issues closed

### KI-062 — DNS/server-thread lock coupling

Normal single-speaker stream DNS validation runs outside both the ownership monitor used by tick/cleanup and the separate command-order lock. `audioPlayPrepared` is a CC:T main-thread method and can no longer wait behind blocked DNS.

After DNS returns, stream commit briefly rejoins command ordering/ownership locking.

`commandRevision` rejects a normal stream start if a newer playback/control mutation happened while DNS was blocked. A separate lifecycle epoch rejects a result returning after cleanup/detach invalidation.

Inherited stream calls may still block their calling ComputerCraft thread during DNS. M3 owns redesigning live streams.

### KI-063 — replacement-before-admission

Prepared replacement validates, obtains media services, retains the asset, constructs the new session/descriptor/status, then stops current ownership and commits.

RAW replacement converts/validates the input and volume before destructive ownership transfer.

Do not regress to “stop old source first, then discover whether the new source is valid.”

### KI-054 — shutdown/root-lock retry

Range shutdown starts during `ServerStoppingEvent`. New work is rejected after closing starts. Failed closing state remains reachable for retry, a later server instance retries stale cleanup before reopening the media root, and failed deletion preserves bookkeeping/quota/root-lock ownership until cleanup succeeds.

### KI-064 — import no-progress / atomic rename

Repeated zero-byte reads are bounded; temporary zero reads can recover; unsupported `ATOMIC_MOVE` falls back to a same-root non-atomic move before asset publication.

## Do not reopen

Do not reopen M1G or KI-062/063/054/064 without a concrete regression.

Do not pull SPR, FLAC, multispeaker synchronization, live-stream redesign, legacy migration, or release cleanup into M1H.

## M1H status

M1H-1 listener membership is now implemented at source/test/CI/package level.

Source checkpoint: `84e7bab99009e5871934a960908945ceb00a10a9`

CI: `35410830197`

- NeoForge 21.1.247: PASS, artifact `10574726822`, SHA-256 `62e8f090ddfe5466db3807dd1d78fd738c355887e6e938f811834d9630078cc9`;
- NeoForge 21.1.248: PASS, artifact `10573826903`, SHA-256 `2538111207be632a1253a762f6a45218b6d7ca4b24c2739359b4f56c8e648555`.

Implemented M1H-1 behavior:

1. Start outside range: no admission.
2. Enter range: targeted BEGIN + current STATE immediately.
3. Stay in range: no repeated BEGIN/STOP.
4. Leave range: targeted STOP and membership removal.
5. Return: rejoin current authoritative playback time.
6. Disconnect: membership pruned.
7. Dimension mismatch: connected player gets cleanup and membership is pruned.
8. Stop/replacement/natural terminal/error: membership cleared coherently.
9. READY and range traffic require current membership plus relevance.

The selected late-entry choice is **Option A**: BEGIN + current STATE immediately. The normal client READY can cause one harmless follow-up STATE; no extra suppression bookkeeping was added.

Deterministic membership-transition tests are included. Focused real-Minecraft walk-in/walk-out/re-entry acceptance was deferred by the owner on 2026-09-19 and remains in the backlog; it must not be claimed from CI alone.

## M1H-2 — recovery status

M1H-2 is implemented at source/test/CI/package level.

Checkpoint: `aa72f0d2fc9f8cde53cd956389beca1743d06165`

CI: `35411480844`

- NeoForge 21.1.247: PASS, artifact `10574298203`, SHA-256 `536399fbb03f1b8009f4abc0c1260a116e5376234f125d4ccd43a25c3263e370`;
- NeoForge 21.1.248: PASS, artifact `10573757634`, SHA-256 `c496b3871cb7dae32d323dbdc3d10dd44efb8f9c012d18043a5a075285293790`.

Implemented:

- SoundEngine/resource-reload stream close recovers instead of becoming a fatal decoder error;
- lost renderer requests current authoritative STATE;
- recovery READY retries once per second until STATE arrives;
- five seconds of continuous renderer starvation triggers current-time recovery;
- ordinary STATE updates do not hide continuing starvation;
- same-revision STATE can rebuild the discarded local decoder;
- no protocol change was needed.

Focused Minecraft reload/loss/starvation testing is deferred to the runtime backlog.

## M1H-3 — moving-source status

M1H-3 is implemented at source/CI/package level.

Checkpoint: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`

CI: `35451236630`

- NeoForge 21.1.247: PASS, artifact `10586971196`, SHA-256 `ad336ca13f243aa19251f5e4649ab59066e60c91c40a4de6aeab49f5ca4d8a5c`;
- NeoForge 21.1.248: PASS, artifact `10587375399`, SHA-256 `31e553d2ed35380958bceb23ecf2479d46b03ad5bcdf8eed82440477b5b760f8`.

Selected design: **local movement resolution, no position streaming**.

- Sable Companion 1.6.0 is embedded for Sable/Aeronautics-style sublevels;
- existing VS2 movement support remains;
- client finite sound position is refreshed locally from BEGIN block coordinates;
- server membership uses the same resolved moving position;
- protocol v7 is unchanged;
- ordinary native Create contraption lifecycle is intentionally not pulled into this slice.

Focused Sable/Aeronautics and VS2 movement testing remains in the runtime backlog.

## Architecture that must remain

- protocol v7;
- server-authoritative playback timeline;
- semantic re-anchor via `decodeRevision`;
- STATE as sole nonterminal transition authority;
- explicit STOP;
- bounded range transport/client memory;
- MP3 + supported common WAV modern prepared support;
- one physical speaker = one mono positional source;
- fixed 32-block core radius;
- HQ volume changes gain, not radius;
- Lua owns application meaning/policy.

## Evidence gaps that are historical, not blockers

- M1E focused runtime acceptance was explicitly skipped.
- M1F focused Minecraft transport acceptance is unrecorded.
- M1G has focused runtime PASS and is closed.

Do not reopen M1E/M1F merely to satisfy old wording unless a current regression requires it.

## Recommended new branch

Create `codex/m1h-listener-lifecycle` from the **current head of `codex/post-m1g-hardening`** after verifying it contains source checkpoint `3d30ce...`.

Do not branch from old `main`; it is still the untouched fork baseline.

## Read order

1. this file;
2. `CURRENT-STATE.md`;
3. `KNOWN-ISSUES.md`;
4. `TESTING.md`;
5. `VERIFIED-FACTS.md`;
6. `ARCHITECTURE.md`;
7. `ROADMAP.md`;
8. exact `HQFiniteMediaServer`, `HQFiniteMediaClient`, `FiniteSpeakerSound`, packet classes, and current tests;
9. latest CI.

## Working style

Keep explanations practical, simple, and concrete.

Do not explain things in abstract architecture language by default. Prefer plain descriptions of what will happen in-game or in the code, what the user will notice, and what each choice changes. Use technical terms only when they are needed to make a decision or to identify the exact code being changed, and explain them briefly when used.

For meaningful tradeoffs, present the main options and consequences and let the owner choose. Handle minor implementation details without asking.

Do not default to either the simpler or the more general design. When both are reasonable, weigh them against each other. Compare the work and failure surface added now against the realistic likelihood and impact of future cases the more adaptable design would cover. Consider maintenance cost, performance cost, implementation risk, reversibility, migration cost if requirements grow later, and whether the extra abstraction would materially reduce future work. Treat plausible near-term needs differently from speculative edge cases. Explain which option appears better justified by those tradeoffs and why, but still leave the final choice to the owner.

When source work is requested: implement fully, add meaningful deterministic tests where they genuinely test the behavior, run both supported NeoForge CI targets, then adversarially re-read the changed lifecycle paths before calling the slice complete.
