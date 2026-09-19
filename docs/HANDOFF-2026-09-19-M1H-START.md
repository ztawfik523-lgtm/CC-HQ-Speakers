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

## Next milestone: M1H

The first implementation slice is **listener membership**.

### Exact current source gap

- `HQFiniteMediaServer.commitPreparedStart()` sends BEGIN and STATE only to players relevant at that moment.
- `HQFiniteMediaServer.tick()` handles natural EOF only.
- There is no admitted-listener UUID set.
- READY/STATE and range requests already check current relevance.
- A player who starts outside the fixed 32-block radius and walks in later never received BEGIN, so the client cannot bootstrap itself.
- A player leaving range is not proactively sent targeted cleanup.
- Return/rejoin has no explicit current-time re-admission path.
- `FiniteSpeakerSound.updatePosition(...)` exists but is not driven after renderer creation.

### M1H-1 acceptance target

1. Start outside range: no client admission.
2. Walk into range during playback: exactly one bootstrap for current generation/current canonical time.
3. Stay in range: no BEGIN/STOP spam.
4. Walk out: targeted client cleanup and membership removal.
5. Walk back in: rejoin current authoritative time, not zero.
6. Disconnect/removal: membership pruned.
7. Dimension mismatch: old membership pruned; re-admit only when relevant again.
8. Stop/replacement/natural terminal: membership cleared coherently.

Add deterministic membership-transition tests before Minecraft acceptance.

### First M1H design choice

Do not silently choose between:

**A. Proactive BEGIN + current STATE**

- faster/self-contained admission;
- duplicates part of existing BEGIN -> READY -> STATE flow and needs clean ordering/idempotence.

**B. BEGIN, then existing READY -> STATE**

- reuses current rejoin path;
- adds a round trip before initial late-entry state arrives.

Server playback time remains authoritative either way. Present the tradeoff briefly to the owner before implementation if it materially affects the patch.

## M1H-2 — recovery

After membership is solid:

- resource/sound-engine reload;
- dimension/world recovery not already covered by membership pruning;
- verify existing renderer-loss -> READY -> STATE behavior;
- long starvation/underrun that should rejoin current server time.

Do not add protocol/state until a concrete failure proves v7 is insufficient.

## M1H-3 — moving source / VS2

Modern BEGIN contains initial world coordinates and block coordinates. Modern STATE does not carry live x/y/z. `FiniteSpeakerSound.updatePosition(...)` exists but is unused after creation.

Two viable approaches remain:

**A.** mirror the inherited client-side ship transform from BEGIN block coordinates;

**B.** explicit authoritative server position updates.

Do not silently choose this while implementing M1H-1.

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

When source work is requested: implement fully, add deterministic tests, run both supported NeoForge CI targets, then adversarially re-read the changed lifecycle paths before calling the slice complete.
