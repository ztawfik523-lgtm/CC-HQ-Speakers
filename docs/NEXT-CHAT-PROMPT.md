# Ready-to-paste prompt for the next chat

Paste the text below into a fresh chat in this project.

---

We are continuing the CC:HQ Speakers project in repository `ztawfik523-lgtm/CC-HQ-Speakers`.

Read the project context and then inspect the **current GitHub source**, not just old chat summaries. Start with:

1. `docs/HANDOFF-2026-09-19-M1H-START.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/ROADMAP.md`
8. exact current source/CI

Current completed-hardening branch: `codex/post-m1g-hardening`.

Final post-M1G hardening source checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`.

Latest full verification of that source tree: CI `35406595856`, green on NeoForge 21.1.247 and 21.1.248 with deterministic tests, packaged-mod verification, and artifacts.

M1G is DONE. Its focused audible/core Minecraft runtime test passed on NeoForge 21.1.247. Do not reopen M1G unless you find a concrete regression.

The Option A post-M1G hardening pass is also DONE:

- KI-062 DNS/server-thread lock coupling resolved;
- KI-063 replacement-before-admission resolved;
- KI-054 shutdown/root-lock retry resolved;
- KI-064 import no-progress / atomic-move fallback resolved.

Do not redo those issues unless exact current source proves a regression.

The next task is **M1H — dynamic listener lifecycle/recovery**.

Start with **M1H-1 listener membership only**:

- player outside range when playback starts should not be admitted;
- player walking into the fixed 32-block radius during active playback should join at the current authoritative server time;
- player staying in range should not receive repeated BEGIN spam;
- player leaving range should get proactive client cleanup and stop range/decode/render work;
- player returning should rejoin current time cleanly;
- disconnect/removal and dimension mismatch should prune membership;
- stop/replacement/natural terminal should clear membership coherently;
- add deterministic tests for these transitions before runtime acceptance.

Exact current-source facts you must verify before editing:

- `HQFiniteMediaServer.commitPreparedStart()` sends BEGIN/STATE only to currently relevant players;
- `HQFiniteMediaServer.tick()` currently handles natural EOF only and does not maintain listeners;
- there is no admitted-listener UUID set;
- READY/range requests already check relevance;
- a late entrant never received BEGIN, so it cannot bootstrap itself;
- `FiniteSpeakerSound.updatePosition(...)` exists but modern finite playback does not drive it after renderer creation.

There is one meaningful M1H-1 choice. Do NOT silently choose it: late-entry bootstrap can either (A) proactively send BEGIN + current STATE, or (B) send BEGIN and use the existing client READY -> server STATE path. Briefly explain the practical tradeoff and let me choose if it materially changes implementation.

Do not pull VS2 into the first patch. Later M1H moving-source work also has a real choice: client-side ship transform from BEGIN block coordinates vs explicit authoritative position updates. Leave that choice open until membership/rejoin is working.

Keep protocol v7/server authority/bounded transport/fixed 32-block range intact unless exact source proves a change is necessary.

Before coding, create a new branch from the current `codex/post-m1g-hardening` head after verifying it contains `3d30ce...`. Suggested branch: `codex/m1h-listener-lifecycle`.

Then implement M1H-1 fully, add focused deterministic tests, run both NeoForge 21.1.247 and 21.1.248 CI/package verification, and adversarially re-read the lifecycle logic before calling the slice complete.

Keep explanations practical and simple. Don’t bury me in abstract architecture language. For meaningful choices, show the options/tradeoffs and let me choose; handle minor implementation details yourself.
